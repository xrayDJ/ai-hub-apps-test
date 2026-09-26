package app.sunflower.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun list(conversationId: String): List<MessageEntity>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    /** Removes a message and everything after it, for edit-and-resend. */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :createdAt")
    suspend fun deleteFrom(
        conversationId: String,
        createdAt: Long,
    )
}

@Dao
interface SystemPromptDao {
    @Query("SELECT * FROM system_prompts ORDER BY isDefault DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SystemPromptEntity>>

    @Query("SELECT * FROM system_prompts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): SystemPromptEntity?

    @Query("SELECT * FROM system_prompts WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): SystemPromptEntity?

    @Query("SELECT * FROM system_prompts WHERE id = :id")
    suspend fun get(id: String): SystemPromptEntity?

    @Upsert
    suspend fun upsert(prompt: SystemPromptEntity)

    @Query("UPDATE system_prompts SET isDefault = (id = :id)")
    suspend fun makeDefault(id: String)

    @Query("UPDATE system_prompts SET isDefault = 0")
    suspend fun clearDefault()

    @Query("DELETE FROM system_prompts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun get(id: String): ModelEntity?

    @Query("SELECT * FROM models WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): ModelEntity?

    @Upsert
    suspend fun upsert(model: ModelEntity)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: String)
}
