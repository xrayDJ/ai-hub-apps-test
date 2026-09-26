package app.sunflower.ui.settings

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.sunflower.data.displayName
import app.sunflower.data.failedSet
import app.sunflower.engine.ALL_LAYERS
import app.sunflower.engine.EAGLE3_ARCH
import app.sunflower.engine.Backend
import app.sunflower.engine.ChatOptions
import app.sunflower.engine.InferenceEngine
import app.sunflower.engine.LoadOptions
import app.sunflower.engine.ModelSettings
import app.sunflower.engine.POWER_MODES
import app.sunflower.engine.SPECULATIVE_TYPES
import app.sunflower.engine.Sampling
import app.sunflower.engine.SamplingPreset
import app.sunflower.engine.defaultThreads
import app.sunflower.engine.estimateTokens
import app.sunflower.engine.powerModeLabel
import app.sunflower.engine.sameSamplerAs
import app.sunflower.engine.speculativeLabel
import app.sunflower.engine.speculativeUsesFile
import app.sunflower.data.isMtpHead
import app.sunflower.data.isSpeculativeHead
import app.sunflower.engine.withSamplerFrom
import app.sunflower.ui.components.ScreenHeader
import app.sunflower.ui.components.SunButton
import app.sunflower.ui.components.SunButtonStyle
import app.sunflower.ui.components.SunChip
import app.sunflower.ui.components.SunflowerMark
import app.sunflower.ui.theme.CodeStyle
import app.sunflower.ui.theme.Motion
import java.util.Locale

/** Max guesses applied when switching to MTP. */
private const val MTP_DEFAULT_MAX = 3

private enum class Tab(val label: String) { Replies("Replies"), Conversation("Conversation"), Loading("Loading") }

@Composable
fun ModelSettingsScreen(
    state: ModelSettingsState,
    systemPrompt: String?,
    onUpdate: ((ModelSettings) -> ModelSettings) -> Unit,
    onReload: () -> Unit,
    onRefreshMemory: () -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        onRefreshMemory()
        onPauseOrDispose {}
    }
    val model = state.model

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        ScreenHeader(
            title = "Model settings",
            onBack = onBack,
            subtitle = {
                Text(
                    model?.displayName.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            },
        )
        if (model == null) return@Column

        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tab.entries.forEachIndexed { index, t -> SunChip(t.label, tab == index, { tab = index }) }
        }

        AnimatedContent(
            targetState = Tab.entries[tab],
            transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
            label = "settingsTab",
            modifier = Modifier.weight(1f),
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                when (current) {
                    Tab.Replies -> RepliesTab(state, onUpdate)
                    Tab.Conversation -> ConversationTab(state, systemPrompt, onUpdate)
                    Tab.Loading -> LoadingTab(state, onUpdate, onReload)
                }
            }
        }

        PendingBar(state, onReload)
    }
}

