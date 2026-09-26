package app.sunflower

import app.sunflower.engine.*
import java.io.File
import kotlin.test.*

class Phase4Test {
    private val info = GgufReader.read(GgufFixture.qwen().inputStream())

    @Test fun readsAttentionShape() {
        assertEquals(2048, info.embeddingLength); assertEquals(16, info.headCount); assertEquals(8, info.headCountKv); assertEquals(128, info.keyLength)
    }
    // 8 kv heads * (128 + 128) * 2 bytes * 28 layers * 4096 tokens = 469,762,048 bytes (~448 MiB)
    @Test fun kvEstimate() = assertEquals(469_762_048L, info.kvCacheBytes(4096))
    @Test fun recommendedSampling() {
        val r = assertNotNull(info.recommendedSampling)
        assertEquals(0.6f, r.temperature, 1e-6f); assertEquals(20, r.topK); assertEquals(0.95f, r.topP, 1e-6f)
        assertEquals(Sampling().minP, r.minP) // undeclared keys keep defaults
    }
    @Test fun settingsRoundTrip() {
        val s = ModelSettings(sampling = Sampling(temperature = 0.3f, stopSequences = listOf("###")), load = LoadOptions(contextSize = 8192, speculative = "ngram-simple"))
        assertEquals(s, ModelSettings.fromJson(s.toJson()))
    }
    @Test fun corruptJsonFallsBack() = assertEquals(ModelSettings(), ModelSettings.fromJson("{not json"))
    @Test fun unknownKeysIgnored() = assertEquals(0.5f, ModelSettings.fromJson("""{"sampling":{"temperature":0.5,"future":1}}""").sampling.temperature)
    @Test fun resolveAuto() {
        val r = LoadOptions().resolve(Backend.NPU, trainedContext = 40960, cores = 8)
        assertEquals(4096, r.contextSize); assertEquals(ALL_LAYERS, r.gpuLayers); assertEquals(6, r.threads); assertEquals(6, r.batchThreads)
    }
    @Test fun cpuNeverOffloads() = assertEquals(0, LoadOptions(gpuLayers = 20).resolve(Backend.CPU, null, 8).gpuLayers)
    @Test fun smallTrainedContextRespected() = assertEquals(2048, LoadOptions().resolve(Backend.CPU, 2048, 8).contextSize)
    @Test fun powerModeOnlyOnNpu() = assertEquals("", LoadOptions(powerMode = "burst").resolve(Backend.GPU, null, 8).powerMode)
    @Test fun diffListsChanges() {
        val a = LoadOptions().resolve(Backend.NPU, 40960, 8)
        val b = LoadOptions(contextSize = 8192, threads = 4).resolve(Backend.NPU, 40960, 8)
        assertEquals(listOf("Context 4096 → 8192", "Threads 6 → 4", "Batch threads 6 → 4"), a.changesTo(b))
        assertTrue(a.changesTo(a).isEmpty())
    }
    @Test fun presetKeepsLengthAndStops() {
        val mine = Sampling(maxTokens = 300, stopSequences = listOf("END"))
        val applied = mine.withSamplerFrom(SamplingPreset.Creative.sampling)
        assertEquals(300, applied.maxTokens); assertEquals(listOf("END"), applied.stopSequences); assertEquals(1.0f, applied.temperature)
        assertTrue(applied.sameSamplerAs(SamplingPreset.Creative.sampling))
    }
}
