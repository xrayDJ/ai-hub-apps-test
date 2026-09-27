package app.sunflower.data

/** A short excerpt around a match; [matchStart] and [matchEnd] index into [text]. */
data class Snippet(
    val text: String,
    val matchStart: Int,
    val matchEnd: Int,
)

/** Cuts a one-line excerpt around the first case-insensitive match of [query]. */
fun snippet(
    content: String,
    query: String,
    before: Int = 36,
    after: Int = 90,
): Snippet? {
    val flat = content.replace(Regex("\\s+"), " ").trim()
    val at = flat.indexOf(query.trim(), ignoreCase = true)
    if (at < 0) return null
    val start = (at - before).coerceAtLeast(0).let { s -> if (s > 0) flat.indexOf(' ', s).takeIf { it in s until at }?.plus(1) ?: s else 0 }
    val end = (at + query.trim().length + after).coerceAtMost(flat.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < flat.length) "…" else ""
    val text = prefix + flat.substring(start, end) + suffix
    val matchStart = prefix.length + (at - start)
    return Snippet(text, matchStart, matchStart + query.trim().length)
}
