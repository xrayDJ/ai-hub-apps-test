package app.sunflower.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sunflower.data.ConversationRepository
import app.sunflower.data.ModelLibrary
import app.sunflower.data.DEFAULT_SYSTEM_PROMPT
import app.sunflower.data.PromptLibrary
import app.sunflower.data.db.SystemPromptEntity
import app.sunflower.data.db.ConversationEntity
import app.sunflower.data.db.MessageEntity
import app.sunflower.data.displayName
import app.sunflower.data.isSpeculativeHead
import app.sunflower.data.db.ModelEntity
import app.sunflower.engine.InferenceEngine
import app.sunflower.engine.ModelSettings
import app.sunflower.engine.Reasoning
import app.sunflower.engine.reasoningOf
import app.sunflower.engine.estimateTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Versions of the last exchange: [index] of [variants] is on screen. */
data class Versions(
    val turnId: String,
    val variants: List<Int>,
    val index: Int,
) {
    val count: Int get() = variants.size
}

/** Visible messages, and the versions of the last exchange when there is more than one. */
fun versionsOf(all: List<MessageEntity>): Pair<List<MessageEntity>, Versions?> {
    val visible = all.filter { it.active }
    val last = visible.lastOrNull() ?: return visible to null
    val turnId = last.turnId ?: return visible to null
    val variants = all.filter { it.turnId == turnId }.map { it.variant }.distinct().sorted()
    if (variants.size < 2) return visible to null
    return visible to Versions(turnId, variants, variants.indexOf(last.variant).coerceAtLeast(0))
}

/** How the loaded model looks from inside a chat. */
sealed interface ModelStatus {
    data object None : ModelStatus

    data class Loading(val name: String) : ModelStatus

    data class Ready(
        val id: String,
        val name: String,
        val backend: String,
        val contextSize: Int,
        /** Room kept free for the reply; past contextSize minus this, old messages are trimmed. */
        val replyReserve: Int,
    ) : ModelStatus
}

