package app.sunflower

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val key: String, val label: String) {
    Dark("dark", "Dark"),
    Light("light", "Light"),
    System("system", "System"),
}

/** How long Sunflower may sit in the background before asking to unlock again. */
enum class LockAfter(val key: String, val label: String, val millis: Long) {
    Immediately("0", "Immediately", 0),
    OneMinute("60", "After 1 min", 60_000),
    FiveMinutes("300", "After 5 min", 300_000),
}

/** Message sizes offered in settings, in percent. */
val CHAT_SCALES = listOf(85, 90, 100, 110, 120, 135, 150)

data class AppPrefs(
    val theme: ThemeMode = ThemeMode.Dark,
    val appLock: Boolean = false,
    val lockAfter: LockAfter = LockAfter.Immediately,
    val hideContent: Boolean = false,
    /** Size of chat messages, in percent of the normal size. */
    val chatScale: Int = 100,
)

/**
 * App-wide choices. None of these are sensitive (no content, no keys), so
 * plain preferences are fine; everything personal lives in the encrypted database.
 */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppPrefs> = _state.asStateFlow()

    fun update(transform: (AppPrefs) -> AppPrefs) {
        val next = transform(_state.value)
        prefs
            .edit()
            .putString("theme", next.theme.key)
            .putBoolean("app_lock", next.appLock)
            .putString("lock_after", next.lockAfter.key)
            .putBoolean("hide_content", next.hideContent)
            .putInt("chat_scale", next.chatScale)
            .apply()
        _state.value = next
    }

    private fun read() =
        AppPrefs(
            theme = ThemeMode.entries.firstOrNull { it.key == prefs.getString("theme", null) } ?: ThemeMode.Dark,
            appLock = prefs.getBoolean("app_lock", false),
            lockAfter = LockAfter.entries.firstOrNull { it.key == prefs.getString("lock_after", null) } ?: LockAfter.Immediately,
            hideContent = prefs.getBoolean("hide_content", false),
            chatScale = prefs.getInt("chat_scale", 100).coerceIn(CHAT_SCALES.first(), CHAT_SCALES.last()),
        )
}
