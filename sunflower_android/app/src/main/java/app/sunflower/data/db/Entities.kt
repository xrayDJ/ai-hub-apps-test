package app.sunflower.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations", indices = [Index("updatedAt")])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** File name of the GGUF the conversation was last run with, for display only. */
    val modelName: String?,
    /** Snapshot of the system prompt this conversation uses. */
    val systemPrompt: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId", "createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    /** "user" or "assistant", matching the chat-template roles. */
    val role: String,
    val content: String,
    /** Reasoning text from thinking models, shown collapsed. */
    val thinking: String? = null,
    val createdAt: Long,
    val promptTokens: Long? = null,
    val generatedTokens: Long? = null,
    val ttftMs: Double? = null,
    val decodeTokensPerSec: Double? = null,
)

@Entity(tableName = "system_prompts")
data class SystemPromptEntity(
    @PrimaryKey val id: String,
    val name: String,
    val content: String,
    val createdAt: Long,
)
