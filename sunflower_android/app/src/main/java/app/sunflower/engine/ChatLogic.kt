package app.sunflower.engine

/** A reply split into the model's reasoning and the answer itself. */
data class ThinkSplit(
    val thinking: String?,
    val content: String,
    /** True while the reasoning block has opened but not closed yet. */
    val thinkingOpen: Boolean,
)

private const val OPEN = "<think>"
private const val CLOSE = "</think>"

/**
 * Separates `<think>…</think>` reasoning from the answer. Works on partial
 * text, so it can run on every streamed update.
 *
 * [startsInThinking] covers chat templates (Qwen3 and others) that put the
 * opening tag in the prompt, so the reply begins mid-reasoning with no tag.
 */
fun splitThinking(
    raw: String,
    startsInThinking: Boolean,
): ThinkSplit {
    val text = if (startsInThinking) OPEN + raw else raw
    val open = text.indexOf(OPEN)
    if (open < 0) return ThinkSplit(null, trimPartialTag(text).trim(), thinkingOpen = false)

    val before = text.substring(0, open)
    val close = text.indexOf(CLOSE, open + OPEN.length)
    if (close < 0) {
        val thinking = trimPartialTag(text.substring(open + OPEN.length)).trim()
        return ThinkSplit(thinking, before.trim(), thinkingOpen = true)
    }
    val thinking = text.substring(open + OPEN.length, close).trim()
    val after = text.substring(close + CLOSE.length)
    return ThinkSplit(thinking.ifEmpty { null }, trimPartialTag(before + after).trim(), thinkingOpen = false)
}

/** Hides a tag that is still arriving, e.g. a trailing "</thi" mid-stream. */
private fun trimPartialTag(text: String): String {
    val lt = text.lastIndexOf('<')
    if (lt < 0) return text
    val tail = text.substring(lt)
    return if ((OPEN.startsWith(tail) || CLOSE.startsWith(tail)) && tail.length < CLOSE.length) text.substring(0, lt) else text
}

data class Turn(
    val role: String,
    val content: String,
)

/**
 * Keeps the most recent turns that fit in the context window, always keeping
 * the latest one. Token counts are estimated at ~3.5 characters per token,
 * which errs on the side of trimming slightly early.
 */
fun fitToContext(
    systemPrompt: String,
    turns: List<Turn>,
    contextTokens: Int,
    reservedForReply: Int,
): List<Turn> {
    if (turns.isEmpty()) return turns
    val overheadPerTurn = 8 // role markers and separators the template adds
    var budget = contextTokens - reservedForReply - estimateTokens(systemPrompt) - overheadPerTurn
    val kept = ArrayDeque<Turn>()
    for (turn in turns.asReversed()) {
        val cost = estimateTokens(turn.content) + overheadPerTurn
        if (kept.isNotEmpty() && cost > budget) break
        kept.addFirst(turn)
        budget -= cost
    }
    // A reply to an assistant message makes no sense to the template; start on a user turn.
    while (kept.size > 1 && kept.first().role != "user") kept.removeFirst()
    return kept.toList()
}

fun estimateTokens(text: String): Int = (text.length / 3.5).toInt() + 1
