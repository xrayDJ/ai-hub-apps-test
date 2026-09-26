package app.sunflower.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * What this phone can offer the engine: which chip it has, whether the
 * Hexagon NPU backend is built for it, and how much memory there is.
 * Drives the order Auto tries backends in and the size hint on the Models screen.
 */
data class DeviceProfile(
    /** Marketing name when known ("Snapdragon 8 Gen 3"), else the raw SoC model, or null. */
    val chip: String?,
    val qualcomm: Boolean,
    /** The NPU backend ships a build for this chip's Hexagon generation. */
    val npu: Boolean,
    val totalRamBytes: Long,
) {
    /** The accelerator Auto leans on; CPU when neither NPU nor Adreno GPU is there. */
    val bestBackend: Backend
        get() =
            when {
                npu -> Backend.NPU
                qualcomm -> Backend.GPU
                else -> Backend.CPU
            }

    /**
     * Backends Auto tries, fastest first. The GPU backend targets Adreno, so on
     * other chips it's left to the user to pick by hand; the same goes for the
     * NPU on Snapdragons it wasn't built for.
     */
    val autoOrder: List<Backend>
        get() =
            when {
                npu -> listOf(Backend.NPU, Backend.GPU, Backend.CPU)
                qualcomm -> listOf(Backend.GPU, Backend.CPU)
                else -> listOf(Backend.CPU)
            }

    /**
     * Largest model file that should run comfortably. Accelerators are limited by
     * memory (weights plus context, beside Android and other apps); on the CPU
     * speed runs out first, so bigger files load but reply slowly.
     */
    val comfortableModelBytes: Long
        get() {
            val byMemory = (totalRamBytes * COMFORTABLE_MEMORY_SHARE).toLong()
            return if (bestBackend == Backend.CPU) minOf(byMemory, CPU_COMFORTABLE_BYTES) else byMemory
        }

    /** Rough parameter count that fits [comfortableModelBytes] at 4-bit, rounded to a common size. */
    val comfortableParams: Int
        get() {
            val billions = comfortableModelBytes / BYTES_PER_BILLION_Q4
            return COMMON_SIZES.lastOrNull { it <= billions } ?: 1
        }

    companion object {
        fun detect(context: Context): DeviceProfile {
            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
            return classify(Build.SOC_MANUFACTURER, Build.SOC_MODEL, memory.totalMem)
        }

        /** Pure part of [detect], kept separate so it can be tested off-device. */
        fun classify(
            socManufacturer: String?,
            socModel: String?,
            totalRamBytes: Long,
        ): DeviceProfile {
            val model = socModel?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() && it != "UNKNOWN" }
            val qualcomm =
                socManufacturer?.trim()?.uppercase(Locale.US) in QUALCOMM_VENDORS ||
                    model?.let { QUALCOMM_MODEL.matches(it) } == true
            val known = model?.let { m -> NPU_CHIPS.entries.firstOrNull { m.startsWith(it.key) }?.value }
            return DeviceProfile(
                chip = known ?: model,
                qualcomm = qualcomm,
                npu = qualcomm && known != null,
                totalRamBytes = totalRamBytes,
            )
        }

        /**
         * Snapdragons whose Hexagon NPU generation (HTP v73–v81) the bundled
         * backend is built for. Keyed by SoC model prefix; OEM variants add suffixes.
         */
        private val NPU_CHIPS =
            mapOf(
                "SM8550" to "Snapdragon 8 Gen 2",
                "SM8650" to "Snapdragon 8 Gen 3",
                "SM8750" to "Snapdragon 8 Elite",
                "SM8850" to "Snapdragon 8 Elite Gen 5",
            )

        private val QUALCOMM_VENDORS = setOf("QTI", "QUALCOMM")
        private val QUALCOMM_MODEL = Regex("^(SM|SDM|QCM|QCS)\\d{3,4}.*")

        private const val COMFORTABLE_MEMORY_SHARE = 0.4
        private const val CPU_COMFORTABLE_BYTES = 2_700_000_000L
        private const val BYTES_PER_BILLION_Q4 = 600_000_000L
        private val COMMON_SIZES = listOf(1, 2, 3, 4, 8, 12, 14, 20, 27, 32)
    }
}
