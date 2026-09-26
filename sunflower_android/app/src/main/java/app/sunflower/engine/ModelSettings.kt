package app.sunflower.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the user can tune for one model. Stored as JSON with the model,
 * so each model keeps its own settings. Defaults are chosen to be safe on a
 * phone; "auto" values (0 or -1 below) are resolved at load time.
 */
@Serializable
data class ModelSettings(
    val sampling: Sampling = Sampling(),
    val chat: ChatOptions = ChatOptions(),
    val load: LoadOptions = LoadOptions(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        /** Unknown or corrupt JSON falls back to defaults rather than failing a load. */
        fun fromJson(text: String?): ModelSettings =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: ModelSettings()
    }
}

/** Applies from the next reply; no reload needed. */
@Serializable
data class Sampling(
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val minP: Float = 0.05f,
    val repetitionPenalty: Float = 1.1f,
    val presencePenalty: Float = 0f,
    val frequencyPenalty: Float = 0f,
    /** -1 draws a new random seed for every reply. */
    val seed: Int = -1,
    val maxTokens: Int = 1024,
    val stopSequences: List<String> = emptyList(),
    /** GBNF grammar that constrains the output; empty means unconstrained. */
    val grammar: String = "",
)

/** Also applies from the next reply. */
@Serializable
data class ChatOptions(
    /** Asks templates that support it (Qwen3 and others) to reason before answering. */
    val thinking: Boolean = false,
    /** Drop the oldest messages that don't fit, before sending. */
    val trimHistory: Boolean = true,
    /** If a reply outgrows the context mid-generation, shift the window instead of stopping. */
    val slidingWindow: Boolean = true,
    /** Tokens kept at the start when the window shifts; 0 means "the system prompt". */
    val keepTokens: Int = 0,
)

/** Takes effect when the model is (re)loaded. */
@Serializable
data class LoadOptions(
    /** "auto", or a [Backend.computeUnit]. */
    val backend: String = AUTO,
    /** 0 = auto: the model's trained length, capped at [DEFAULT_CONTEXT]. */
    val contextSize: Int = 0,
    /** Layers offloaded to NPU/GPU; -1 = all. Ignored on CPU. */
    val gpuLayers: Int = -1,
    /** 0 = auto. */
    val threads: Int = 0,
    /** 0 = same as [threads]. */
    val batchThreads: Int = 0,
    val batchSize: Int = 2048,
    val microBatch: Int = 512,
    /** Hexagon NPU power profile; "" = runtime default. */
    val powerMode: String = "",
    /** Jinja chat template replacing the model's own; "" = use the model's. */
    val chatTemplate: String = "",
    /** One of [SPECULATIVE_TYPES]. */
    val speculative: String = "none",
    val draftModelId: String? = null,
    val draftMax: Int = 16,
    val draftMin: Int = 0,
    val draftMinProbability: Float = 0.75f,
) {
    companion object {
        const val AUTO = "auto"
        const val DEFAULT_CONTEXT = 4096
    }
}

/** The values actually passed to the runtime, after resolving every "auto". */
data class ResolvedLoad(
    val backend: Backend,
    val contextSize: Int,
    val gpuLayers: Int,
    val threads: Int,
    val batchThreads: Int,
    val batchSize: Int,
    val microBatch: Int,
    val powerMode: String,
    val chatTemplate: String,
    val speculative: String,
    val draftModelId: String?,
    val draftMax: Int,
    val draftMin: Int,
    val draftMinProbability: Float,
)

fun LoadOptions.resolve(
    backend: Backend,
    trainedContext: Int?,
    cores: Int,
): ResolvedLoad {
    val threadCount = if (threads > 0) threads else defaultThreads(cores)
    val batch = batchSize.coerceAtLeast(32)
    return ResolvedLoad(
        backend = backend,
        contextSize = if (contextSize > 0) contextSize else (trainedContext ?: LoadOptions.DEFAULT_CONTEXT).coerceIn(512, LoadOptions.DEFAULT_CONTEXT),
        gpuLayers =
            when {
                backend == Backend.CPU -> 0
                gpuLayers < 0 -> ALL_LAYERS
                else -> gpuLayers
            },
        threads = threadCount,
        batchThreads = if (batchThreads > 0) batchThreads else threadCount,
        batchSize = batch,
        microBatch = microBatch.coerceIn(16, batch),
        powerMode = if (backend == Backend.NPU) powerMode else "",
        chatTemplate = chatTemplate,
        speculative = speculative,
        draftModelId = if (speculativeUsesFile(speculative)) draftModelId else null,
        draftMax = draftMax,
        draftMin = draftMin,
        draftMinProbability = draftMinProbability,
    )
}

