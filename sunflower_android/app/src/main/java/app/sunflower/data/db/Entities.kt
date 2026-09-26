package app.sunflower.data.db

import androidx.room.ColumnInfo
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
    /** Tokens guessed by speculative decoding for this reply, and how many the model accepted. */
    val draftTokens: Long? = null,
    val draftAccepted: Long? = null,
)

@Entity(tableName = "system_prompts")
data class SystemPromptEntity(
    @PrimaryKey val id: String,
    val name: String,
    val content: String,
    val createdAt: Long,
    /** Used as the system prompt of every new chat. At most one prompt has this set. */
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
)

/** A GGUF the user imported. The file itself stays where the user keeps it unless copied in. */
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    /** File name as shown by the picker, e.g. "Qwen3-1.7B-Q4_K_M.gguf". */
    val fileName: String,
    /** content:// URI with a persisted read grant. */
    val uri: String,
    /** Set when the file was copied into app storage because direct access failed. */
    val localPath: String?,
    val sizeBytes: Long,
    val name: String?,
    val architecture: String?,
    val sizeLabel: String?,
    val quantization: String?,
    val contextLength: Int?,
    val layerCount: Int?,
    val hasChatTemplate: Boolean,
    val addedAt: Long,
    /** Backend that last loaded this model successfully ("npu", "gpu", "cpu"). */
    val lastBackend: String?,
    /** Comma-separated backends that failed or crashed while loading this model. */
    val failedBackends: String,
    /** [app.sunflower.engine.ModelSettings] as JSON; null means all defaults. */
    val settings: String? = null,
    /** Built-in MTP layers (0 = none); null until the header has been checked. */
    val nextnLayers: Int? = null,
)