// ---------------------------------------------------------------- Replies

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepliesTab(
    state: ModelSettingsState,
    onUpdate: ((ModelSettings) -> ModelSettings) -> Unit,
) {
    val s = state.settings.sampling
    val d = Sampling()
    fun set(transform: Sampling.() -> Sampling) = onUpdate { it.copy(sampling = it.sampling.transform()) }
    val recommended = state.info?.recommendedSampling

    Text(
        summary(s),
        style = CodeStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
    )

    SettingSection("Presets") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            if (recommended != null) {
                SunChip("Model's own", s.sameSamplerAs(recommended), { set { withSamplerFrom(recommended) } })
            }
            SamplingPreset.entries.forEach { preset ->
                SunChip(preset.label, s.sameSamplerAs(preset.sampling), { set { withSamplerFrom(preset.sampling) } })
            }
        }
        Text(
            if (recommended != null) {
                "“Model's own” uses the values this GGUF recommends. Presets change the sampler only, not length, seed or stops."
            } else {
                "Presets change the sampler only, not length, seed or stops."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }

    SettingSection("Word choice") {
        FloatSetting(
            "Temperature",
            "How adventurous word choice is. Low values stick to the most likely words: focused, repeatable answers. " +
                "High values take more chances: more varied and creative, but more mistakes. 0 always picks the single most likely word.",
            s.temperature, d.temperature, 0f..2f, 0.05f, { v -> set { copy(temperature = v) } },
        )
        FloatSetting(
            "Top P",
            "Only considers the most likely words until their probabilities add up to this share. " +
                "0.95 drops the unlikely 5% tail. 1 turns this filter off.",
            s.topP, d.topP, 0.05f..1f, 0.01f, { v -> set { copy(topP = v) } },
        )
        StepSetting(
            "Top K",
            "Only considers this many of the most likely next words. Lower is safer and plainer. Off removes the limit.",
            s.topK, d.topK, listOf(0, 1, 5, 10, 20, 30, 40, 50, 64, 80, 100, 150, 200),
            { v -> set { copy(topK = v) } },
            format = { if (it == 0) "Off" else it.toString() },
        )
        FloatSetting(
            "Min P",
            "Ignores words less likely than this fraction of the top choice: at 0.05, anything under 5% as likely as the best word is skipped. " +
                "It adapts to how confident the model is, which keeps high temperatures sensible. 0 turns it off.",
            s.minP, d.minP, 0f..0.5f, 0.01f, { v -> set { copy(minP = v) } },
        )
    }

    SettingSection("Repetition") {
        FloatSetting(
            "Repetition penalty",
            "Makes words that already appeared less likely to appear again. 1 is off. 1.05–1.15 helps small models out of loops; much higher makes wording odd.",
            s.repetitionPenalty, d.repetitionPenalty, 1f..2f, 0.01f, { v -> set { copy(repetitionPenalty = v) } },
        )
        FloatSetting(
            "Presence penalty",
            "A flat penalty on any word that has already appeared, however often. Nudges the model toward new topics. 0 is off.",
            s.presencePenalty, d.presencePenalty, 0f..2f, 0.05f, { v -> set { copy(presencePenalty = v) } },
        )
        FloatSetting(
            "Frequency penalty",
            "A penalty that grows each time a word is repeated, trimming repeated phrases. 0 is off.",
            s.frequencyPenalty, d.frequencyPenalty, 0f..2f, 0.05f, { v -> set { copy(frequencyPenalty = v) } },
        )
    }

    SettingSection("Length and ending") {
        val context = state.resolved.contextSize
        StepSetting(
            "Max reply length",
            "The most tokens one reply may use; a token is about ¾ of a word. The reply stops here even mid-sentence. " +
                "This space is reserved in the context window, so very long limits leave less room for history.",
            s.maxTokens, d.maxTokens,
            listOf(64, 128, 256, 512, 768, 1024, 1536, 2048, 3072, 4096, 6144, 8192, 16384).filter { it < context }.ifEmpty { listOf(context / 2) },
            { v -> set { copy(maxTokens = v) } },
            format = { "$it tokens" },
        )
        StopSequencesSetting(
            "Stop sequences",
            "Text that ends the reply as soon as the model writes it. The chat template already stops at the end of a turn; " +
                "add these for models that keep going, or for custom formats. Type \\n for a line break.",
            s.stopSequences,
            { v -> set { copy(stopSequences = v) } },
        )
    }

    SettingSection("Randomness") {
        ToggleSetting(
            "Random seed",
            "The seed drives the random choices. On: a fresh seed every reply, so regenerating gives something new. " +
                "Off: a fixed seed, so the same message with the same settings reproduces the same reply.",
            s.seed < 0, true, { random -> set { copy(seed = if (random) -1 else 42) } },
        )
        AnimatedVisibility(s.seed >= 0) {
            NumberEntrySetting(
                "Seed",
                "Any whole number. Tap the value to change it.",
                s.seed.coerceAtLeast(0), 42, { v -> set { copy(seed = v.coerceAtLeast(0)) } },
            )
        }
    }

    SettingSection("Output format") {
        TextAreaSetting(
            "Grammar (GBNF)",
            "Forces replies into a fixed structure, such as valid JSON or one of a few allowed answers, using llama.cpp's GBNF grammar format. " +
                "Leave empty for normal text. Replies are slower with a grammar, and a broken grammar makes replies fail.",
            s.grammar,
            placeholder = "root ::= \"yes\" | \"no\"",
            onChange = { v -> set { copy(grammar = v) } },
        )
    }
}

private fun summary(s: Sampling): String =
    buildList {
        add("temp ${"%.2f".fmt(s.temperature)}")
        add("top-p ${"%.2f".fmt(s.topP)}")
        add("top-k ${if (s.topK == 0) "off" else s.topK}")
        add("min-p ${"%.2f".fmt(s.minP)}")
        add("repeat ${"%.2f".fmt(s.repetitionPenalty)}")
        add("≤ ${s.maxTokens} tokens")
        add("seed ${if (s.seed < 0) "random" else s.seed}")
    }.joinToString(" · ")

