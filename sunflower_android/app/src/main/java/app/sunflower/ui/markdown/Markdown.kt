package app.sunflower.ui.markdown

/**
 * A small Markdown model covering what chat models actually write: headings,
 * paragraphs, lists, quotes, fenced code, tables and rules, with bold, italic,
 * strikethrough, inline code and links inside text.
 *
 * It must cope with half-written input, since it re-parses on every streamed
 * update: an unclosed code fence is still code, an unclosed `**` is just text.
 */
sealed interface Block {
    data class Heading(val level: Int, val text: List<Run>) : Block

    data class Paragraph(val text: List<Run>) : Block

    data class Code(val language: String?, val code: String) : Block

    data class Quote(val blocks: List<Block>) : Block

    data class ListBlock(val ordered: Boolean, val items: List<ListItem>) : Block

    data class Table(val header: List<List<Run>>, val rows: List<List<List<Run>>>) : Block

    data object Rule : Block
}

data class ListItem(
    /** Number shown for ordered lists, as written ("1", "2"…). */
    val marker: String?,
    val depth: Int,
    val text: List<Run>,
)

/** A stretch of inline text with uniform styling. */
data class Run(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

private val HEADING = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
private val FENCE = Regex("^\\s{0,3}(`{3,}|~{3,})\\s*([^`\\s]*).*$")
private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
private val LIST_ITEM = Regex("^(\\s*)([-*+]|(\\d{1,9})[.)])\\s+(.*)$")
private val TABLE_DIVIDER = Regex("^\\s*\\|?\\s*:?-{1,}:?\\s*(\\|\\s*:?-{1,}:?\\s*)*\\|?\\s*$")
private val QUOTE = Regex("^\\s{0,3}>\\s?(.*)$")

fun parseMarkdown(source: String): List<Block> = parseLines(source.replace("\r\n", "\n").split('\n'))

private fun parseLines(lines: List<String>): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) {
            blocks += Block.Paragraph(parseInline(paragraph.joinToString("\n")))
            paragraph.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        val fenceMatch = FENCE.matchEntire(line)
        if (fenceMatch != null) {
            flush()
            val fence = fenceMatch.groupValues[1]
            val language = fenceMatch.groupValues[2].ifBlank { null }
            val code = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith(fence)) code += lines[i++]
            i++ // closing fence (or past the end while still streaming)
            blocks += Block.Code(language, code.joinToString("\n"))
            continue
        }

        if (line.isBlank()) {
            flush()
            i++
            continue
        }

        val headingMatch = HEADING.matchEntire(line)
        if (headingMatch != null) {
            flush()
            blocks += Block.Heading(headingMatch.groupValues[1].length, parseInline(headingMatch.groupValues[2]))
            i++
            continue
        }

        if (RULE.matches(line)) {
            flush()
            blocks += Block.Rule
            i++
            continue
        }

        if (QUOTE.matches(line)) {
            flush()
            val inner = mutableListOf<String>()
            while (i < lines.size) {
                val m = QUOTE.matchEntire(lines[i]) ?: break
                inner += m.groupValues[1]
                i++
            }
            blocks += Block.Quote(parseLines(inner))
            continue
        }

        if (LIST_ITEM.matches(line)) {
            flush()
            val ordered = LIST_ITEM.matchEntire(line)!!.groupValues[3].isNotEmpty()
            val items = mutableListOf<ListItem>()
            val baseIndent = LIST_ITEM.matchEntire(line)!!.groupValues[1].length
            while (i < lines.size) {
                val current = lines[i]
                val m = LIST_ITEM.matchEntire(current)
                if (m != null) {
                    val indent = m.groupValues[1].length
                    val depth = ((indent - baseIndent).coerceAtLeast(0) / 2).coerceAtMost(3)
                    items += ListItem(m.groupValues[3].ifEmpty { null }, depth, parseInline(m.groupValues[4]))
                    i++
                } else if (current.isNotBlank() && current.startsWith(" ") && items.isNotEmpty()) {
                    // Continuation line of the previous item.
                    val last = items.removeAt(items.lastIndex)
                    items += last.copy(text = last.text + Run("\n") + parseInline(current.trim()))
                    i++
                } else if (current.isBlank() && i + 1 < lines.size && LIST_ITEM.matches(lines[i + 1])) {
                    i++ // loose list: blank line between items
                } else {
                    break
                }
            }
            blocks += Block.ListBlock(ordered, items)
            continue
        }

        if (line.contains('|') && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1]) && lines[i + 1].contains('-')) {
            flush()
            val header = cells(line)
            i += 2
            val rows = mutableListOf<List<List<Run>>>()
            while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                rows += cells(lines[i])
                i++
            }
            blocks += Block.Table(header, rows)
            continue
        }

        paragraph += line
        i++
    }
    flush()
    return blocks
}

private fun cells(line: String): List<List<Run>> =
    line
        .trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split('|')
        .map { parseInline(it.trim()) }

