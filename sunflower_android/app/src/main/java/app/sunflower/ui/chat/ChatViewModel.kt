package app.sunflower.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sunflower.data.ConversationRepository
import app.sunflower.data.DEFAULT_SYSTEM_PROMPT
import app.sunflower.data.PromptLibrary
import app.sunflower.data.db.SystemPromptEntity
import app.sunflower.data.db.ConversationEntity
import app.sunflower.data.db.MessageEntity
import app.sunflower.data.displayName
import app.sunflower.engine.InferenceEngine
import app.sunflower.engine.ModelSettings
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val messages =
        conversationId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeMessages(id) }

    private val stored =
        combine(conversation, messages, draftSystemPrompt, prompts.observe().catch { emit(emptyList()) }) { c: ConversationEntity?, m, draft, library ->
            ChatState(
                title = c?.title ?: "New chat",
                messages = m,
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

    val state: StateFlow<ChatState> =
        combine(stored, live) { s, l ->
            // Drop the held frame as soon as its saved message is in the list.
            val streaming = l.streaming?.takeIf { gen -> gen !== lingering.value || s.messages.none { it.id == gen.messageId } }
            val used =
                estimateTokens(s.systemPrompt) +
                    s.messages.sumOf { estimateTokens(it.content) + TURN_OVERHEAD } +
                    (streaming?.let { estimateTokens(it.content + it.thinking.orEmpty()) + TURN_OVERHEAD } ?: 0)
            s.copy(model = l.model, streaming = streaming, busyElsewhere = l.busyElsewhere, failure = l.failure, contextUsed = used)
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

    /** Replaces the last reply with a fresh one. */
    fun regenerate() {
        val id = conversationId.value ?: return
        if (!state.value.canSend) return
        engine.dismissFailure()
        viewModelScope.launch {
            val last = repository.messages(id).lastOrNull() ?: return@launch
            if (last.role == ConversationRepository.ROLE_ASSISTANT) repository.deleteMessage(last.id)
            startReply(id)
        }
    }

    /** Resends an edited message, dropping it and everything after it. */
    fun sendEdit(
        messageId: String,
        text: String,
    ) {
        val id = conversationId.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !state.value.canSend) return
        engine.dismissFailure()
        viewModelScope.launch {
            val original = repository.messages(id).firstOrNull { it.id == messageId } ?: return@launch
            repository.deleteFrom(original)
            repository.addUserMessage(id, trimmed)
            startReply(id)
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

    private data class LiveState(
        val model: ModelStatus,
        val streaming: InferenceEngine.Generation?,
        val busyElsewhere: Boolean,
        val failure: String?,
    )
}