private fun String.fmt(v: Float) = String.format(Locale.US, this, v)

// ----------------------------------------------------------- Conversation

@Composable
private fun ConversationTab(
    state: ModelSettingsState,
    systemPrompt: String?,
    onUpdate: ((ModelSettings) -> ModelSettings) -> Unit,
) {
    val c = state.settings.chat
    val d = ChatOptions()
    fun set(transform: ChatOptions.() -> ChatOptions) = onUpdate { it.copy(chat = it.chat.transform()) }
    val context = state.resolved.contextSize
    val reply = state.settings.sampling.maxTokens
    val systemTokens = systemPrompt?.let { estimateTokens(it) }

    // What actually fits into one request, in plain numbers.
    InfoCard(
        buildString {
            append("Context window: $context tokens. ")
            append("Up to $reply are kept for the reply")
            if (systemTokens != null) append(", about $systemTokens for this chat's system prompt")
            append(", and the rest, about ${(context - reply - (systemTokens ?: 0)).coerceAtLeast(0)}, for earlier messages.")
        },
    )

    SettingSection("Reasoning") {
        ToggleSetting(
            "Thinking",
            "Asks models that support it (Qwen3, DeepSeek R1 distills and others) to reason step by step before answering. " +
                "Replies take longer and can be more accurate. The reasoning appears folded above the answer. Other models ignore this.",
            c.thinking, d.thinking, { v -> set { copy(thinking = v) } },
        )
    }

    SettingSection("When the chat gets long") {
        ToggleSetting(
            "Trim old messages",
            "When the chat no longer fits the context window, leave out the oldest messages so the newest fit. The system prompt is always kept. " +
                "Off sends everything, which fails on long chats unless the sliding window below is on.",
            c.trimHistory, d.trimHistory, { v -> set { copy(trimHistory = v) } },
        )
        ToggleSetting(
            "Sliding window",
            "If the window fills up while a reply is being written, drop the oldest tokens and keep writing instead of stopping.",
            c.slidingWindow, d.slidingWindow, { v -> set { copy(slidingWindow = v) } },
        )
        StepSetting(
            "Keep at start",
            "How many tokens at the very beginning survive when the window slides. " +
                "Auto keeps the system prompt${systemTokens?.let { " (about ${it + 16} tokens here)" }.orEmpty()}.",
            c.keepTokens, d.keepTokens, listOf(0, 32, 64, 128, 256, 512, 1024, 2048).filter { it < context },
            { v -> set { copy(keepTokens = v) } },
            format = { if (it == 0) "Auto" else "$it tokens" },
            enabled = c.slidingWindow,
        )
    }
}