/** Parses inline styling. Unclosed markers are kept as literal text. */
fun parseInline(text: String): List<Run> {
    val out = mutableListOf<Run>()
    parseInlineInto(text, Run(""), out)
    return merge(out)
}

private fun parseInlineInto(
    text: String,
    style: Run,
    out: MutableList<Run>,
) {
    val plain = StringBuilder()
    fun emitPlain() {
        if (plain.isNotEmpty()) {
            out += style.copy(text = plain.toString())
            plain.clear()
        }
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                plain.append(text[i + 1])
                i += 2
            }
            c == '`' -> {
                val ticks = text.countRun(i, '`')
                val close = text.indexOf("`".repeat(ticks), i + ticks)
                if (close < 0) {
                    plain.append(text, i, i + ticks)
                    i += ticks
                } else {
                    emitPlain()
                    out += style.copy(text = text.substring(i + ticks, close).trim(), code = true)
                    i = close + ticks
                }
            }
            c == '[' -> {
                val closeText = findClosing(text, i + 1, ']')
                if (closeText > 0 && closeText + 1 < text.length && text[closeText + 1] == '(') {
                    val closeUrl = text.indexOf(')', closeText + 2)
                    if (closeUrl > 0) {
                        emitPlain()
                        val url = text.substring(closeText + 2, closeUrl).trim()
                        parseInlineInto(text.substring(i + 1, closeText), style.copy(link = url), out)
                        i = closeUrl + 1
                        continue
                    }
                }
                plain.append(c)
                i++
            }
            text.startsWith("~~", i) -> i = delimited(text, i, "~~", style.copy(strike = true), plain, out, ::emitPlain)
            text.startsWith("**", i) -> i = delimited(text, i, "**", style.copy(bold = true), plain, out, ::emitPlain)
            text.startsWith("__", i) && isLeftBoundary(text, i) ->
                i = delimited(text, i, "__", style.copy(bold = true), plain, out, ::emitPlain, requireBoundary = true)
            c == '*' && i + 1 < text.length && !text[i + 1].isWhitespace() ->
                i = delimited(text, i, "*", style.copy(italic = true), plain, out, ::emitPlain)
            c == '_' && isLeftBoundary(text, i) && i + 1 < text.length && !text[i + 1].isWhitespace() ->
                i = delimited(text, i, "_", style.copy(italic = true), plain, out, ::emitPlain, requireBoundary = true)
            else -> {
                plain.append(c)
                i++
            }
        }
    }
    emitPlain()
}

/** Handles a symmetric delimiter pair; returns the index after it, or treats it as text if unclosed. */
private fun delimited(
    text: String,
    start: Int,
    marker: String,
    innerStyle: Run,
    plain: StringBuilder,
    out: MutableList<Run>,
    emitPlain: () -> Unit,
    requireBoundary: Boolean = false,
): Int {
    var search = start + marker.length
    while (true) {
        var close = text.indexOf(marker, search)
        if (close < 0) break
        // In "***", a double marker closes on the last two, leaving the first to close an inner "*".
        if (marker.length == 2) {
            val run = text.countRun(close, marker[0])
            if (run > 2) close += run - 2
        }
        val validClose =
            close > start + marker.length &&
                !text[close - 1].isWhitespace() &&
                (!requireBoundary || isRightBoundary(text, close + marker.length)) &&
                // "**" must not be read as the close of a single "*".
                !(marker == "*" && text.startsWith("**", close) && !text.startsWith("***", close))
        if (validClose) {
            emitPlain()
            parseInlineInto(text.substring(start + marker.length, close), innerStyle, out)
            return close + marker.length
        }
        search = close + marker.length
    }
    plain.append(marker)
    return start + marker.length
}

private fun isLeftBoundary(
    text: String,
    i: Int,
) = i == 0 || !text[i - 1].isLetterOrDigit()

private fun isRightBoundary(
    text: String,
    i: Int,
) = i >= text.length || !text[i].isLetterOrDigit()

private fun findClosing(
    text: String,
    from: Int,
    close: Char,
): Int {
    var depth = 0
    for (j in from until text.length) {
        when (text[j]) {
            '[' -> depth++
            close -> if (depth == 0) return j else depth--
            '\n' -> return -1
        }
    }
    return -1
}

private fun String.countRun(
    from: Int,
    ch: Char,
): Int {
    var n = 0
    while (from + n < length && this[from + n] == ch) n++
    return n
}

/** Joins neighbouring runs with identical styling. */
private fun merge(runs: List<Run>): List<Run> {
    val merged = mutableListOf<Run>()
    for (run in runs) {
        if (run.text.isEmpty()) continue
        val last = merged.lastOrNull()
        if (last != null && last.copy(text = "") == run.copy(text = "")) {
            merged[merged.lastIndex] = last.copy(text = last.text + run.text)
        } else {
            merged += run
        }
    }
    return merged
}

private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~>"
