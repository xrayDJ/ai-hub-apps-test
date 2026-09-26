package app.sunflower.data

import app.sunflower.data.db.SunflowerDatabase
import app.sunflower.data.db.SystemPromptEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.UUID

/** A ready-made prompt shipped with the app; copied into the library when the user edits or saves it. */
data class StarterPrompt(val name: String, val content: String)

val STARTER_PROMPTS =
    listOf(
        StarterPrompt("Assistant", DEFAULT_SYSTEM_PROMPT),
        StarterPrompt(
            "Straight to the point",
            "Answer in as few words as possible. No preamble, no summary at the end. Use lists only when they genuinely help.",
        ),
        StarterPrompt(
            "Coder",
            "You are an expert programmer. Give working, idiomatic code in fenced code blocks with the language named. " +
                "Explain only what isn't obvious from the code. Point out bugs and edge cases you notice.",
        ),
        StarterPrompt(
            "Translator",
            "Translate everything the user writes. If it's in English, translate it into Italian; otherwise translate it into English. " +
                "Reply with the translation only, keeping tone and formatting.",
        ),
        StarterPrompt(
            "Editor",
            "You are a careful editor. Improve the clarity, grammar and flow of the text the user gives you without changing its meaning or voice. " +
                "Return the edited text, then a short list of the most important changes.",
        ),
        StarterPrompt(
            "Tutor",
            "You are a patient tutor. Explain ideas step by step with simple examples, check understanding with a short question, " +
                "and adapt to what the user already knows. Don't give full answers to homework; guide instead.",
        ),
    )

/** The user's saved system prompts, stored in the encrypted database. */
class PromptLibrary(
    private val database: suspend () -> SunflowerDatabase,
) {
    fun observe(): Flow<List<SystemPromptEntity>> = flow { emitAll(database().systemPrompts().observeAll()) }

    /** The prompt a new chat starts with. */
    suspend fun defaultPrompt(): String = database().systemPrompts().getDefault()?.content ?: DEFAULT_SYSTEM_PROMPT

    /** Saves [content] under [name], replacing a prompt with the same name. Returns its id. */
    suspend fun save(
        name: String,
        content: String,
    ): String {
        val dao = database().systemPrompts()
        val existing = dao.findByName(name.trim())
        val prompt =
            existing?.copy(content = content)
                ?: SystemPromptEntity(UUID.randomUUID().toString(), name.trim(), content, System.currentTimeMillis())
        dao.upsert(prompt)
        return prompt.id
    }

    suspend fun rename(
        id: String,
        name: String,
    ) {
        val dao = database().systemPrompts()
        dao.get(id)?.let { dao.upsert(it.copy(name = name.trim())) }
    }

    suspend fun delete(id: String) = database().systemPrompts().delete(id)

    suspend fun setDefault(
        id: String,
        isDefault: Boolean,
    ) {
        val dao = database().systemPrompts()
        if (isDefault) dao.makeDefault(id) else dao.clearDefault()
    }
}
