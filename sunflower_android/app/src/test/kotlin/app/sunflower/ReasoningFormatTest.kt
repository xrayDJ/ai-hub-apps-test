package app.sunflower

import app.sunflower.engine.REASONING_FORMATS
import app.sunflower.engine.ThinkSplit
import app.sunflower.engine.reasoningOpenedByPrompt
import app.sunflower.engine.splitThinking
import kotlin.test.Test
import kotlin.test.assertEquals

class ReasoningFormatTest {
    @Test fun gemma4Closed() =
        assertEquals(
            ThinkSplit("User wants a deeper thought.", "Blue can be tranquil.", false),
            splitThinking("<|channel>thought\nUser wants a deeper thought.<channel|>Blue can be tranquil."),
        )

    @Test fun gemma4Streaming() = assertEquals(ThinkSplit("User wants", "", true), splitThinking("<|channel>thought\nUser wants"))

    @Test fun gemma4PartialOpenHidden() = assertEquals(ThinkSplit(null, "", false), splitThinking("<|chan"))

    @Test fun gemma4PartialCloseHidden() = assertEquals(ThinkSplit("plan", "", true), splitThinking("<|channel>thought plan<chann"))

    @Test fun magistral() = assertEquals(ThinkSplit("steps", "Answer", false), splitThinking("[THINK]steps[/THINK]Answer"))

    @Test fun gptOss() =
        assertEquals(
            ThinkSplit("reasoning here", "Final answer", false),
            splitThinking("<|channel|>analysis<|message|>reasoning here<|end|><|start|>assistant<|channel|>final<|message|>Final answer<|return|>"),
        )

    @Test fun promptOpenedGemma() {
        val format = reasoningOpenedByPrompt("<start_of_turn>model\n<|channel>thought\n")
        assertEquals(REASONING_FORMATS[1], format)
        assertEquals(ThinkSplit("mid", "done", false), splitThinking("mid<channel|>done", format))
    }

    @Test fun plainTextUntouched() = assertEquals(ThinkSplit(null, "a < b and [x]", false), splitThinking("a < b and [x]"))

    @Test fun earliestMarkerWins() = assertEquals("x", splitThinking("<think>x</think>[THINK]y[/THINK]").thinking)
}
