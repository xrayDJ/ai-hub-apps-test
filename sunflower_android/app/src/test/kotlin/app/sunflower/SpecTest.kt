package app.sunflower

import app.sunflower.engine.*
import java.io.File
import kotlin.test.*

class SpeculativeHeaderTest {
    private fun read(name: String) =
        GgufReader.read(
            when (name) {
                "mtp.gguf" -> GgufFixture.mtpModel()
                "eagle.gguf" -> GgufFixture.eagleHead()
                else -> GgufFixture.qwen()
            }.inputStream(),
        )
    @Test fun mtpDetected() { val i = read("mtp.gguf"); assertEquals(1, i.nextnLayers); assertFalse(i.isEagle3Head) }
    @Test fun eagleDetected() { val i = read("eagle.gguf"); assertTrue(i.isEagle3Head); assertNull(i.nextnLayers) }
    @Test fun plainModelHasNeither() { val i = read("test.gguf"); assertNull(i.nextnLayers); assertFalse(i.isEagle3Head) }
}
