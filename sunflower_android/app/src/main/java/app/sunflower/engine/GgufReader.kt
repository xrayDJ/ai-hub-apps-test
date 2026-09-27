package app.sunflower.engine

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream

/** What a GGUF file says about itself, read from its header without touching the weights. */
data class GgufInfo(
    val name: String?,
    val architecture: String?,
    /** e.g. "1.7B", when the file declares it. */
    val sizeLabel: String?,
    /** e.g. "Q4_K_M", derived from general.file_type. */
    val quantization: String?,
    /** Context length the model was trained for. */
    val contextLength: Int?,
    /** Transformer layers; the ceiling for GPU offload. */
    val layerCount: Int?,
    val hasChatTemplate: Boolean,
    val embeddingLength: Int? = null,
    val headCount: Int? = null,
    val headCountKv: Int? = null,
    val keyLength: Int? = null,
    val valueLength: Int? = null,
    /** Built-in multi-token-prediction (NextN) layers, usable for speculative decoding. */
    val nextnLayers: Int? = null,
    /** Sampler values the model's authors recommend (general.sampling.*), if declared. */
    val recommendedSampling: Sampling? = null,
    /** Whether the built-in chat template reasons, and whether that can be switched. */
    val reasoning: Reasoning = Reasoning.None,
) {
    /**
     * Rough KV-cache size for [contextSize] tokens at 16-bit precision. Models
     * with sliding-window or state-space layers use less, so this is an upper bound.
     */
    /** An EAGLE3 speculative head: not a chat model, only a helper for its target model. */
    val isEagle3Head: Boolean get() = architecture == EAGLE3_ARCH

    fun kvCacheBytes(contextSize: Int): Long? {
        val layers = layerCount ?: return null
        val heads = headCount ?: return null
        val kvHeads = headCountKv ?: heads
        val headDim = (embeddingLength ?: return null) / heads
        val perToken = kvHeads.toLong() * ((keyLength ?: headDim) + (valueLength ?: headDim)) * 2L
        return perToken * layers * contextSize
    }
}

const val EAGLE3_ARCH = "eagle3"

/** How a chat template handles reasoning. */
enum class Reasoning {
    /** No reasoning: the toggle is hidden. */
    None,

    /** Reasons only when asked (`enable_thinking`: Qwen3, Gemma 4, SmolLM3, …). */
    Switchable,

    /** Always reasons (DeepSeek R1 distills, Qwen3 Thinking, gpt-oss). */
    Always,
}

/**
 * Reads a Jinja chat template for reasoning support. Switchable templates read the
 * `enable_thinking` flag the runtime passes; always-on ones open a think block in
 * the generation prompt or take a reasoning effort.
 */
fun reasoningOf(template: String): Reasoning =
    when {
        "enable_thinking" in template -> Reasoning.Switchable
        "reasoning_effort" in template -> Reasoning.Always
        ALWAYS_THINKS.containsMatchIn(template) -> Reasoning.Always
        else -> Reasoning.None
    }

private val ALWAYS_THINKS = Regex("""add_generation_prompt[\s\S]{0,300}?<think>""")

/** Architectures that are MTP drafters for another model, never chat models themselves. */
val MTP_HEAD_ARCHS = setOf("gemma4-assistant")

class NotGgufException : Exception("This file isn't a GGUF model")

/**
 * Streams through the GGUF key/value header. Token vocabularies (arrays of
 * ~150k strings) sit between the keys we want, so arrays are skipped in place
 * rather than materialised.
 */
object GgufReader {
    private const val MAGIC = 0x46554747 // "GGUF", little-endian

    fun read(input: InputStream): GgufInfo {
        val s = LittleEndian(BufferedInputStream(input, 1 shl 16))
        if (s.u32().toInt() != MAGIC) throw NotGgufException()
        val version = s.u32()
        if (version < 2) throw NotGgufException() // v1 used 32-bit counts; long obsolete
        s.u64() // tensor count
        val kvCount = s.u64()

        val strings = HashMap<String, String>()
        val ints = HashMap<String, Long>()
        val floats = HashMap<String, Double>()
        var hasTemplate = false
        var reasoning = Reasoning.None
        for (i in 0 until kvCount) {
            val key = s.string()
            when (val type = s.u32().toInt()) {
                TYPE_STRING -> {
                    if (key == "tokenizer.chat_template") {
                        hasTemplate = true
                        reasoning = reasoningOf(s.string())
                    } else {
                        strings[key] = s.string()
                    }
                }
                TYPE_ARRAY -> s.skipArray()
                TYPE_FLOAT32 -> floats[key] = s.float32().toDouble()
                TYPE_FLOAT64 -> floats[key] = Double.fromBits(s.u64())
                else -> s.scalar(type)?.let { ints[key] = it }
            }
        }

        val arch = strings["general.architecture"]
        return GgufInfo(
            name = strings["general.name"]?.takeIf { it.isNotBlank() },
            architecture = arch,
            sizeLabel = strings["general.size_label"]?.takeIf { it.isNotBlank() },
            quantization = ints["general.file_type"]?.let { fileTypeName(it.toInt()) },
            contextLength = arch?.let { ints["$it.context_length"]?.toInt() },
            layerCount = arch?.let { ints["$it.block_count"]?.toInt() },
            hasChatTemplate = hasTemplate,
            embeddingLength = arch?.let { ints["$it.embedding_length"]?.toInt() },
            headCount = arch?.let { ints["$it.attention.head_count"]?.toInt() },
            headCountKv = arch?.let { ints["$it.attention.head_count_kv"]?.toInt() },
            keyLength = arch?.let { ints["$it.attention.key_length"]?.toInt() },
            valueLength = arch?.let { ints["$it.attention.value_length"]?.toInt() },
            nextnLayers = arch?.let { ints["$it.nextn_predict_layers"]?.toInt() },
            recommendedSampling = recommendedSampling(ints, floats),
            reasoning = reasoning,
        )
    }

