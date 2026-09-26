package app.sunflower.data

import app.sunflower.data.db.ConversationEntity
import app.sunflower.data.db.MessageEntity
import app.sunflower.data.db.SunflowerDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.UUID

const val DEFAULT_SYSTEM_PROMPT = "You are a helpful, concise assistant."

class ConversationRepository(
    private val database: suspend () -> SunflowerDatabase,
) {
    fun observeConversations(): Flow<List<ConversationEntity>> =
        flow { emitAll(database().conversations().observeAll()) }

    fun observeConversation(id: String): Flow<ConversationEntity?> =
        flow { emitAll(database().conversations().observe(id)) }

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        flow { emitAll(database().messages().observe(conversationId)) }

    suspend fun createConversation(systemPrompt: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        database().conversations().upsert(
            ConversationEntity(
                id = id,
                title = "New chat",
                modelName = null,
                systemPrompt = systemPrompt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    suspend fun addUserMessage(
        conversationId: String,
        text: String,
    ) {
        val db = database()
        val now = System.currentTimeMillis()
        db.messages().upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = ROLE_USER,
                content = text,
                createdAt = now,
            ),
        )
        val conversation = db.conversations().get(conversationId) ?: return
        val title = if (conversation.title == "New chat") titleFrom(text) else conversation.title
        db.conversations().upsert(conversation.copy(title = title, updatedAt = now))
    }

    suspend fun conversation(id: String): ConversationEntity? = database().conversations().get(id)

    suspend fun messages(conversationId: String): List<MessageEntity> = database().messages().list(conversationId)

    suspend fun addAssistantMessage(message: MessageEntity) {
        val db = database()
        db.messages().upsert(message)
        val conversation = db.conversations().get(message.conversationId) ?: return
        db.conversations().upsert(conversation.copy(updatedAt = message.createdAt))
    }

    suspend fun setModelName(
        conversationId: String,
        modelName: String,
    ) {
        val dao = database().conversations()
        val conversation = dao.get(conversationId) ?: return
        if (conversation.modelName != modelName) dao.upsert(conversation.copy(modelName = modelName))
    }

    suspend fun updateSystemPrompt(
        conversationId: String,
        systemPrompt: String,
    ) {
        val dao = database().conversations()
        val conversation = dao.get(conversationId) ?: return
        dao.upsert(conversation.copy(systemPrompt = systemPrompt))
    }

    suspend fun deleteConversation(id: String) = database().conversations().delete(id)

    private fun titleFrom(text: String): String {
        val line = text.trim().lineSequence().first().trim()
        return if (line.length <= TITLE_MAX) line else line.take(TITLE_MAX).trimEnd() + "…"
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        private const val TITLE_MAX = 48
    }
}
