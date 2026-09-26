package app.sunflower

import app.sunflower.engine.*
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.*

class GgufReaderTest {
    @Test fun readsRealisticHeader() {
        val info = GgufReader.read(GgufFixture.qwen().inputStream())
        assertEquals("Qwen3 1.7B Instruct", info.name)
        assertEquals("qwen3", info.architecture)
        assertEquals("1.7B", info.sizeLabel)
        assertEquals("Q4_K_M", info.quantization)
        assertEquals(40960, info.contextLength)
        assertEquals(28, info.layerCount)
        assertTrue(info.hasChatTemplate)
    }

    @Test fun rejectsNonGguf() {
        assertFailsWith<NotGgufException> { GgufReader.read(ByteArrayInputStream("PK\u0003\u0004 not a model at all".toByteArray())) }
    }

    @Test fun truncatedFileFailsCleanly() {
        val bytes = GgufFixture.qwen().copyOf(5000)
        assertFails { GgufReader.read(ByteArrayInputStream(bytes)) }
    }
}

class ThinkingTest {
    @Test fun plainAnswer() = assertEquals(ThinkSplit(null, "Hello", false), splitThinking("Hello", false))
    @Test fun closedBlock() = assertEquals(ThinkSplit("plan", "Answer", false), splitThinking("<think>\nplan\n</think>\n\nAnswer", false))
    @Test fun openBlockWhileStreaming() = assertEquals(ThinkSplit("still going", "", true), splitThinking("<think>still going", false))
    @Test fun templateOpenedTag() = assertEquals(ThinkSplit("reasoning", "Done", false), splitThinking("reasoning</think>Done", true))
    @Test fun templateOpenedStillThinking() = assertEquals(ThinkSplit("reason", "", true), splitThinking("reason", true))
    @Test fun emptyThinkingBlockHidden() = assertEquals(ThinkSplit(null, "Hi", false), splitThinking("<think>\n\n</think>\n\nHi", false))
    @Test fun partialOpenTagHidden() = assertEquals(ThinkSplit(null, "", false), splitThinking("<thi", false))
    @Test fun partialCloseTagHidden() = assertEquals(ThinkSplit("abc", "", true), splitThinking("<think>abc</thi", false))
    @Test fun lessThanInAnswerKept() = assertEquals("a < b", splitThinking("a < b", false).content)
}

class ContextTest {
    private fun t(role: String, n: Int) = Turn(role, "x".repeat(n))

    @Test fun keepsEverythingWhenItFits() {
        val turns = listOf(t("user", 100), t("assistant", 100), t("user", 100))
        assertEquals(turns, fitToContext("sys", turns, 4096, 1024))
    }

    @Test fun dropsOldestFirstAndStartsOnUser() {
        val turns = listOf(t("user", 3500), t("assistant", 3500), t("user", 3500), t("assistant", 3500), t("user", 350))
        val kept = fitToContext("sys", turns, 4096, 1024)
        assertEquals("user", kept.first().role)
        assertEquals(turns.last(), kept.last())
        assertTrue(kept.size < turns.size)
    }

    @Test fun alwaysKeepsLatestEvenIfHuge() {
        val turns = listOf(t("user", 10), t("assistant", 10), t("user", 100000))
        assertEquals(listOf(turns.last()), fitToContext("sys", turns, 2048, 512))
    }
}