data class ChatState(
    val title: String = "New chat",
    val messages: List<MessageEntity> = emptyList(),
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val model: ModelStatus = ModelStatus.None,
    /** The reply being written in this chat, if any. */
    val streaming: InferenceEngine.Generation? = null,
    /** Another chat is using the model right now. */
    val busyElsewhere: Boolean = false,
    val failure: String? = null,
    val promptLibrary: List<SystemPromptEntity> = emptyList(),
    /** Estimated tokens the chat occupies in the context window, same estimate the trimming uses. */
    val contextUsed: Int = 0,
    /** The model this chat last ran with, when a different one (or none) is loaded now. */
    val chatModel: ModelEntity? = null,
    /** How the loaded model reasons; null while unknown. */
    val reasoning: Reasoning? = null,
    /** The loaded model's "reason before answering" setting. */
    val thinking: Boolean = false,
    val versions: Versions? = null,
) {
    val contextSize: Int? get() = (model as? ModelStatus.Ready)?.contextSize
    val replyReserve: Int get() = (model as? ModelStatus.Ready)?.replyReserve ?: 0

    val generating: Boolean get() = streaming != null
    val canSend: Boolean get() = model is ModelStatus.Ready && !generating && !busyElsewhere
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    initialConversationId: String?,
    private val repository: ConversationRepository,
    private val engine: InferenceEngine,
    private val prompts: PromptLibrary,
    private val library: ModelLibrary,
) : ViewModel() {
    // A new chat is only written to disk once the first message is sent,
    // so opening and backing out never leaves empty conversations behind.
    private val conversationId = MutableStateFlow(initialConversationId)
    private val draftSystemPrompt = MutableStateFlow(DEFAULT_SYSTEM_PROMPT)
    private val createLock = Mutex()

    // When a reply finishes, the engine clears its live state a moment before the
    // saved message arrives from the database. Holding the last frame until then
    // keeps the reply on screen instead of blinking out and back in.
    private val lingering = MutableStateFlow<InferenceEngine.Generation?>(null)
    private var lingerJob: Job? = null

    private val conversation =
        conversationId.flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeConversation(id) }

    /** Set when the user waves off the offer to switch back to this chat's model. */
    private val keepCurrentModel = MutableStateFlow(false)

    private val chatModel =
        combine(conversation, library.observe().catch { emit(emptyList()) }) { c, models ->
            c?.modelId?.let { id -> models.firstOrNull { it.id == id && !it.isSpeculativeHead } }
        }

    /** The loaded model as saved (settings change without a reload), and how its template reasons. */
    private val loadedModel =
        combine(engine.state, library.observe().catch { emit(emptyList()) }) { engineState, models ->
            (engineState as? InferenceEngine.State.Ready)?.let { ready -> models.firstOrNull { it.id == ready.model.id } ?: ready.model }
        }

    private val reasoning =
        loadedModel
            .map { model -> model?.let { it.id to library.settingsOf(it).load.chatTemplate } }
            .distinctUntilChanged()
            .mapLatest { key ->
                val model = key?.let { library.get(it.first) } ?: return@mapLatest null
                val custom = key.second
                if (custom.isNotBlank()) reasoningOf(custom) else library.info(model)?.reasoning
            }

    private val messages =
        conversationId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeMessages(id) }

    private val stored =
        combine(conversation, messages, draftSystemPrompt, prompts.observe().catch { emit(emptyList()) }) { c: ConversationEntity?, m, draft, library ->
            val (visible, versions) = versionsOf(m)
            ChatState(
                title = c?.title ?: "New chat",
                messages = visible,
                versions = versions,
                systemPrompt = c?.systemPrompt ?: draft,
                promptLibrary = library,
            )
        }

    private val live =
        combine(engine.state, engine.generation, engine.failure, conversationId, lingering) { engineState, generation, failure, id, linger ->
            val model =
                when (engineState) {
                    is InferenceEngine.State.Ready -> ModelStatus.Ready(
                        engineState.model.id,
                        engineState.model.displayName,
                        engineState.backend.label,
                        engineState.contextSize,
                        ModelSettings.fromJson(engineState.model.settings).sampling.maxTokens,
                    )
                    is InferenceEngine.State.Loading -> ModelStatus.Loading(engineState.model.displayName)
                    else -> ModelStatus.None
                }
            val mine = generation?.takeIf { id != null && it.conversationId == id }
            LiveState(
                model = model,
                streaming = mine ?: linger?.takeIf { it.conversationId == id },
                busyElsewhere = generation != null && mine == null,
                failure = failure?.takeIf { it.conversationId == id }?.message,
            )
        }

    /** This chat's model when another is loaded, and the loaded model's reasoning. */
    private val modelExtras =
        combine(chatModel, keepCurrentModel, engine.state, loadedModel, reasoning) { previous, keep, engineState, loaded, reasons ->
            val current =
                when (engineState) {
                    is InferenceEngine.State.Ready -> engineState.model.id
                    is InferenceEngine.State.Loading -> engineState.model.id
                    else -> null
                }
            ModelExtras(
                offer = previous?.takeIf { it.id != current && !keep },
                reasoning = reasons,
                thinking = loaded?.let { library.settingsOf(it).chat.thinking } ?: false,
            )
        }

    val state: StateFlow<ChatState> =
        combine(stored, live, modelExtras) { s, l, extras ->
            val offer = extras.offer?.takeIf { l.streaming == null && !l.busyElsewhere }
            // Drop the held frame as soon as its saved message is in the list.
            // Replies are checkpointed while streaming, so the saved row can lag the live text.
            // Keep showing the live (or last live) text until the saved row has caught up.
            val streaming =
                l.streaming?.takeIf { gen ->
                    gen !== lingering.value || s.messages.none { it.id == gen.messageId && it.content == gen.content && it.thinking == gen.thinking }
                }
            val visible = if (streaming != null) s.messages.filter { it.id != streaming.messageId } else s.messages
            val used =
                estimateTokens(s.systemPrompt) +
                    visible.sumOf { estimateTokens(it.content) + TURN_OVERHEAD } +
                    (streaming?.let { estimateTokens(it.content + it.thinking.orEmpty()) + TURN_OVERHEAD } ?: 0)
            s.copy(
                reasoning = extras.reasoning,
                thinking = extras.thinking,
                messages = visible,
                model = l.model,
                streaming = streaming,
                busyElsewhere = l.busyElsewhere,
                failure = l.failure,
                contextUsed = used,
                chatModel = offer,
            )
        }.catch { emit(ChatState()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatState())

    init {
        // New chats start from the default saved prompt, if the user picked one.
        if (initialConversationId == null) {
            viewModelScope.launch { runCatching { draftSystemPrompt.value = prompts.defaultPrompt() } }
        }
        viewModelScope.launch {
            var previous: InferenceEngine.Generation? = null
            engine.generation.collect { current ->
                if (current == null && previous != null && previous?.conversationId == conversationId.value) {
                    lingering.value = previous
                    lingerJob?.cancel()
                    lingerJob =
                        launch {
                            delay(1_500)
                            lingering.value = null
                        }
                }
                previous = current
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !state.value.canSend) return
        viewModelScope.launch {
            val id = ensureConversation()
            repository.addUserMessage(id, trimmed)
            startReply(id)
        }
    }

    fun retry() {
        val id = conversationId.value ?: return
        engine.dismissFailure()
        viewModelScope.launch {
            if (repository.messages(id).lastOrNull()?.role == ConversationRepository.ROLE_USER) startReply(id)
        }
    }

    /** Writes another version of the last reply; the current one stays one tap away. */
    fun regenerate() {
        val id = conversationId.value ?: return
        if (!state.value.canSend) return
        engine.dismissFailure()
        viewModelScope.launch {
            if (repository.newVersion(id)) startReply(id)
        }
    }

    /** Shows the previous (-1) or next (+1) version of the last exchange. */
    fun showVersion(step: Int) {
        val id = conversationId.value ?: return
        val versions = state.value.versions ?: return
        if (state.value.generating) return
        val target = versions.variants.getOrNull(versions.index + step) ?: return
        viewModelScope.launch { repository.selectVersion(id, versions.turnId, target) }
    }

    /** Resends an edited last message as a new version; the original and its reply are kept. */
    fun sendEdit(
        messageId: String,
        text: String,
    ) {
        val id = conversationId.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !state.value.canSend) return
        engine.dismissFailure()
        viewModelScope.launch {
            if (repository.messages(id).none { it.id == messageId }) return@launch
            if (repository.newVersion(id, editedText = trimmed)) startReply(id)
        }
    }

    /** Conversation id once the chat has been saved, for screens that need its context. */
    val savedConversationId: String? get() = conversationId.value

    val promptActions =
        PromptLibraryActions(
            save = { name, content -> viewModelScope.launch { prompts.save(name, content) } },
            rename = { id, name -> viewModelScope.launch { prompts.rename(id, name) } },
            delete = { id -> viewModelScope.launch { prompts.delete(id) } },
            setDefault = { id, isDefault -> viewModelScope.launch { prompts.setDefault(id, isDefault) } },
        )

    /** Loads the model this chat last ran with. */
    fun loadChatModel() {
        state.value.chatModel?.let { engine.load(it) }
    }

    /** Keeps the loaded model for this chat; replies from now on are recorded with it. */
    fun keepCurrentModel() {
        keepCurrentModel.value = true
    }

    fun rename(title: String) {
        val id = conversationId.value ?: return
        viewModelScope.launch { repository.rename(id, title) }
    }

    /** Flips reasoning for the loaded model; applies from the next message, no reload needed. */
    fun toggleThinking() {
        val model = (engine.state.value as? InferenceEngine.State.Ready)?.model ?: return
        viewModelScope.launch {
            val saved = library.get(model.id) ?: return@launch
            val settings = library.settingsOf(saved)
            library.updateSettings(saved.id, settings.copy(chat = settings.chat.copy(thinking = !settings.chat.thinking)))
        }
    }

    fun stop() {
        viewModelScope.launch { engine.stop() }
    }

    fun setSystemPrompt(prompt: String) {
        val id = conversationId.value
        if (id == null) {
            draftSystemPrompt.value = prompt
        } else {
            viewModelScope.launch { repository.updateSystemPrompt(id, prompt) }
        }
    }

    private suspend fun startReply(id: String) {
        val systemPrompt = repository.conversation(id)?.systemPrompt ?: draftSystemPrompt.value
        engine.send(id, systemPrompt, repository.messages(id))
    }

    private suspend fun ensureConversation(): String =
        createLock.withLock {
            conversationId.value ?: repository.createConversation(draftSystemPrompt.value).also { conversationId.value = it }
        }

    private companion object {
        /** Template tokens around each message; matches fitToContext's allowance. */
        const val TURN_OVERHEAD = 8
    }

    private data class ModelExtras(
        val offer: ModelEntity?,
        val reasoning: Reasoning?,
        val thinking: Boolean,
    )

    private data class LiveState(
        val model: ModelStatus,
        val streaming: InferenceEngine.Generation?,
        val busyElsewhere: Boolean,
        val failure: String?,
    )
}
