package app.sunflower.engine

/** A reply split into the model's reasoning and the answer itself. */
data class ThinkSplit(
    val thinking: String?,
    val content: String,
    /** True while the reasoning block has opened but not closed yet. */
    val thinkingOpen: Boolean,
)

/** How a model family marks its reasoning. */
data class ReasoningFormat(
    val open: String,
    val close: String,
    /** Wrapper tokens that can follow the close before the answer (removed from the answer). */
    val answerPrefixes: List<String> = emptyList(),
)

val REASONING_FORMATS =
    listOf(
        // Qwen3, DeepSeek R1 and most open reasoning models.
        ReasoningFormat("<think>", "</think>"),
        // Gemma 4.
        ReasoningFormat("<|channel>thought", "<channel|>"),
        // Mistral Magistral.
        ReasoningFormat("[THINK]", "[/THINK]"),
        // gpt-oss (harmony): analysis channel, then the final channel.
        ReasoningFormat(
            "<|channel|>analysis<|message|>",
            "<|end|>",
            answerPrefixes = listOf("<|start|>assistant<|channel|>final<|message|>", "<|channel|>final<|message|>"),
        ),
    )

private val STRAY_TOKENS = listOf("<|return|>", "<|end|>", "<end_of_turn>")

/** The reasoning format whose opening marker a chat template left at the end of the prompt, if any. */
fun reasoningOpenedByPrompt(prompt: String): ReasoningFormat? {
    val tail = prompt.trimEnd()
    return REASONING_FORMATS.firstOrNull { tail.endsWith(it.open) }
}

/**
 * Separates reasoning from the answer for any known format. Works on partial
 * text, so it can run on every streamed update.
 *
 * [openedByPrompt] covers chat templates that put the opening marker in the
 * prompt, so the reply begins mid-reasoning with no marker of its own.
 */
fun splitThinking(
    raw: String,
    openedByPrompt: ReasoningFormat? = null,
): ThinkSplit {
    val text = if (openedByPrompt != null) openedByPrompt.open + raw else raw
    val (format, open) =
        REASONING_FORMATS
            .map { it to text.indexOf(it.open) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
            ?: return ThinkSplit(null, clean(text), thinkingOpen = false)

    val before = text.substring(0, open)
    val bodyStart = open + format.open.length
    val close = text.indexOf(format.close, bodyStart)
    if (close < 0) {
        return ThinkSplit(trimPartialMarker(text.substring(bodyStart)).trim(), clean(before), thinkingOpen = true)
    }
    val thinking = text.substring(bodyStart, close).trim()
    var after = text.substring(close + format.close.length)
    format.answerPrefixes.forEach { prefix -> after = after.trimStart().removePrefix(prefix) }
    return ThinkSplit(thinking.ifEmpty { null }, clean(before + after), thinkingOpen = false)
}

/** Keeps the old call shape used by `<think>`-style templates. */
fun splitThinking(
    raw: String,
    startsInThinking: Boolean,
): ThinkSplit = splitThinking(raw, if (startsInThinking) REASONING_FORMATS.first() else null)

private fun clean(text: String): String {
    var result = trimPartialMarker(text)
    STRAY_TOKENS.forEach { result = result.replace(it, "") }
    return result.trim()
}

/** Hides a marker that is still arriving, e.g. a trailing "</thi" or "<|chan" mid-stream. */
private fun trimPartialMarker(text: String): String {
    val markers = REASONING_FORMATS.flatMap { listOf(it.open, it.close) + it.answerPrefixes } + STRAY_TOKENS
    for (start in (text.length - 1) downTo maxOf(0, text.length - 40)) {
        val c = text[start]
        if (c != '<' && c != '[') continue
        val tail = text.substring(start)
        if (markers.any { it.length > tail.length && it.startsWith(tail) }) return text.substring(0, start)
    }
    return text
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
