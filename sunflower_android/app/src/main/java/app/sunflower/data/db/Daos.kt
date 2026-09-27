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

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(
        id: String,
        title: String,
    )

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(
        id: String,
        pinned: Boolean,
    )

    /** Titles containing [pattern] (a LIKE pattern with backslash escapes). */
    @Query("SELECT * FROM conversations WHERE title LIKE :pattern ESCAPE '\\' ORDER BY pinned DESC, updatedAt DESC")
    suspend fun searchTitles(pattern: String): List<ConversationEntity>
}

@Dao
interface MessageDao {
    /** Every message, including versions not on screen. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeAll(conversationId: String): Flow<List<MessageEntity>>

    /** The conversation as shown and sent to the model. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND active = 1 ORDER BY createdAt ASC")
    suspend fun list(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND turnId = :turnId ORDER BY createdAt ASC")
    suspend fun turn(
        conversationId: String,
        turnId: String,
    ): List<MessageEntity>

    @Query("UPDATE messages SET active = (variant = :variant) WHERE conversationId = :conversationId AND turnId = :turnId")
    suspend fun selectVariant(
        conversationId: String,
        turnId: String,
        variant: Int,
    )

    /** Forgets the versions not picked, once the chat moves on. */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND active = 0")
    suspend fun deleteInactive(conversationId: String)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    /** Messages containing [pattern] (a LIKE pattern with backslash escapes), newest first. */
    @Query("SELECT * FROM messages WHERE content LIKE :pattern ESCAPE '\\' AND active = 1 ORDER BY createdAt DESC LIMIT :limit")
    suspend fun search(
        pattern: String,
        limit: Int,
    ): List<MessageEntity>

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
