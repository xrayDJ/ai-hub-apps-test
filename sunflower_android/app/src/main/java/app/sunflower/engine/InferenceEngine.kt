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
    context: Context,
    private val runtime: GenieXRuntime,
    private val library: ModelLibrary,
    private val conversations: ConversationRepository,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State

        data class Loading(val model: ModelEntity, val backend: Backend) : State

        data class Ready(val model: ModelEntity, val backend: Backend, val contextSize: Int) : State

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
    private var loadJob: Job? = null
    private var generateJob: Job? = null

    init {
        // A native crash while loading kills the process with no exception to catch.
        // The marker written before each attempt survives it; blame that backend.
        prefs.getString(KEY_PENDING_LOAD, null)?.let { pending ->
            prefs.edit().remove(KEY_PENDING_LOAD).apply()
            val (modelId, unit) = pending.split('|', limit = 2).let { it[0] to it.getOrNull(1) }
            if (unit != null) scope.launch { library.recordCrash(modelId, unit) }
        }
    }

    fun load(
        model: ModelEntity,
        choice: BackendChoice,
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
        model: ModelEntity,
        choice: BackendChoice,
    ) {
        val runtimeState = runtime.state.first { it !is GenieXRuntime.State.Starting }
        if (runtimeState is GenieXRuntime.State.Failed) {
            _state.value = State.Failed(model, "The on-device runtime didn't start: ${runtimeState.reason}", false)
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

        val contextSize = defaultContextSize(model)
        var lastError = "Couldn't load this model"
        for (backend in candidates) {
            _state.value = State.Loading(model, backend)
            prefs.edit().putString(KEY_PENDING_LOAD, "${model.id}|${backend.computeUnit}").commit()
            val result =
                withContext(Dispatchers.IO) {
                    LlmWrapper
                        .builder()
                        .llmCreateInput(
                            LlmCreateInput(
                                model_path = opened.path,
                                tokenizer_path = null,
                                config =
                                    ModelConfig(
                                        nCtx = contextSize,
                                        nThreads = threadCount(),
                                        nThreadsBatch = threadCount(),
                                        nGpuLayers = backend.gpuLayers,
                                    ),
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
                _state.value = State.Ready(model, backend, contextSize)
                return
            }
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
        val settings = GenerationSettings.Default
        val turns =
            fitToContext(
                systemPrompt = systemPrompt,
                turns = history.map { Turn(it.role, it.content) },
                contextTokens = ready.contextSize,
                reservedForReply = settings.maxTokens,
            )
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
                    .applyChatTemplate(messages.toTypedArray(), null, settings.enableThinking, true)
                    .getOrThrow()
                    .formattedText
            val startsInThinking = prompt.trimEnd().endsWith("<think>")
            var lastEmit = 0L
            fun publish() {
                val split = splitThinking(raw.toString(), startsInThinking)
                _generation.value =
                    Generation(conversationId, messageId, split.content, split.thinking, split.thinkingOpen, started = raw.isNotEmpty())
            }
            llm.generateStreamFlow(prompt, settings.toGenerationConfig()).collect { result ->
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
                    ),
                )
                conversations.setModelName(conversationId, ready.model.displayName)
            }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        } finally {
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
    }

    private fun defaultContextSize(model: ModelEntity): Int = (model.contextLength ?: DEFAULT_CONTEXT).coerceIn(512, DEFAULT_CONTEXT)

    /** llama.cpp scales poorly onto efficiency cores; leave a couple free. */
    private fun threadCount(): Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)

    private companion object {
        const val TAG = "InferenceEngine"
        const val KEY_PENDING_LOAD = "pending_load"
        const val DEFAULT_CONTEXT = 4096
        const val FRAME_MS = 33L
    }
}

/**
 * Sampling and length settings. The SDK's own defaults (32 max tokens, every
 * sampler value 0) are unusable, so every field is set explicitly.
 * These become user-tunable in the parameters sheet.
 */
data class GenerationSettings(
    val maxTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float,
    val repetitionPenalty: Float,
    val presencePenalty: Float,
    val frequencyPenalty: Float,
    /** -1 picks a fresh random seed for every reply. */
    val seed: Int,
    val enableThinking: Boolean,
) {
    fun toGenerationConfig() =
        GenerationConfig(
            maxTokens = maxTokens,
            samplerConfig =
                SamplerConfig(
                    temperature = temperature,
                    topP = topP,
                    topK = topK,
                    minP = minP,
                    repetitionPenalty = repetitionPenalty,
                    presencePenalty = presencePenalty,
                    frequencyPenalty = frequencyPenalty,
                    seed = seed,
                ),
            // Safety net: if a reply outgrows the window mid-generation, shift instead of failing.
            slidingWindow = true,
        )

    companion object {
        val Default =
            GenerationSettings(
                maxTokens = 1024,
                temperature = 0.7f,
                topP = 0.95f,
                topK = 40,
                minP = 0.05f,
                repetitionPenalty = 1.1f,
                presencePenalty = 0f,
                frequencyPenalty = 0f,
                seed = -1,
                enableThinking = false,
            )
    }
}
