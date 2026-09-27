package app.sunflower.data

import app.sunflower.data.db.ConversationEntity
import app.sunflower.data.db.MessageEntity
import app.sunflower.data.db.SunflowerDatabase
import androidx.room.withTransaction
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

    /** Every message, including versions of the last exchange that aren't on screen. */
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        flow { emitAll(database().messages().observeAll(conversationId)) }

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
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            // Moving on settles the previous exchange: the versions not picked go.
            db.messages().deleteInactive(conversationId)
            db.messages().upsert(
                MessageEntity(
                    id = id,
                    conversationId = conversationId,
                    role = ROLE_USER,
                    content = text,
                    createdAt = now,
                    turnId = id,
                ),
            )
        }
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

    suspend fun setModel(
        conversationId: String,
        modelId: String,
        modelName: String,
    ) {
        val dao = database().conversations()
        val conversation = dao.get(conversationId) ?: return
        if (conversation.modelId != modelId || conversation.modelName != modelName) {
            dao.upsert(conversation.copy(modelId = modelId, modelName = modelName))
        }
    }

    suspend fun updateSystemPrompt(
        conversationId: String,
        systemPrompt: String,
    ) {
        val dao = database().conversations()
        val conversation = dao.get(conversationId) ?: return
        dao.upsert(conversation.copy(systemPrompt = systemPrompt))
    }

    /**
     * Starts a new version of the last exchange, keeping the current one to flip back to:
     * the last user message is repeated ([editedText] replaces it when editing) and the
     * old message and reply are set aside. Returns false when there is nothing to reply to.
     */
    suspend fun newVersion(
        conversationId: String,
        editedText: String? = null,
    ): Boolean {
        val db = database()
        val dao = db.messages()
        return db.withTransaction {
            val visible = dao.list(conversationId)
            val lastUser = visible.indexOfLast { it.role == ROLE_USER }
            if (lastUser < 0) return@withTransaction false
            val user = visible[lastUser]
            val after = visible.drop(lastUser + 1)
            // A failed reply leaves nothing to keep: just answer again.
            if (editedText == null && after.isEmpty()) return@withTransaction true
            val turnId = user.turnId ?: user.id
            if (user.turnId == null || after.any { it.turnId == null }) {
                dao.upsertAll((listOf(user) + after).map { it.copy(turnId = turnId) })
            }
            val next = dao.turn(conversationId, turnId).maxOf { it.variant } + 1
            dao.selectVariant(conversationId, turnId, variant = -1)
            val now = System.currentTimeMillis()
            dao.upsert(
                user.copy(
                    id = UUID.randomUUID().toString(),
                    content = editedText ?: user.content,
                    createdAt = now,
                    turnId = turnId,
                    variant = next,
                    active = true,
                ),
            )
            db.conversations().get(conversationId)?.let { db.conversations().upsert(it.copy(updatedAt = now)) }
            true
        }
    }

    /** Shows another version of the last exchange. */
    suspend fun selectVersion(
        conversationId: String,
        turnId: String,
        variant: Int,
    ) = database().messages().selectVariant(conversationId, turnId, variant)

    suspend fun deleteConversation(id: String) = database().conversations().delete(id)

    suspend fun rename(
        id: String,
        title: String,
    ) {
        val clean = title.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (clean.isNotEmpty()) database().conversations().rename(id, clean.take(TITLE_LIMIT))
    }

    suspend fun setPinned(
        id: String,
        pinned: Boolean,
    ) = database().conversations().setPinned(id, pinned)

    /**
     * Chats whose title or messages contain [query], ignoring case. Title matches
     * come first; each chat appears once, with its most recent matching message.
     */
    suspend fun search(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val db = database()
        val pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val byTitle = db.conversations().searchTitles(pattern)
        val messages = db.messages().search(pattern, SEARCH_LIMIT)
        val hits = LinkedHashMap<String, SearchHit>()
        byTitle.forEach { hits[it.id] = SearchHit(it, null, null) }
        for (message in messages) {
            val existing = hits[message.conversationId]
            if (existing?.messageId != null) continue
            val conversation = existing?.conversation ?: db.conversations().get(message.conversationId) ?: continue
            hits[conversation.id] = SearchHit(conversation, message.id, snippet(message.content, q))
        }
        return hits.values.toList()
    }

    private fun titleFrom(text: String): String {
        val line = text.trim().lineSequence().first().trim()
        return if (line.length <= TITLE_MAX) line else line.take(TITLE_MAX).trimEnd() + "…"
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        private const val TITLE_MAX = 48
        private const val TITLE_LIMIT = 120
        private const val SEARCH_LIMIT = 300
    }
}

/** A chat matching a search, with the matching message when the match is in the text. */
data class SearchHit(
    val conversation: ConversationEntity,
    val messageId: String?,
    val snippet: Snippet?,
)
