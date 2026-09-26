package app.sunflower

import app.sunflower.ui.markdown.*
import kotlin.test.*

class InlineTest {
    @Test fun plain() = assertEquals(listOf(Run("hello world")), parseInline("hello world"))
    @Test fun bold() = assertEquals(listOf(Run("a "), Run("b", bold = true), Run(" c")), parseInline("a **b** c"))
    @Test fun italicStar() = assertEquals(listOf(Run("x", italic = true)), parseInline("*x*"))
    @Test fun boldInsideItalicNotConfused() = assertEquals(listOf(Run("a", bold = true), Run(" and "), Run("b", italic = true)), parseInline("**a** and *b*"))
    @Test fun nested() = assertEquals(listOf(Run("very ", bold = true), Run("big", bold = true, italic = true)), parseInline("**very *big***"))
    @Test fun code() = assertEquals(listOf(Run("use "), Run("x = 1", code = true)), parseInline("use `x = 1`"))
    @Test fun codeKeepsStars() = assertEquals(listOf(Run("**not bold**", code = true)), parseInline("`**not bold**`"))
    @Test fun strike() = assertEquals(listOf(Run("gone", strike = true)), parseInline("~~gone~~"))
    @Test fun link() = assertEquals(listOf(Run("see "), Run("docs", link = "https://x.y")), parseInline("see [docs](https://x.y)"))
    @Test fun snakeCaseIsNotItalic() = assertEquals(listOf(Run("my_var_name")), parseInline("my_var_name"))
    @Test fun unclosedBoldStaysLiteral() = assertEquals(listOf(Run("**half")), parseInline("**half"))
    @Test fun unclosedCodeStaysLiteral() = assertEquals(listOf(Run("`open")), parseInline("`open"))
    @Test fun multiplicationStar() = assertEquals(listOf(Run("2 * 3 * 4")), parseInline("2 * 3 * 4"))
    @Test fun escaped() = assertEquals(listOf(Run("*literal*")), parseInline("\\*literal\\*"))
}

class BlockTest {
    @Test fun headingAndParagraph() {
        val b = parseMarkdown("# Title\nSome text\nmore")
        assertEquals(Block.Heading(1, listOf(Run("Title"))), b[0])
        assertEquals(Block.Paragraph(listOf(Run("Some text\nmore"))), b[1])
    }
    @Test fun fencedCode() {
        val b = parseMarkdown("Intro\n```kotlin\nval x = 1\n\nprintln(x)\n```\nAfter")
        assertEquals(3, b.size)
        assertEquals(Block.Code("kotlin", "val x = 1\n\nprintln(x)"), b[1])
    }
    @Test fun unclosedFenceWhileStreaming() {
        val b = parseMarkdown("```py\nprint(1)\nprint(")
        assertEquals(listOf<Block>(Block.Code("py", "print(1)\nprint(")), b)
    }
    @Test fun bulletList() {
        val b = parseMarkdown("- one\n- **two**\n  - nested\n- three")
        val list = b.single() as Block.ListBlock
        assertFalse(list.ordered)
        assertEquals(4, list.items.size)
        assertEquals(1, list.items[2].depth)
        assertEquals(Run("two", bold = true), list.items[1].text.single())
    }
    @Test fun orderedList() {
        val list = parseMarkdown("1. a\n2. b\n\n3. c").single() as Block.ListBlock
        assertTrue(list.ordered)
        assertEquals(listOf("1", "2", "3"), list.items.map { it.marker })
    }
    @Test fun quote() {
        val q = parseMarkdown("> quoted **bold**\n> line").single() as Block.Quote
        assertTrue(q.blocks.single() is Block.Paragraph)
    }
    @Test fun table() {
        val t = parseMarkdown("| A | B |\n|---|:-:|\n| 1 | 2 |\n| 3 | 4 |").single() as Block.Table
        assertEquals(2, t.header.size)
        assertEquals(2, t.rows.size)
        assertEquals(listOf(Run("4")), t.rows[1][1])
    }
    @Test fun rule() = assertEquals(listOf<Block>(Block.Rule), parseMarkdown("---"))
    @Test fun listStopsAtParagraph() {
        val b = parseMarkdown("- a\n- b\n\nParagraph")
        assertEquals(2, b.size)
        assertTrue(b[1] is Block.Paragraph)
    }
    @Test fun pipeInTextIsNotTable() = assertTrue(parseMarkdown("a | b").single() is Block.Paragraph)
    @Test fun emptyInput() = assertEquals(emptyList(), parseMarkdown(""))
}
