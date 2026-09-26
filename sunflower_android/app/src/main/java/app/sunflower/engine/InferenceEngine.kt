package app.sunflower.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.sunflower.data.ConversationRepository
import app.sunflower.data.ModelHandle
import app.sunflower.data.ModelLibrary
import app.sunflower.data.db.MessageEntity
import app.sunflower.data.displayName
import app.sunflower.data.failedSet
import app.sunflower.data.isMtpHead
import app.sunflower.data.isSpeculativeHead
import app.sunflower.data.db.ModelEntity
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.bean.ChatMessage
import com.geniex.sdk.bean.GenerationConfig
import com.geniex.sdk.bean.LlmCreateInput
import com.geniex.sdk.bean.LlmStreamResult
import com.geniex.sdk.bean.ModelConfig
import com.geniex.sdk.bean.ProfilingData
import com.geniex.sdk.bean.RuntimeIdValue
import com.geniex.sdk.bean.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Where the model runs. Values match GenieX compute units for the llama.cpp runtime. */
enum class Backend(
    val computeUnit: String,
    val label: String,
    /** Layers offloaded to the accelerator; 999 means all of them. */
    val gpuLayers: Int,
) {
    NPU("npu", "NPU", 999),
    GPU("gpu", "GPU", 999),
    CPU("cpu", "CPU", 0),
    ;

    companion object {
        fun fromUnit(unit: String?) = entries.firstOrNull { it.computeUnit == unit }
    }
}

/** What the user asked for: a specific backend, or Auto (fastest that works). */
sealed interface BackendChoice {
    data object Auto : BackendChoice

    data class Only(val backend: Backend) : BackendChoice
}

