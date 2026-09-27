package app.sunflower

import app.sunflower.ui.models.contextLabel
import app.sunflower.ui.models.quantBits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelSpecsTest {
    @Test
    fun bitsFromQuantizationNames() {
        assertEquals(4, quantBits("Q4_K_M"))
        assertEquals(2, quantBits("IQ2_XXS"))
        assertEquals(8, quantBits("Q8_0"))
        assertEquals(16, quantBits("BF16"))
        assertEquals(32, quantBits("F32"))
        assertEquals(4, quantBits("MXFP4"))
        assertNull(quantBits("unknown"))
    }

    @Test
    fun contextInThousands() {
        assertEquals("32k", contextLabel(32_768))
        assertEquals("128k", contextLabel(131_072))
        assertEquals("512", contextLabel(512))
    }
}