// ---------------------------------------------------------------- Loading

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadingTab(
    state: ModelSettingsState,
    onUpdate: ((ModelSettings) -> ModelSettings) -> Unit,
    onReload: () -> Unit,
) {
    val context = LocalContext.current
    val model = state.model ?: return
    val l = state.settings.load
    val d = LoadOptions()
    fun set(transform: LoadOptions.() -> LoadOptions) = onUpdate { it.copy(load = it.load.transform()) }
    val resolved = state.resolved
    val backend = state.targetBackend
    val trained = model.contextLength
    val layers = model.layerCount ?: state.info?.layerCount
    fun bytes(b: Long) = Formatter.formatShortFileSize(context, b)

    LoadStatusCard(state, onReload)

    SettingSection("Where it runs") {
        val crashed = model.failedSet().mapNotNull { Backend.fromUnit(it)?.label }
        ChoiceSetting(
            "Backend",
            "NPU is Snapdragon's AI engine: usually fastest and most efficient. GPU is next, CPU works everywhere but is slowest. " +
                "Auto tries NPU, then GPU, then CPU, starting with whichever worked last time.",
            options = listOf(LoadOptions.AUTO) + Backend.entries.map { it.computeUnit },
            selected = l.backend,
            default = d.backend,
            label = { if (it == LoadOptions.AUTO) "Auto" else Backend.fromUnit(it)?.label ?: it },
            onChange = { v -> set { copy(backend = v) } },
            footer = {
                if (crashed.isNotEmpty()) {
                    Text(
                        "${crashed.joinToString(" and ")} stopped the app while loading this model before, so Auto skips it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            },
        )
        if (backend != Backend.CPU) {
            val max = layers ?: 100
            StepSetting(
                "Layers on ${backend.label}",
                "How many of the model's ${layers ?: "?"} layers run on the ${backend.label}; the rest run on the CPU. " +
                    "All is fastest when it fits. Lower it if loading fails for lack of memory.",
                if (l.gpuLayers < 0) ALL_LAYERS else l.gpuLayers,
                ALL_LAYERS,
                (0..max).toList() + ALL_LAYERS,
                { v -> set { copy(gpuLayers = if (v >= ALL_LAYERS) -1 else v) } },
                format = { if (it >= ALL_LAYERS) "All" else "$it of $max" },
            )
        }
        ChoiceSetting(
            "NPU power mode",
            "How hard the NPU runs. Burst and high performance are fastest but use more battery and can heat the phone in long chats. " +
                "Power saver modes are slower and cooler. Default lets the runtime decide. Only applies on the NPU.",
            options = POWER_MODES,
            selected = l.powerMode,
            default = d.powerMode,
            label = ::powerModeLabel,
            onChange = { v -> set { copy(powerMode = v) } },
            enabled = backend == Backend.NPU,
        )
    }

    SettingSection("Memory") {
        val options = listOf(0, 512, 1024, 2048, 3072, 4096, 6144, 8192, 12288, 16384, 24576, 32768, 49152, 65536, 98304, 131072, 262144)
            .filter { it == 0 || it <= (trained ?: 32768) }
        StepSetting(
            "Context size",
            "How many tokens the model can see at once: system prompt, earlier messages and the reply being written. " +
                "Bigger remembers more of the chat, but needs more memory and makes long prompts slower. " +
                "This model was trained for up to ${trained ?: "an unknown number of"} tokens. Auto uses up to ${LoadOptions.DEFAULT_CONTEXT}.",
            l.contextSize, d.contextSize, options,
            { v -> set { copy(contextSize = v) } },
            format = { if (it == 0) "Auto (${resolved.contextSize})" else it.toString() },
        ) { preview ->
            val ctx = if (preview == 0) resolved.contextSize else preview
            if (state.memory.totalBytes > 0) {
                MemoryBar(
                    modelBytes = model.sizeBytes.coerceAtLeast(0),
                    contextBytes = state.info?.kvCacheBytes(ctx),
                    totalBytes = state.memory.totalBytes,
                    availableBytes = state.memory.availableBytes + if (state.loaded != null) model.sizeBytes.coerceAtLeast(0) else 0,
                    format = ::bytes,
                )
            }
        }
    }

    SettingSection("Processing") {
        val threadOptions = listOf(0) + (1..state.cores).toList()
        StepSetting(
            "Threads",
            "CPU threads used while writing the reply. Auto uses ${defaultThreads(state.cores)}, leaving the slowest cores free. " +
                "More isn't always faster on phones: extra threads land on efficiency cores and hold the others back.",
            l.threads, d.threads, threadOptions,
            { v -> set { copy(threads = v) } },
            format = { if (it == 0) "Auto (${defaultThreads(state.cores)})" else it.toString() },
        )
        StepSetting(
            "Prompt threads",
            "CPU threads used while reading your message and the chat history. Auto matches the reply threads.",
            l.batchThreads, d.batchThreads, threadOptions,
            { v -> set { copy(batchThreads = v) } },
            format = { if (it == 0) "Auto (${resolved.threads})" else it.toString() },
        )
        StepSetting(
            "Batch size",
            "How many prompt tokens are processed per step when reading a message. Larger is faster for long prompts but needs more memory.",
            l.batchSize, d.batchSize, listOf(64, 128, 256, 512, 1024, 2048, 4096),
            { v -> set { copy(batchSize = v, microBatch = microBatch.coerceAtMost(v)) } },
        )
        StepSetting(
            "Micro-batch size",
            "The chunk actually computed at once inside a batch. Lower it if long prompts crash for lack of memory. Can't exceed the batch size.",
            l.microBatch, d.microBatch, listOf(16, 32, 64, 128, 256, 512, 1024, 2048, 4096).filter { it <= l.batchSize },
            { v -> set { copy(microBatch = v) } },
        )
    }

    SettingSection("Chat format") {
        val hasOwn = model.hasChatTemplate
        TextAreaSetting(
            "Custom chat template",
            "The template turns the conversation into the exact text format the model was trained on. " +
                "Only replace it for models whose built-in template is missing or broken: a wrong template gives garbled or endless replies. " +
                "Uses Jinja, like Hugging Face tokenizer configs. Empty means the model's own.",
            l.chatTemplate,
            placeholder = if (hasOwn) "Using the model's built-in template" else "This model has no built-in template. Pick a format below.",
            onChange = { v -> set { copy(chatTemplate = v) } },
            minLines = 3,
            above = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    ChatTemplates.entries.forEach { t ->
                        SunChip(t.label, l.chatTemplate == t.jinja, { set { copy(chatTemplate = t.jinja) } })
                    }
                }
            },
        )
    }

    SettingSection("Speculative decoding") {
        val mtpLayers = model.nextnLayers ?: state.info?.nextnLayers ?: 0
        val hasMtp = mtpLayers > 0
        ChoiceSetting(
            "Method",
            "Guesses several tokens ahead and lets the model check them all in one step. When guesses are right, replies come faster; the text is the same either way. " +
                "MTP uses a multi-token-prediction head made for this model: either a separate head file (like Gemma 4's “assistant”) or layers built into the model itself. " +
                "Draft model uses a smaller complete model that shares this model's tokenizer (for example Qwen3 0.6B with Qwen3 8B). " +
                "EAGLE3 head uses a tiny add-on file trained for this exact model. " +
                "The n-gram methods need no extra file: they reuse patterns from the conversation and help most when replies repeat earlier text, like editing code. " +
                "Each reply shows how many guesses were accepted, so you can tell whether it helps.",
            options = SPECULATIVE_TYPES,
            selected = l.speculative,
            default = d.speculative,
            label = ::speculativeLabel,
            onChange = { v ->
                set {
                    copy(
                        speculative = v,
                        draftModelId = draftModelId.takeIf { speculativeUsesFile(v) },
                        // MTP heads guess best a few tokens ahead; keep a custom value if the user set one.
                        draftMax = if (v == "draft-mtp" && draftMax == d.draftMax) MTP_DEFAULT_MAX else draftMax,
                    )
                }
            },
            footer = {
                if (l.speculative != "none" && backend != Backend.CPU) {
                    Text(
                        "On phone ${backend.label}s, checking guesses and passing the model's state to the helper often costs more than it saves, " +
                            "so replies can be slower than without it. Each reply's stats compare its speed with plain generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (hasMtp && l.speculative == "none") {
                    Text(
                        "This model has $mtpLayers built-in MTP layer${if (mtpLayers == 1) "" else "s"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            },
        )
        AnimatedVisibility(speculativeUsesFile(l.speculative)) {
            val kind = l.speculative
            val candidates =
                when (kind) {
                    "draft-mtp" -> state.otherModels.filter { it.isMtpHead }
                    "draft-eagle3" -> state.otherModels.sortedByDescending { it.architecture == EAGLE3_ARCH }
                    else -> state.otherModels.filter { !it.isSpeculativeHead }
                }
            // For MTP, "none picked" means the model's own layers, which only exist on some models.
            val noneLabel = if (kind == "draft-mtp") "Built into this model" else "None"
            val showNone = kind != "draft-mtp" || hasMtp
            Column {
                if (candidates.isEmpty() && (kind != "draft-mtp" || !hasMtp)) {
                    Text(
                        when (kind) {
                            "draft-mtp" -> "This model has no MTP layers of its own. Import the MTP head made for it (for Gemma 4, its “assistant” GGUF)."
                            "draft-eagle3" -> "Import the EAGLE3 head made for this model first."
                            else -> "Import a smaller model from the same family first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    ChoiceSetting(
                        when (kind) {
                            "draft-mtp" -> "MTP head"
                            "draft-eagle3" -> "EAGLE3 head"
                            else -> "Draft model"
                        },
                        when (kind) {
                            "draft-mtp" -> "The head that makes the guesses. It must be made for this exact model; it runs linked to it and adds a little memory."
                            "draft-eagle3" -> "The EAGLE3 file trained for this exact model. A head made for a different model, even a fine-tune of the same one, won't help."
                            else -> "The small model that makes the guesses. It is loaded alongside this one and uses extra memory. Heads are listed under their own methods."
                        },
                        options = (if (showNone) listOf<String?>(null) else emptyList()) + candidates.map { it.id },
                        selected = l.draftModelId,
                        default = null,
                        label = { id -> id?.let { candidates.firstOrNull { m -> m.id == it }?.displayName } ?: noneLabel },
                        onChange = { v -> set { copy(draftModelId = v) } },
                    )
                }
            }
        }
        AnimatedVisibility(l.speculative != "none") {
            Column {
                StepSetting(
                    "Max guesses",
                    "The most tokens guessed ahead in one go. More can be faster when guesses are good, slower when they aren't.",
                    l.draftMax, d.draftMax, (1..32).toList(),
                    { v -> set { copy(draftMax = v, draftMin = draftMin.coerceAtMost(v)) } },
                )
                StepSetting(
                    "Min guesses",
                    "Skip guessing when fewer than this many tokens can be proposed. 0 always tries.",
                    l.draftMin, d.draftMin, (0..l.draftMax.coerceAtMost(16)).toList(),
                    { v -> set { copy(draftMin = v) } },
                )
                FloatSetting(
                    "Min confidence",
                    "The guessing stops for this step once it's less sure than this. Higher gives fewer, better guesses.",
                    l.draftMinProbability, d.draftMinProbability, 0f..1f, 0.05f,
                    { v -> set { copy(draftMinProbability = v) } },
                    enabled = l.speculative.startsWith("draft"),
                )
            }
        }
    }
}

@Composable
private fun LoadStatusCard(
    state: ModelSettingsState,
    onReload: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val loaded = state.loaded
    val mine = (state.engine as? InferenceEngine.State.Loading)?.model?.id == state.model?.id
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, if (loaded != null) colors.primary else colors.outlineVariant, MaterialTheme.shapes.large)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    mine -> "Loading…"
                    loaded != null -> "Running on ${loaded.backend.label}"
                    else -> "Not loaded"
                },
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(
                if (loaded != null) {
                    val a = loaded.applied
                    "${a.contextSize} context · ${if (a.gpuLayers >= ALL_LAYERS) "all" else a.gpuLayers} layers offloaded · ${a.threads} threads"
                } else {
                    "Changes here apply when you load it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (mine) {
            SunflowerMark(size = 24.dp, spinning = true, bloom = false)
        } else if (loaded == null) {
            SunButton("Load", onReload, style = SunButtonStyle.Tonal, enabled = !state.busy)
        }
    }
}

/** Pinned at the bottom whenever loading settings differ from what is running. */
@Composable
private fun PendingBar(
    state: ModelSettingsState,
    onReload: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val changes = state.pendingChanges
    AnimatedVisibility(
        visible = changes.isNotEmpty(),
        enter = slideInVertically(Motion.slide) { it } + fadeIn(Motion.enter()),
        exit = slideOutVertically(Motion.slide) { it } + fadeOut(Motion.exit()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainerHigh)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reload to apply", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                val shown = changes.take(3)
                shown.forEach { Text(it, style = CodeStyle, color = colors.onSurfaceVariant, maxLines = 1) }
                if (changes.size > shown.size) {
                    Text("and ${changes.size - shown.size} more", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            SunButton("Reload", onReload, enabled = !state.busy)
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Common chat formats, for models that ship without a usable template. */
private enum class ChatTemplates(
    val label: String,
    val jinja: String,
) {
    ChatML(
        "ChatML",
        "{% for message in messages %}{{ '<|im_start|>' + message['role'] + '\\n' + message['content'] + '<|im_end|>\\n' }}{% endfor %}" +
            "{% if add_generation_prompt %}{{ '<|im_start|>assistant\\n' }}{% endif %}",
    ),
    Llama3(
        "Llama 3",
        "{{ bos_token }}{% for message in messages %}{{ '<|start_header_id|>' + message['role'] + '<|end_header_id|>\\n\\n' + message['content'] | trim + '<|eot_id|>' }}{% endfor %}" +
            "{% if add_generation_prompt %}{{ '<|start_header_id|>assistant<|end_header_id|>\\n\\n' }}{% endif %}",
    ),
    Gemma(
        "Gemma",
        "{{ bos_token }}{% for message in messages %}{% set role = 'model' if message['role'] == 'assistant' else 'user' %}" +
            "{{ '<start_of_turn>' + role + '\\n' + message['content'] | trim + '<end_of_turn>\\n' }}{% endfor %}" +
            "{% if add_generation_prompt %}{{ '<start_of_turn>model\\n' }}{% endif %}",
    ),
    Mistral(
        "Mistral",
        "{{ bos_token }}{% for message in messages %}{% if message['role'] == 'assistant' %}{{ message['content'] + eos_token }}" +
            "{% else %}{{ '[INST] ' + message['content'] + ' [/INST]' }}{% endif %}{% endfor %}",
    ),
    Phi(
        "Phi",
        "{% for message in messages %}{{ '<|' + message['role'] + '|>\\n' + message['content'] + '<|end|>\\n' }}{% endfor %}" +
            "{% if add_generation_prompt %}{{ '<|assistant|>\\n' }}{% endif %}",
    ),
}