class InferenceEngine(
    private val context: Context,
    private val runtime: GenieXRuntime,
    private val library: ModelLibrary,
    private val conversations: ConversationRepository,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State

        data class Loading(val model: ModelEntity, val backend: Backend) : State

        data class Ready(
            val model: ModelEntity,
            val backend: Backend,
            /** Exactly what the runtime was given, for showing which settings changed since. */
            val applied: ResolvedLoad,
        ) : State {
            val contextSize: Int get() = applied.contextSize
        }

        data class Failed(
            val model: ModelEntity,
            val message: String,
            val canCopyIntoApp: Boolean,
            /** Granting "All files access" would let the engine read the file in place. */
            val canGrantFileAccess: Boolean = false,
        ) : State
    }

    /** The reply being written right now. Saved to the database when it finishes. */
    data class Generation(
        val conversationId: String,
        /** Id the finished reply is saved under, so the UI can swap live text for the saved row seamlessly. */
        val messageId: String,
        val content: String = "",
        val thinking: String? = null,
        val thinkingOpen: Boolean = false,
        /** False until the first token arrives (the model is reading the prompt). */
        val started: Boolean = false,
    )

    data class GenerationFailure(val conversationId: String, val message: String)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _generation = MutableStateFlow<Generation?>(null)
    val generation: StateFlow<Generation?> = _generation.asStateFlow()

    private val _failure = MutableStateFlow<GenerationFailure?>(null)
    val failure: StateFlow<GenerationFailure?> = _failure.asStateFlow()

    private val prefs = context.applicationContext.getSharedPreferences("engine", Context.MODE_PRIVATE)

    /** Serialises load, unload and generate; stop() deliberately bypasses it. */
    private val mutex = Mutex()
    private var wrapper: LlmWrapper? = null
    private var handle: ModelHandle? = null
    private var draftHandle: ModelHandle? = null
    private var loadJob: Job? = null
    private var generateJob: Job? = null

    val crashReports = CrashReports(context)

    init {
        // A native crash kills the process with no exception to catch. The marker
        // written before each risky step survives it, so the next launch knows what
        // was running and can keep the next attempt from crashing the same way.
        val pending =
            prefs.getString(KEY_PENDING_LOAD, null)?.let { "loading" to it }
                ?: prefs.getString(KEY_PENDING_GENERATE, null)?.let { "replying" to it }
        if (pending != null) {
            prefs.edit().remove(KEY_PENDING_LOAD).remove(KEY_PENDING_GENERATE).apply()
            val (stage, marker) = pending
            val parts = marker.split('|')
            val modelId = parts[0]
            val unit = parts.getOrNull(1).orEmpty()
            val speculative = parts.getOrNull(2).orEmpty().ifEmpty { "none" }
            scope.launch(Dispatchers.IO) { handleCrash(modelId, stage, unit, speculative) }
        }
    }

    /**
     * With speculative decoding on, that is the likelier culprit than the backend:
     * turn it off for this model. Otherwise a crash while loading marks the backend
     * so Auto skips it. Either way, keep a report the user can read and share.
     */
    private suspend fun handleCrash(
        modelId: String,
        stage: String,
        unit: String,
        speculative: String,
    ) {
        val backendLabel = Backend.fromUnit(unit)?.label ?: unit
        val action =
            when {
                speculative != "none" -> {
                    library.get(modelId)?.let { model ->
                        val settings = library.settingsOf(model)
                        library.updateSettings(model.id, settings.copy(load = settings.load.copy(speculative = "none", draftModelId = null)))
                    }
                    "${speculativeLabel(speculative)} was turned off for this model."
                }
                stage == "loading" && unit.isNotEmpty() -> {
                    library.recordCrash(modelId, unit)
                    "Auto will skip $backendLabel for this model."
                }
                else -> null
            }
        crashReports.save(
            CrashReport(
                modelId = modelId,
                stage = stage,
                backend = backendLabel,
                speculative = speculative,
                time = System.currentTimeMillis(),
                log = CrashReports.captureLog(),
                action = action,
            ),
        )
    }

    /** Loads [model] with its saved settings. [choice] overrides the saved backend for this load only. */
    fun load(
        model: ModelEntity,
        choice: BackendChoice? = null,
    ) {
        if (_state.value is State.Loading || generateJob?.isActive == true) return
        loadJob =
            scope.launch {
                mutex.withLock {
                    releaseLocked()
                    loadLocked(model, choice)
                }
            }
    }

    fun unload() {
        scope.launch {
            stop()
            mutex.withLock {
                releaseLocked()
                _state.value = State.Idle
            }
        }
    }

    private suspend fun loadLocked(
        requestedModel: ModelEntity,
        requested: BackendChoice?,
    ) {
        // Settings may have changed since the caller read the model.
        val model = library.get(requestedModel.id) ?: requestedModel
        val settings = library.settingsOf(model)
        val choice = requested ?: settings.load.backend.toChoice()

        val runtimeState = runtime.state.first { it !is GenieXRuntime.State.Starting }
        if (runtimeState is GenieXRuntime.State.Failed) {
            _state.value = State.Failed(model, "The on-device runtime didn't start: ${runtimeState.reason}", false)
            return
        }

        if (model.isSpeculativeHead) {
            val kind = if (model.isMtpHead) "an MTP head" else "an EAGLE3 head"
            _state.value =
                State.Failed(model, "This is $kind, not a chat model. Pick it under Speculative decoding in the settings of the model it was made for.", false)
            return
        }

        val candidates = candidatesFor(model, choice)
        val opened =
            try {
                library.open(model)
            } catch (e: Exception) {
                _state.value = State.Failed(model, e.message ?: "Couldn't open the file", model.localPath == null)
                return
            }

        var lastError = "Couldn't load this model"
        val cores = Runtime.getRuntime().availableProcessors()
        for (backend in candidates) {
            val (resolved, draftPath) = withSpeculativeFile(model, settings.load.resolve(backend, model.contextLength, cores))
            _state.value = State.Loading(model, backend)
            prefs.edit().putString(KEY_PENDING_LOAD, "${model.id}|${backend.computeUnit}|${resolved.speculative}").commit()
            val result =
                withContext(Dispatchers.IO) {
                    LlmWrapper
                        .builder()
                        .llmCreateInput(
                            LlmCreateInput(
                                model_path = opened.path,
                                tokenizer_path = null,
                                config = resolved.toModelConfig(draftPath),
                                runtime_id = RuntimeIdValue.LLAMA_CPP.value,
                                compute_unit = backend.computeUnit,
                            ),
                        ).build()
                }
            prefs.edit().remove(KEY_PENDING_LOAD).apply()
            val loaded = result.getOrNull()
            if (loaded != null) {
                wrapper = loaded
                handle = opened
                library.recordLoaded(model.id, backend.computeUnit)
                _state.value = State.Ready(library.get(model.id) ?: model, backend, resolved)
                return
            }
            closeDraft()
            lastError = result.exceptionOrNull()?.message ?: lastError
            Log.w(TAG, "Load on ${backend.label} failed: $lastError")
        }
        opened.close()
        // If every backend failed on a file we read in place, the path hand-off is the likely culprit.
        val inPlace = model.localPath == null
        _state.value =
            State.Failed(
                model,
                lastError,
                canCopyIntoApp = inPlace,
                canGrantFileAccess = inPlace && !library.hasAllFilesAccess(),
            )
    }

    /**
     * Settles which speculative method actually runs and opens its file:
     * an MTP head picked under "Draft model" is run as MTP (it only works linked
     * to this model), and a method whose file is missing falls back to plain decoding.
     */
    private suspend fun withSpeculativeFile(
        model: ModelEntity,
        resolved: ResolvedLoad,
    ): Pair<ResolvedLoad, String?> {
        val helper = resolved.draftModelId?.let { library.get(it) }
        var method = resolved.speculative
        if (method == "draft" && helper?.isMtpHead == true) {
            method = "draft-mtp"
            // Save the correction so the settings screen shows what actually runs.
            val saved = library.settingsOf(library.get(model.id) ?: model)
            library.updateSettings(model.id, saved.copy(load = saved.load.copy(speculative = method)))
        }
        val path = if (helper != null && speculativeUsesFile(method)) openDraft(helper.id) else null.also { closeDraft() }
        val builtInMtp = (model.nextnLayers ?: 0) > 0
        val runnable =
            when (method) {
                "draft", "draft-eagle3" -> path != null
                "draft-mtp" -> path != null || builtInMtp
                else -> true
            }
        val effective = if (runnable) method else "none"
        return resolved.copy(speculative = effective, draftModelId = helper?.id.takeIf { path != null }) to path
    }

    /** Opens the speculative-decoding helper file; a missing one just disables drafting. */
    private suspend fun openDraft(id: String): String? {
        closeDraft()
        val draft = library.get(id) ?: return null
        return runCatching { library.open(draft) }
            .onFailure { Log.w(TAG, "Draft model unavailable: ${it.message}") }
            .getOrNull()
            ?.also { draftHandle = it }
            ?.path
    }

    private fun closeDraft() {
        draftHandle?.close()
        draftHandle = null
    }

    private fun ResolvedLoad.toModelConfig(draftPath: String?) =
        ModelConfig(
            nCtx = contextSize,
            nThreads = threads,
            nThreadsBatch = batchThreads,
            nBatch = batchSize,
            nUBatch = microBatch,
            nGpuLayers = gpuLayers,
            chat_template_content = chatTemplate,
            // Already settled by withSpeculativeFile: "none" means plain decoding.
            spec_type = if (speculative == "none") "" else speculative,
            spec_draft_model = draftPath.orEmpty(),
            spec_n_max = draftMax,
            spec_n_min = draftMin,
            spec_p_min = draftMinProbability,
            power_mode = powerMode,
        )

    private fun String.toChoice(): BackendChoice = Backend.fromUnit(this)?.let { BackendChoice.Only(it) } ?: BackendChoice.Auto

    private fun candidatesFor(
        model: ModelEntity,
        choice: BackendChoice,
    ): List<Backend> {
        if (choice is BackendChoice.Only) return listOf(choice.backend)
        val crashed = model.failedSet()
        val preferred = listOf(Backend.NPU, Backend.GPU, Backend.CPU).filterNot { it.computeUnit in crashed }
        // Try what worked last time first.
        val last = Backend.fromUnit(model.lastBackend)
        val ordered = if (last != null && last in preferred) listOf(last) + (preferred - last) else preferred
        return ordered.ifEmpty { listOf(Backend.CPU) }
    }

    /**
     * Sends the conversation to the model. [history] must end with the new user turn.
     * Returns false if the engine isn't ready or already busy.
     */
    fun send(
        conversationId: String,
        systemPrompt: String,
        history: List<MessageEntity>,
    ): Boolean {
        val ready = _state.value as? State.Ready ?: return false
        if (generateJob?.isActive == true) return false
        _failure.value = null
        val messageId = UUID.randomUUID().toString()
        _generation.value = Generation(conversationId, messageId)
        GenerationService.start(context, conversationId)
        generateJob =
            scope.launch {
                mutex.withLock { generateLocked(ready, conversationId, messageId, systemPrompt, history) }
            }
        return true
    }

    /** Ends the current reply early; what was written so far is kept. */
    suspend fun stop() {
        if (generateJob?.isActive != true) return
        wrapper?.stopStream()
    }

    fun dismissFailure() {
        _failure.value = null
    }

    private suspend fun generateLocked(
        ready: State.Ready,
        conversationId: String,
        messageId: String,
        systemPrompt: String,
        history: List<MessageEntity>,
    ) {
        val llm = wrapper
        if (llm == null) {
            _generation.value = null
            return
        }
        val settings = library.settingsOf(library.get(ready.model.id) ?: ready.model)
        val sampling = settings.sampling
        val chat = settings.chat
        val allTurns = history.map { Turn(it.role, it.content) }
        val turns =
            if (chat.trimHistory) {
                fitToContext(systemPrompt, allTurns, ready.contextSize, reservedForReply = sampling.maxTokens)
            } else {
                allTurns
            }
        val keepTokens = if (chat.keepTokens > 0) chat.keepTokens else estimateTokens(systemPrompt) + SYSTEM_OVERHEAD
        val messages =
            buildList {
                if (systemPrompt.isNotBlank()) add(ChatMessage(role = "system", content = systemPrompt))
                turns.forEach { add(ChatMessage(role = it.role, content = it.content)) }
            }

        val raw = StringBuilder()
        var profile: ProfilingData? = null
        var error: String? = null
        try {
            val prompt =
                llm
                    .applyChatTemplate(messages.toTypedArray(), null, chat.thinking, true)
                    .getOrThrow()
                    .formattedText
            val startsInThinking = prompt.trimEnd().endsWith("<think>")
            var lastEmit = 0L
            fun publish() {
                val split = splitThinking(raw.toString(), startsInThinking)
                _generation.value =
                    Generation(conversationId, messageId, split.content, split.thinking, split.thinkingOpen, started = raw.isNotEmpty())
            }
            prefs.edit().putString(KEY_PENDING_GENERATE, "${ready.model.id}|${ready.backend.computeUnit}|${ready.applied.speculative}").commit()
            llm.generateStreamFlow(prompt, generationConfig(sampling, chat, keepTokens)).collect { result ->
                when (result) {
                    is LlmStreamResult.Token -> {
                        raw.append(result.text)
                        // Coalesce tokens to roughly one UI update per frame.
                        val now = SystemClock.uptimeMillis()
                        if (now - lastEmit >= FRAME_MS) {
                            lastEmit = now
                            publish()
                        }
                    }
                    is LlmStreamResult.Completed -> profile = result.profile
                    is LlmStreamResult.Error -> error = result.throwable.message ?: "Generation failed"
                    else -> Unit
                }
            }
            publish()

            val split = splitThinking(raw.toString(), startsInThinking)
            if (split.content.isNotBlank() || !split.thinking.isNullOrBlank()) {
                conversations.addAssistantMessage(
                    MessageEntity(
                        id = messageId,
                        conversationId = conversationId,
                        role = ConversationRepository.ROLE_ASSISTANT,
                        content = split.content,
                        thinking = split.thinking,
                        createdAt = System.currentTimeMillis(),
                        promptTokens = profile?.promptTokens,
                        generatedTokens = profile?.generatedTokens,
                        ttftMs = profile?.ttftMs,
                        decodeTokensPerSec = profile?.decodingSpeed,
                        draftTokens = profile?.draftNTotal?.takeIf { it > 0 },
                        draftAccepted = profile?.draftNAccepted?.takeIf { (profile?.draftNTotal ?: 0) > 0 },
                    ),
                )
                conversations.setModelName(conversationId, ready.model.displayName)
            }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        } finally {
            prefs.edit().remove(KEY_PENDING_GENERATE).apply()
            _generation.value = null
            error?.let {
                Log.w(TAG, "Generation failed: $it")
                _failure.value = GenerationFailure(conversationId, it)
            }
        }
    }

    private fun releaseLocked() {
        wrapper?.let { runCatching { it.close() } }
        wrapper = null
        handle?.close()
        handle = null
        closeDraft()
    }

    private fun generationConfig(
        sampling: Sampling,
        chat: ChatOptions,
        keepTokens: Int,
    ): GenerationConfig {
        val stops = sampling.stopSequences.filter { it.isNotEmpty() }
        return GenerationConfig(
            maxTokens = sampling.maxTokens,
            stopWords = stops.toTypedArray().takeIf { it.isNotEmpty() },
            stopCount = stops.size,
            samplerConfig =
                SamplerConfig(
                    temperature = sampling.temperature,
                    topP = sampling.topP,
                    topK = sampling.topK,
                    minP = sampling.minP,
                    repetitionPenalty = sampling.repetitionPenalty,
                    presencePenalty = sampling.presencePenalty,
                    frequencyPenalty = sampling.frequencyPenalty,
                    seed = sampling.seed,
                    grammarString = sampling.grammar.takeIf { it.isNotBlank() },
                ),
            slidingWindow = chat.slidingWindow,
            slidingWindowNKeep = keepTokens,
        )
    }

    private companion object {
        const val TAG = "InferenceEngine"
        const val KEY_PENDING_LOAD = "pending_load"
        const val KEY_PENDING_GENERATE = "pending_generate"
        /** Template tokens around the system prompt, kept with it when the window shifts. */
        const val SYSTEM_OVERHEAD = 16
        const val FRAME_MS = 33L
    }
}
