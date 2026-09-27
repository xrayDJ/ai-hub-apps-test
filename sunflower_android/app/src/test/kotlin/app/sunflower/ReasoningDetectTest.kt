package app.sunflower

import app.sunflower.engine.Reasoning
import app.sunflower.engine.reasoningOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ReasoningDetectTest {
    @Test
    fun qwen3SwitchesWithEnableThinking() {
        val template =
            "{%- if add_generation_prompt %}{{- '<|im_start|>assistant\\n' }}" +
                "{%- if enable_thinking is defined and enable_thinking is false %}{{- '<think>\\n\\n</think>\\n\\n' }}{%- endif %}{%- endif %}"
        assertEquals(Reasoning.Switchable, reasoningOf(template))
    }

    @Test
    fun r1DistillAlwaysThinks() {
        val template = "{% if add_generation_prompt %}{{'<｜Assistant｜><think>\\n'}}{% endif %}"
        assertEquals(Reasoning.Always, reasoningOf(template))
    }

    @Test
    fun gptOssAlwaysThinks() {
        assertEquals(Reasoning.Always, reasoningOf("Reasoning: {{ reasoning_effort }}<|channel|>analysis"))
    }

    @Test
    fun instructModelsDoNotThink() {
        // Qwen3 2507 Instruct only strips old think blocks from history.
        val template =
            "{%- if '</think>' in content %}{%- set content = content.split('</think>')[-1] %}{%- endif %}" +
                "{%- if add_generation_prompt %}{{- '<|im_start|>assistant\\n' }}{%- endif %}"
        assertEquals(Reasoning.None, reasoningOf(template))
        assertEquals(Reasoning.None, reasoningOf("{{ bos_token }}{% for m in messages %}{{ m.content }}{% endfor %}"))
    }
}
