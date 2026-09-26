package app.sunflower

import app.sunflower.engine.Backend
import app.sunflower.engine.DeviceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceProfileTest {
    private val gb = 1_073_741_824L

    @Test
    fun snapdragonFlagshipsGetTheNpu() {
        val s24 = DeviceProfile.classify("QTI", "SM8650", 12 * gb)
        assertEquals("Snapdragon 8 Gen 3", s24.chip)
        assertTrue(s24.npu)
        assertEquals(listOf(Backend.NPU, Backend.GPU, Backend.CPU), s24.autoOrder)
        assertTrue(DeviceProfile.classify("QTI", "SM8750-AC", 12 * gb).npu)
        assertTrue(DeviceProfile.classify("QTI", "SM8550", 8 * gb).npu)
        assertTrue(DeviceProfile.classify("QTI", "SM8850", 16 * gb).npu)
    }

    @Test
    fun otherSnapdragonsUseTheGpu() {
        val gen1 = DeviceProfile.classify("QTI", "SM8450", 8 * gb)
        assertFalse(gen1.npu)
        assertTrue(gen1.qualcomm)
        assertEquals("SM8450", gen1.chip)
        assertEquals(listOf(Backend.GPU, Backend.CPU), gen1.autoOrder)
    }

    @Test
    fun otherChipsUseTheCpu() {
        val pixel = DeviceProfile.classify("Google", "Tensor G4", 16 * gb)
        assertFalse(pixel.qualcomm)
        assertEquals(listOf(Backend.CPU), pixel.autoOrder)
        assertEquals(4, pixel.comfortableParams)
        val unknown = DeviceProfile.classify("unknown", "unknown", 8 * gb)
        assertNull(unknown.chip)
        assertEquals(Backend.CPU, unknown.bestBackend)
    }

    @Test
    fun sizeHintScalesWithMemory() {
        assertEquals(4, DeviceProfile.classify("QTI", "SM8550", 8 * gb).comfortableParams)
        assertEquals(8, DeviceProfile.classify("QTI", "SM8650", 12 * gb).comfortableParams)
        assertEquals(14, DeviceProfile.classify("QTI", "SM8750", 24 * gb).comfortableParams)
    }
}
