package app.sunflower

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds small GGUF headers in memory, shaped like real files (large vocabularies included). */
class GgufFixture(private val architecture: String) {
    private val kvs = mutableListOf<Pair<String, (Out) -> Unit>>()

    fun string(key: String, value: String) = apply { kvs += key to { it.u32(8); it.str(value) } }

    fun u32(key: String, value: Int) = apply { kvs += key to { it.u32(4); it.u32(value.toLong()) } }

    fun i32(key: String, value: Int) = apply { kvs += key to { it.u32(5); it.u32(value.toLong()) } }

    fun f32(key: String, value: Float) = apply { kvs += key to { it.u32(6); it.u32(java.lang.Float.floatToIntBits(value).toLong()) } }

    fun bool(key: String, value: Boolean) = apply { kvs += key to { it.u32(7); it.byte(if (value) 1 else 0) } }

    fun strings(key: String, values: List<String>) =
        apply {
            kvs += key to { out ->
                out.u32(9)
                out.u32(8)
                out.u64(values.size.toLong())
                values.forEach(out::str)
            }
        }

    fun ints(key: String, values: List<Int>) =
        apply {
            kvs += key to { out ->
                out.u32(9)
                out.u32(5)
                out.u64(values.size.toLong())
                values.forEach { out.u32(it.toLong()) }
            }
        }

    fun build(): ByteArray {
        val out = Out()
        out.u32(0x46554747) // "GGUF"
        out.u32(3)
        out.u64(0) // tensors
        out.u64(kvs.size.toLong() + 1)
        out.str("general.architecture")
        out.u32(8)
        out.str(architecture)
        kvs.forEach { (key, write) ->
            out.str(key)
            write(out)
        }
        return out.bytes()
    }

    class Out {
        private val stream = ByteArrayOutputStream()
        private val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

        fun u32(v: Long) {
            buf.clear()
            buf.putInt(v.toInt())
            stream.write(buf.array(), 0, 4)
        }

        fun u64(v: Long) {
            buf.clear()
            buf.putLong(v)
            stream.write(buf.array(), 0, 8)
        }

        fun byte(v: Int) = stream.write(v)

        fun str(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            u64(b.size.toLong())
            stream.write(b)
        }

        fun bytes(): ByteArray = stream.toByteArray()
    }

    companion object {
        /** A Qwen3-1.7B-like header: attention shape, recommended sampling, 152k-token vocabulary, template last. */
        fun qwen(): ByteArray =
            GgufFixture("qwen3")
                .string("general.name", "Qwen3 1.7B Instruct")
                .string("general.size_label", "1.7B")
                .u32("general.file_type", 15)
                .u32("qwen3.context_length", 40960)
                .u32("qwen3.block_count", 28)
                .u32("qwen3.embedding_length", 2048)
                .u32("qwen3.attention.head_count", 16)
                .u32("qwen3.attention.head_count_kv", 8)
                .u32("qwen3.attention.key_length", 128)
                .u32("qwen3.attention.value_length", 128)
                .f32("qwen3.rope.freq_base", 1_000_000f)
                .f32("general.sampling.temp", 0.6f)
                .f32("general.sampling.top_p", 0.95f)
                .i32("general.sampling.top_k", 20)
                .bool("tokenizer.ggml.add_bos_token", false)
                .strings("tokenizer.ggml.tokens", List(151_936) { "tok$it" })
                .ints("tokenizer.ggml.token_type", List(151_936) { 1 })
                .string("tokenizer.chat_template", "{% for m in messages %}<|im_start|>{{m.role}}\n{{m.content}}<|im_end|>\n{% endfor %}".repeat(50))
                .u32("tokenizer.ggml.eos_token_id", 151_645)
                .build()

        fun mtpModel(): ByteArray =
            GgufFixture("glm4moe").string("general.name", "GLM MTP test").u32("glm4moe.block_count", 47).u32("glm4moe.nextn_predict_layers", 1).build()

        fun eagleHead(): ByteArray = GgufFixture("eagle3").string("general.name", "EAGLE3 head").ints("eagle3.extract_layers", listOf(2, 16, 29)).build()
    }
}