/** llama.cpp scales poorly onto efficiency cores; leave a couple free. */
fun defaultThreads(cores: Int): Int = (cores - 2).coerceIn(2, 6)

const val ALL_LAYERS = 999

/** Human-readable differences between what is loaded and what the settings ask for. */
fun ResolvedLoad.changesTo(next: ResolvedLoad): List<String> =
    buildList {
        fun <T> diff(
            label: String,
            a: T,
            b: T,
            show: (T) -> String = { it.toString() },
        ) {
            if (a != b) add("$label ${show(a)} → ${show(b)}")
        }
        diff("Backend", backend, next.backend) { it.label }
        diff("Context", contextSize, next.contextSize)
        diff("Offloaded layers", gpuLayers, next.gpuLayers) { if (it >= ALL_LAYERS) "all" else it.toString() }
        diff("Threads", threads, next.threads)
        diff("Batch threads", batchThreads, next.batchThreads)
        diff("Batch", batchSize, next.batchSize)
        diff("Micro-batch", microBatch, next.microBatch)
        diff("Power mode", powerMode, next.powerMode) { powerModeLabel(it) }
        diff("Chat template", chatTemplate.isNotBlank(), next.chatTemplate.isNotBlank()) { if (it) "custom" else "model's" }
        if (chatTemplate != next.chatTemplate && chatTemplate.isNotBlank() && next.chatTemplate.isNotBlank()) add("Chat template edited")
        diff("Speculative", speculative, next.speculative) { speculativeLabel(it) }
        diff("Helper file", draftModelId, next.draftModelId) { if (it == null) "none" else "selected" }
        diff("Draft max", draftMax, next.draftMax)
        diff("Draft min", draftMin, next.draftMin)
        diff("Draft min probability", draftMinProbability, next.draftMinProbability)
    }

/** Power profiles understood by llama.cpp's Hexagon backend. */
val POWER_MODES = listOf("", "burst", "sustained_high_performance", "high_performance", "balanced", "power_saver", "low_power_saver")

fun powerModeLabel(mode: String): String =
    when (mode) {
        "" -> "Default"
        "burst" -> "Burst"
        "sustained_high_performance" -> "Sustained high"
        "high_performance" -> "High"
        "balanced" -> "Balanced"
        "power_saver" -> "Power saver"
        "low_power_saver" -> "Low power"
        else -> mode
    }

/**
 * Speculative decoding strategies in the bundled llama.cpp.
 * "draft" and "draft-eagle3" need a second file; "draft-mtp" uses an MTP head file,
 * or the model's own MTP layers when it has them.
 */
val SPECULATIVE_TYPES =
    listOf("none", "draft-mtp", "draft", "draft-eagle3", "ngram-simple", "ngram-map-k", "ngram-map-k4v", "ngram-mod", "ngram-cache")

/** Methods that can use a second file picked by the user. */
fun speculativeUsesFile(type: String): Boolean = type == "draft" || type == "draft-eagle3" || type == "draft-mtp"

fun speculativeNeedsFile(type: String): Boolean = type == "draft" || type == "draft-eagle3"

fun speculativeLabel(type: String): String =
    when (type) {
        "none" -> "Off"
        "draft" -> "Draft model"
        "draft-mtp" -> "MTP"
        "draft-eagle3" -> "EAGLE3 head"
        "ngram-simple" -> "N-gram"
        "ngram-map-k" -> "N-gram map"
        "ngram-map-k4v" -> "N-gram map k4v"
        "ngram-mod" -> "N-gram mod"
        "ngram-cache" -> "N-gram cache"
        else -> type
    }

/** One-tap starting points for the reply settings. */
enum class SamplingPreset(
    val label: String,
    val sampling: Sampling,
) {
    Precise("Precise", Sampling(temperature = 0.2f, topP = 0.9f, topK = 20, minP = 0.1f, repetitionPenalty = 1.05f)),
    Balanced("Balanced", Sampling()),
    Creative("Creative", Sampling(temperature = 1.0f, topP = 0.98f, topK = 100, minP = 0.02f, repetitionPenalty = 1.05f)),
}

/** Applies a preset's sampler values while keeping the user's length, seed, stops and grammar. */
fun Sampling.withSamplerFrom(other: Sampling): Sampling =
    copy(
        temperature = other.temperature,
        topP = other.topP,
        topK = other.topK,
        minP = other.minP,
        repetitionPenalty = other.repetitionPenalty,
        presencePenalty = other.presencePenalty,
        frequencyPenalty = other.frequencyPenalty,
    )

fun Sampling.sameSamplerAs(other: Sampling): Boolean = withSamplerFrom(other) == this
