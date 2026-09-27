package app.sunflower

import app.sunflower.ui.markdown.TextFade
import app.sunflower.ui.markdown.parseMarkdown
import app.sunflower.ui.markdown.renderOffsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextFadeTest {
    @Test
    fun newTextFadesInAndSettles() {
        val fade = TextFade(initialLength = 5, durationMs = 500)
        fade.observe(9, time = 1_000)
        val early = fade.fading(0, 20, now = 1_000)
        assertEquals(1, early.size)
        assertEquals(5, early[0].start)
        assertEquals(9, early[0].end)
        assertEquals(0f, early[0].alpha)
        assertTrue(fade.fading(0, 20, now = 1_250)[0].alpha in 0.5f..0.99f)
        assertTrue(fade.fading(0, 20, now = 1_500).isEmpty())
        fade.prune(now = 1_500)
        assertFalse(fade.active(now = 1_500))
        assertFalse(fade.touches(0, 9))
    }

    @Test
    fun onlyGrowthIsTracked() {
        val fade = TextFade(initialLength = 0, durationMs = 500)
        fade.observe(4, time = 0)
        fade.observe(3, time = 10)
        fade.observe(8, time = 100)
        val parts = fade.fading(0, 8, now = 100)
        assertEquals(listOf(0 to 4, 4 to 8), parts.map { it.start to it.end })
        assertTrue(parts[0].alpha > parts[1].alpha)
    }

    @Test
    fun offsetsFollowReadingOrder() {
        val blocks = parseMarkdown("Hello **you**\n\n- one\n- two")
        val offsets = renderOffsets(blocks)
        assertEquals("Hello you".length + "one".length + "two".length, offsets.total)
        assertEquals(0, offsets.starts.values.min())
    }
}
