package app.sunflower

import app.sunflower.data.snippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnippetTest {
    @Test
    fun marksTheMatchIgnoringCase() {
        val s = assertNotNull(snippet("Sunflowers turn to face the sun.", "FACE"))
        assertEquals("face", s.text.substring(s.matchStart, s.matchEnd))
        assertEquals("Sunflowers turn to face the sun.", s.text)
    }

    @Test
    fun cutsLongTextAroundTheMatchOnAWord() {
        val long = "word ".repeat(40) + "needle " + "tail ".repeat(40)
        val s = assertNotNull(snippet(long, "needle"))
        assertTrue(s.text.startsWith("…word"))
        assertTrue(s.text.endsWith("…"))
        assertEquals("needle", s.text.substring(s.matchStart, s.matchEnd))
    }

    @Test
    fun flattensLinesAndMissesCleanly() {
        val s = assertNotNull(snippet("first line\n\nsecond   line", "second"))
        assertEquals("first line second line", s.text)
        assertNull(snippet("nothing here", "absent"))
    }
}
