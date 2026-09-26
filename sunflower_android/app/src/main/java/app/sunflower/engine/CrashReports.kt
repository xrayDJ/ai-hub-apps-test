package app.sunflower.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What was happening when the native engine took the process down, and its last words. */
@Serializable
data class CrashReport(
    val modelId: String,
    /** "loading" or "replying". */
    val stage: String,
    val backend: String,
    val speculative: String,
    val time: Long,
    /** Engine log lines from the crashed process; no conversation content is logged. */
    val log: String,
    /** What Sunflower changed so the next attempt works, if anything. */
    val action: String?,
)

/**
 * Native crashes (a failed assertion in llama.cpp, a driver fault) end the
 * process with nothing to catch. A marker written before each risky step
 * survives it; on the next launch the marker plus the app's own log buffer
 * (Android lets an app read its own logs, including a previous process's)
 * become a report the user can read and share.
 */
class CrashReports(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("crash_reports", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _reports = MutableStateFlow(load())
    val reports: StateFlow<Map<String, CrashReport>> = _reports.asStateFlow()

    fun save(report: CrashReport) {
        prefs.edit().putString(report.modelId, json.encodeToString(CrashReport.serializer(), report)).apply()
        _reports.value = _reports.value + (report.modelId to report)
    }

    fun dismiss(modelId: String) {
        prefs.edit().remove(modelId).apply()
        _reports.value = _reports.value - modelId
    }

    private fun load(): Map<String, CrashReport> =
        prefs.all.mapNotNull { (id, value) ->
            (value as? String)?.let { runCatching { json.decodeFromString(CrashReport.serializer(), it) }.getOrNull() }?.let { id to it }
        }.toMap()

    companion object {
        private val RELEVANT =
            Regex(
                "geniex|llama|ggml|GGML|gguf|speculat|draft|mtp|MTP|nextn|htp|hexagon|qnn|opencl|assert|abort|Fatal signal|SIGABRT|SIGSEGV|backtrace|#\\d\\d pc|F DEBUG|F libc",
                RegexOption.IGNORE_CASE,
            )

        /** Recent engine-related lines from this app's log buffers (main and crash). */
        fun captureLog(): String =
            runCatching {
                val process =
                    ProcessBuilder("logcat", "-d", "-b", "main,crash", "-t", "1500", "-v", "time")
                        .redirectErrorStream(true)
                        .start()
                val lines = process.inputStream.bufferedReader().readLines()
                process.waitFor()
                lines.filter { RELEVANT.containsMatchIn(it) }.takeLast(120).joinToString("\n")
            }.getOrDefault("").ifBlank { "No engine log lines were available." }
    }
}
