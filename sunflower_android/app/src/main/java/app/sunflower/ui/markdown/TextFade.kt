package app.sunflower.ui.markdown

import java.util.IdentityHashMap

/**
 * Remembers when each stretch of a streamed reply arrived, so new words can
 * fade in over [durationMs] instead of appearing all at once. Positions count
 * rendered characters (after Markdown), in reading order.
 */
class TextFade(
    initialLength: Int,
    private val durationMs: Long = FADE_MS,
) {
    /** Everything before this has finished fading. */
    private var settled = initialLength
    private val ends = ArrayList<Int>()
    private val times = ArrayList<Long>()

    /** Notes that the text is now [length] characters long; only growth counts. */
    fun observe(
        length: Int,
        time: Long,
    ) {
        val last = ends.lastOrNull() ?: settled
        if (length > last) {
            ends += length
            times += time
        }
    }

    /** Forgets arrivals that have fully faded in. */
    fun prune(now: Long) {
        while (times.isNotEmpty() && now - times[0] >= durationMs) {
            settled = maxOf(settled, ends[0])
            ends.removeAt(0)
            times.removeAt(0)
        }
    }

    /** Whether anything is still fading in. */
    fun active(now: Long): Boolean = times.any { now - it < durationMs }

    /** Whether characters [start, end) may still be fading. */
    fun touches(
        start: Int,
        end: Int,
    ): Boolean = ends.isNotEmpty() && end > settled && start < ends.last()

    /** The parts of [start, end) still fading in, each with its current opacity. */
    fun fading(
        start: Int,
        end: Int,
        now: Long,
    ): List<Fading> {
        val out = ArrayList<Fading>()
        var from = settled
        for (k in ends.indices) {
            val to = ends[k]
            val s = maxOf(from, start)
            val e = minOf(to, end)
            val alpha = ease((now - times[k]).toFloat() / durationMs)
            if (s < e && alpha < 1f) out += Fading(s, e, alpha)
            from = to
        }
        return out
    }

    private fun ease(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        val inv = 1f - t
        return 1f - inv * inv * inv
    }

    companion object {
        const val FADE_MS = 520L
    }
}

data class Fading(
    val start: Int,
    val end: Int,
    val alpha: Float,
)

/** Where each run of inline text starts in the rendered reply, and its total length. */
class RenderOffsets(
    val starts: IdentityHashMap<List<Run>, Int>,
    val total: Int,
)

/** Walks [blocks] in reading order, the same order they are drawn in. */
fun renderOffsets(blocks: List<Block>): RenderOffsets {
    val starts = IdentityHashMap<List<Run>, Int>()
    var offset = 0
    fun inline(runs: List<Run>) {
        starts[runs] = offset
        offset += runs.sumOf { it.text.length }
    }
    fun walk(block: Block) {
        when (block) {
            is Block.Paragraph -> inline(block.text)
            is Block.Heading -> inline(block.text)
            is Block.Code -> offset += block.code.length
            is Block.Quote -> block.blocks.forEach(::walk)
            is Block.ListBlock -> block.items.forEach { inline(it.text) }
            is Block.Table -> (listOf(block.header) + block.rows).forEach { row -> row.forEach(::inline) }
            Block.Rule -> Unit
        }
    }
    blocks.forEach(::walk)
    return RenderOffsets(starts, offset)
}