    private fun recommendedSampling(
        ints: Map<String, Long>,
        floats: Map<String, Double>,
    ): Sampling? {
        fun f(key: String) = floats["general.sampling.$key"]?.toFloat() ?: ints["general.sampling.$key"]?.toFloat()
        val temp = f("temp")
        val topP = f("top_p")
        val topK = ints["general.sampling.top_k"]?.toInt()
        val minP = f("min_p")
        val repeat = f("penalty_repeat")
        if (listOf(temp, topP, topK, minP, repeat).all { it == null }) return null
        val base = Sampling()
        return base.copy(
            temperature = temp ?: base.temperature,
            topP = topP ?: base.topP,
            topK = topK ?: base.topK,
            minP = minP ?: base.minP,
            repetitionPenalty = repeat ?: base.repetitionPenalty,
        )
    }

    /** llama.cpp's LLAMA_FTYPE_* values. */
    private fun fileTypeName(type: Int): String =
        when (type) {
            0 -> "F32"
            1 -> "F16"
            2 -> "Q4_0"
            3 -> "Q4_1"
            7 -> "Q8_0"
            8 -> "Q5_0"
            9 -> "Q5_1"
            10 -> "Q2_K"
            11 -> "Q3_K_S"
            12 -> "Q3_K_M"
            13 -> "Q3_K_L"
            14 -> "Q4_K_S"
            15 -> "Q4_K_M"
            16 -> "Q5_K_S"
            17 -> "Q5_K_M"
            18 -> "Q6_K"
            19 -> "IQ2_XXS"
            20 -> "IQ2_XS"
            21 -> "Q2_K_S"
            22 -> "IQ3_XS"
            23 -> "IQ3_XXS"
            24 -> "IQ1_S"
            25 -> "IQ4_NL"
            26 -> "IQ3_S"
            27 -> "IQ3_M"
            28 -> "IQ2_S"
            29 -> "IQ2_M"
            30 -> "IQ4_XS"
            31 -> "IQ1_M"
            32 -> "BF16"
            36 -> "TQ1_0"
            37 -> "TQ2_0"
            38 -> "MXFP4"
            else -> "type $type"
        }

    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    private fun width(type: Int): Int =
        when (type) {
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> 1
            TYPE_UINT16, TYPE_INT16 -> 2
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> 4
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> 8
            else -> throw NotGgufException()
        }

    private class LittleEndian(private val input: InputStream) {
        private val buf = ByteArray(8)

        private fun fill(n: Int) {
            var off = 0
            while (off < n) {
                val r = input.read(buf, off, n - off)
                if (r < 0) throw EOFException()
                off += r
            }
        }

        fun u32(): Long {
            fill(4)
            return (buf[0].toLong() and 0xFF) or
                ((buf[1].toLong() and 0xFF) shl 8) or
                ((buf[2].toLong() and 0xFF) shl 16) or
                ((buf[3].toLong() and 0xFF) shl 24)
        }

        fun u64(): Long {
            val lo = u32()
            val hi = u32()
            return lo or (hi shl 32)
        }

        fun string(): String {
            val len = u64()
            if (len < 0 || len > MAX_STRING) throw NotGgufException()
            val bytes = ByteArray(len.toInt())
            var off = 0
            while (off < bytes.size) {
                val r = input.read(bytes, off, bytes.size - off)
                if (r < 0) throw EOFException()
                off += r
            }
            return String(bytes, Charsets.UTF_8)
        }

        fun skip(n: Long) {
            var left = n
            while (left > 0) {
                val skipped = input.skip(left)
                if (skipped <= 0) {
                    if (input.read() < 0) throw EOFException()
                    left--
                } else {
                    left -= skipped
                }
            }
        }

        fun skipString() = skip(u64())

        fun float32(): Float = java.lang.Float.intBitsToFloat(u32().toInt())

        fun skipArray() {
            val elementType = u32().toInt()
            val count = u64()
            if (elementType == TYPE_STRING) {
                for (i in 0 until count) skipString()
            } else if (elementType == TYPE_ARRAY) {
                for (i in 0 until count) skipArray()
            } else {
                skip(width(elementType) * count)
            }
        }

        /** Integer-like scalars as Long; floats and bools are consumed and dropped. */
        fun scalar(type: Int): Long? =
            when (type) {
                TYPE_UINT8, TYPE_INT8 -> {
                    fill(1)
                    buf[0].toLong() and 0xFF
                }
                TYPE_UINT16, TYPE_INT16 -> {
                    fill(2)
                    (buf[0].toLong() and 0xFF) or ((buf[1].toLong() and 0xFF) shl 8)
                }
                TYPE_UINT32, TYPE_INT32 -> u32()
                TYPE_UINT64, TYPE_INT64 -> u64()
                TYPE_FLOAT32, TYPE_FLOAT64, TYPE_BOOL -> {
                    skip(width(type).toLong())
                    null
                }
                else -> throw NotGgufException()
            }

        companion object {
            const val MAX_STRING = 64L shl 20
        }
    }
}
