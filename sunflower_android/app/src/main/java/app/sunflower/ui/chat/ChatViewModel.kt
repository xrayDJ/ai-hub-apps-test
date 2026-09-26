package app.sunflower.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sunflower.data.ConversationRepository
import app.sunflower.data.DEFAULT_SYSTEM_PROMPT
import app.sunflower.data.db.ConversationEntity
import app.sunflower.data.db.MessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

data class ChatState(
    val title: String = "New chat",
    val messages: List<MessageEntity> = emptyList(),
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val modelLoaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    initialConversationId: String?,
    private val repository: ConversationRepository,
) : ViewModel() {
    // A new chat is only written to disk once the first message is sent,
    // so opening and backing out never leaves empty conversations behind.
    private val conversationId = MutableStateFlow(initialConversationId)
    private val draftSystemPrompt = MutableStateFlow(DEFAULT_SYSTEM_PROMPT)
    private val createLock = Mutex()

    private val conversation =
        conversationId.flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeConversation(id) }

    private val messages =
        conversationId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeMessages(id) }

    val state: StateFlow<ChatState> =
        combine(conversation, messages, draftSystemPrompt) { c: ConversationEntity?, m, draft ->
            ChatState(
                title = c?.title ?: "New chat",
                messages = m,
                systemPrompt = c?.systemPrompt ?: draft,
            )
        }
            // Storage failures are reported on the home screen; here the chat just stays empty.
            .catch { emit(ChatState()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatState())

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addUserMessage(ensureConversation(), trimmed)
        }
    }

    fun setSystemPrompt(prompt: String) {
        val id = conversationId.value
        if (id == null) {
            draftSystemPrompt.value = prompt
        } else {
            viewModelScope.launch { repository.updateSystemPrompt(id, prompt) }
        }
    }

    private suspend fun ensureConversation(): String =
        createLock.withLock {
            conversationId.value ?: repository.createConversation(draftSystemPrompt.value).also { conversationId.value = it }
        }
}
