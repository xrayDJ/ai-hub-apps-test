package app.sunflower.ui.models

import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import app.sunflower.ui.theme.lift
import app.sunflower.ui.theme.CardShape
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.sunflower.data.db.ModelEntity
import app.sunflower.data.displayName
import app.sunflower.data.failedSet
import app.sunflower.data.isMtpHead
import app.sunflower.data.isSpeculativeHead
import app.sunflower.engine.Backend
import app.sunflower.engine.CrashReport
import app.sunflower.engine.DeviceProfile
import app.sunflower.engine.speculativeLabel
import app.sunflower.ui.markdown.copyToClipboard
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import app.sunflower.engine.EAGLE3_ARCH
import app.sunflower.engine.LoadOptions
import app.sunflower.engine.ModelSettings
import app.sunflower.engine.InferenceEngine
import app.sunflower.ui.components.InfoHintButton
import app.sunflower.ui.components.InfoHintText
import app.sunflower.ui.components.ScreenHeader
import app.sunflower.ui.components.SunButton
import app.sunflower.ui.components.SunButtonStyle
import app.sunflower.ui.components.SunChip
import app.sunflower.ui.components.SunIcons
import app.sunflower.ui.components.SunProgress
import app.sunflower.ui.components.SunflowerMark
import app.sunflower.ui.theme.CodeStyle
import app.sunflower.ui.theme.Motion

@Composable
fun ModelsScreen(
    state: ModelsState,
    onBack: () -> Unit,
    onImport: (Uri) -> Unit,
    onLoad: (ModelEntity) -> Unit,
    onSetBackend: (ModelEntity, String) -> Unit,
    onOpenSettings: (ModelEntity) -> Unit,
    onUnload: () -> Unit,
    onRemove: (ModelEntity) -> Unit,
    onCopyIntoApp: (ModelEntity) -> Unit,
    onRequestFileAccess: (ModelEntity) -> Unit,
    onResume: () -> Unit,
    onDismissCrash: (ModelEntity) -> Unit,
    onRetryCrashedBackends: (ModelEntity) -> Unit,
    onLoadAnyway: (ModelEntity) -> Unit,
    onRelink: (ModelEntity, Uri) -> Unit,
    onCancelCopy: (ModelEntity) -> Unit,
) {
    var relinking by rememberSaveable { mutableStateOf<String?>(null) }
    val relinkPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val target = state.models.firstOrNull { it.id == relinking }
            if (uri != null && target != null) onRelink(target, uri)
            relinking = null
        }
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        onResume()
        onPauseOrDispose {}
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) onImport(uri)
        }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenHeader("Models", onBack)

        if (state.models.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.device?.let { DeviceSummary(it) }
                Spacer(Modifier.height(56.dp))
                SunflowerMark(size = 72.dp, spinning = state.importing)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (state.importing) "Reading model…" else "No models yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.device?.let { device -> item(key = "device") { DeviceSummary(device) } }
                items(state.models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        engine = state.engine,
                        device = state.device,
                        copyProgress = state.copying[model.id],
                        expanded = expandedId == model.id,
                        onToggle = { expandedId = if (expandedId == model.id) null else model.id },
                        onLoad = { onLoad(model) },
                        onSetBackend = { onSetBackend(model, it) },
                        onOpenSettings = { onOpenSettings(model) },
                        onUnload = onUnload,
                        onRemove = { onRemove(model) },
                        onCopyIntoApp = { onCopyIntoApp(model) },
                        onAllowFileAccess = {
                            onRequestFileAccess(model)
                            openAllFilesAccessSettings(context)
                        },
                        crashReport = state.crashReports[model.id],
                        onLoadAnyway = { onLoadAnyway(model) },
                        onLocateFile = {
                            relinking = model.id
                            relinkPicker.launch(arrayOf("*/*"))
                        },
                        onCancelCopy = { onCancelCopy(model) },
                        onDismissCrash = { onDismissCrash(model) },
                        onRetryCrashedBackends = { onRetryCrashedBackends(model) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.importError != null,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            Text(
                state.importError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        SunButton(
            text = if (state.importing) "Reading…" else "Import GGUF",
            icon = SunIcons.Import,
            enabled = !state.importing,
            // GGUF has no registered MIME type, so the picker can't filter for it.
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelEntity,
    engine: InferenceEngine.State,
    device: DeviceProfile?,
    copyProgress: Float?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLoad: () -> Unit,
    onSetBackend: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onUnload: () -> Unit,
    onRemove: () -> Unit,
    onCopyIntoApp: () -> Unit,
    onAllowFileAccess: () -> Unit,
    crashReport: CrashReport?,
    onLoadAnyway: () -> Unit,
    onLocateFile: () -> Unit,
    onCancelCopy: () -> Unit,
    onDismissCrash: () -> Unit,
    onRetryCrashedBackends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val mine = engine.modelId == model.id
    val loaded = mine && engine is InferenceEngine.State.Ready
    val busy = engine is InferenceEngine.State.Loading
    val ring by animateColorAsState(if (loaded) colors.primary.copy(alpha = 0.75f) else Color.Transparent, Motion.enter(), label = "cardRing")
    val backend = ModelSettings.fromJson(model.settings).load.backend
    val source = remember { MutableInteractionSource() }

    Column(
        modifier
            .fillMaxWidth()
            .lift(CardShape, ring = ring)
            .clickable(source, null, onClick = onToggle)
            .animateContentSize(Motion.snappy())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            model.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        ModelSpecs(model, device)

        if (crashReport != null) CrashNotice(crashReport, onDismissCrash)

        when {
            model.isSpeculativeHead ->
                Text(
                    if (model.isMtpHead) {
                        "MTP head: not a chat model. In the settings of the model it was made for, choose Speculative decoding → MTP and pick this file."
                    } else {
                        "EAGLE3 head: not a chat model. In the settings of the model it was made for, choose Speculative decoding → EAGLE3 head and pick this file."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            copyProgress != null -> {
                SunProgress(copyProgress)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Copying ${(copyProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SunButton("Cancel", onCancelCopy, style = SunButtonStyle.Ghost)
                }
            }
            mine && engine is InferenceEngine.State.Loading ->
                StatusRow(spinning = true, text = "Loading on ${engine.backend.label}…")
            loaded && engine is InferenceEngine.State.Ready ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusRow(spinning = false, text = "Running on ${engine.backend.label}", modifier = Modifier.weight(1f))
                    SunButton("Unload", onUnload, style = SunButtonStyle.Ghost)
                }
            mine && engine is InferenceEngine.State.Failed && engine.fileMissing -> {
                Text(engine.message, style = MaterialTheme.typography.bodySmall, color = colors.error)
                SunButton("Locate file", onLocateFile, style = SunButtonStyle.Tonal)
            }
            mine && engine is InferenceEngine.State.Failed && engine.tooBig -> {
                Text(engine.message, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SunButton("Load anyway", onLoadAnyway, style = SunButtonStyle.Ghost, enabled = !busy)
                    SunButton("Settings", onOpenSettings, style = SunButtonStyle.Tonal)
                }
            }
            mine && engine is InferenceEngine.State.Failed -> {
                Text(engine.message, style = MaterialTheme.typography.bodySmall, color = colors.error, maxLines = 4, overflow = TextOverflow.Ellipsis)
                SunButton("Try again", onLoad, style = SunButtonStyle.Tonal, enabled = !busy)
                if (engine.canCopyIntoApp) {
                    Column {
                        if (engine.canGrantFileAccess) SunButton("Allow file access", onAllowFileAccess, style = SunButtonStyle.Ghost)
                        SunButton("Copy into app", onCopyIntoApp, style = SunButtonStyle.Ghost)
                    }
                    FileAccessHint(model, engine.canGrantFileAccess)
                }
            }
            else ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        backendSummary(backend, model),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SunButton("Load", onLoad, enabled = !busy)
                }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SunChip("Auto", backend == LoadOptions.AUTO, { onSetBackend(LoadOptions.AUTO) })
                    Backend.entries.forEach { b ->
                        SunChip(b.label, backend == b.computeUnit, { onSetBackend(b.computeUnit) })
                    }
                }
                backendCaveat(backend, device)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                val crashed = model.failedSet().mapNotNull { Backend.fromUnit(it)?.label }
                if (crashed.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${crashed.joinToString(" and ")} stopped the app before, so Auto skips ${if (crashed.size == 1) "it" else "them"}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        SunButton("Try again", onRetryCrashedBackends, style = SunButtonStyle.Ghost)
                    }
                }
                if (model.localPath != null) {
                    Text("Stored inside Sunflower", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SunButton("Settings", onOpenSettings, icon = SunIcons.Tune, style = SunButtonStyle.Tonal)
                    SunButton("Remove", onRemove, style = SunButtonStyle.Ghost, enabled = !(mine && busy))
                }
            }
        }
    }
}

/** What the last crash was, what Sunflower changed, and the engine's log on request. */
@Composable
private fun CrashNotice(
    report: CrashReport,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showLog by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Text(
            buildString {
                append("The app stopped while ${report.stage} on ${report.backend}")
                if (report.speculative != "none") append(" with ${speculativeLabel(report.speculative)}")
                append(". ")
                report.action?.let { append(it) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            modifier = Modifier.padding(end = 10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SunButton(if (showLog) "Hide details" else "Details", { showLog = !showLog }, style = SunButtonStyle.Ghost)
            SunButton("Copy", { copyToClipboard(context, report.log) }, style = SunButtonStyle.Ghost)
            SunButton("Dismiss", onDismiss, style = SunButtonStyle.Ghost)
        }
        AnimatedVisibility(showLog, enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()), exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit())) {
            Text(
                report.log,
                style = CodeStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                color = colors.onSurfaceVariant,
                softWrap = false,
                modifier =
                    Modifier
                        .padding(end = 10.dp, bottom = 10.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/** Explains the two ways around in-place loading failures, only when asked. */
@Composable
private fun FileAccessHint(
    model: ModelEntity,
    offerAccess: Boolean,
) {
    val context = LocalContext.current
    var explain by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Which should I pick?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InfoHintButton(explain, { explain = !explain })
    }
    val size = Formatter.formatShortFileSize(context, model.sizeBytes.coerceAtLeast(0))
    InfoHintText(
        explain,
        buildString {
            append("Your phone didn't let the engine read the file where it's stored. ")
            if (offerAccess) {
                append("File access lets Sunflower load models in place, with no copy, but it can see all your shared files. ")
            }
            append("A copy keeps Sunflower to its own storage and uses $size more space; you can then delete the original.")
        },
    )
}

private fun openAllFilesAccessSettings(context: Context) {
    val perApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(perApp)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

@Composable
private fun StatusRow(
    spinning: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (spinning) {
            SunflowerMark(size = 18.dp, spinning = true, bloom = false)
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun backendSummary(
    backend: String,
    model: ModelEntity,
): String =
    Backend.fromUnit(backend)?.label
        ?: Backend.fromUnit(model.lastBackend)?.let { "Auto · last on ${it.label}" }
        ?: "Auto"

/**
 * What this phone offers, in one plain sentence with the figures that matter
 * picked out: where models run, how much memory there is, and the model size
 * that fits. The reasoning behind it is one tap away.
 */
@Composable
private fun DeviceSummary(device: DeviceProfile) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var explain by rememberSaveable { mutableStateOf(false) }
    val strong = SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Medium)
    val summary =
        buildAnnotatedString {
            append("Runs models on the ")
            withStyle(strong) { append(device.bestBackend.label) }
            ramLabel(device)?.let {
                append(", with ")
                withStyle(strong) { append(it) }
                append(" of memory")
            }
            append(". Models up to ")
            withStyle(strong) { append(Formatter.formatShortFileSize(context, device.comfortableModelBytes)) }
            append(" (about ${device.comfortableParams}B parameters at 4-bit) ")
            append(if (device.bestBackend == Backend.CPU) "reply at a comfortable pace." else "fit comfortably.")
        }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 14.dp)) {
        Text("This phone", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                device.chip ?: "Unrecognised chip",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            InfoHintButton(explain, { explain = !explain })
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        AnimatedVisibility(
            visible = explain,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            Text(
                deviceReasoning(device),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * A model's figures as one readable line: the values stand out, the words
 * around them say what they are, and a closing note says how it fits this phone.
 */
@Composable
private fun ModelSpecs(
    model: ModelEntity,
    device: DeviceProfile?,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val strong = SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Medium)
    val line =
        buildAnnotatedString {
            val parts = mutableListOf<AnnotatedString.Builder.() -> Unit>()
            model.sizeLabel?.let { size -> parts += { withStyle(strong) { append(size) }; append(" parameters") } }
            model.quantization?.let { quant ->
                val bits = quantBits(quant)
                parts += {
                    if (bits != null) {
                        withStyle(strong) { append("$bits-bit") }
                        append(" ($quant)")
                    } else {
                        withStyle(strong) { append(quant) }
                    }
                }
            }
            if (model.sizeBytes > 0) parts += { withStyle(strong) { append(Formatter.formatShortFileSize(context, model.sizeBytes)) } }
            model.contextLength?.let { tokens -> parts += { withStyle(strong) { append(contextLabel(tokens)) }; append(" context") } }
            when {
                model.isMtpHead -> parts += { append("MTP head") }
                model.architecture == EAGLE3_ARCH -> parts += { append("EAGLE3 head") }
                (model.nextnLayers ?: 0) > 0 -> parts += { append("built-in MTP") }
            }
            if (device != null && model.sizeBytes > 0 && !model.isSpeculativeHead) {
                val (text, color) =
                    when {
                        model.sizeBytes <= device.comfortableModelBytes -> "fits well" to colors.tertiary
                        model.sizeBytes <= device.totalRamBytes * 0.6 -> "tight fit" to colors.primary
                        else -> "very large for this phone" to colors.error
                    }
                parts += { withStyle(SpanStyle(color = color)) { append(text) } }
            }
            parts.forEachIndexed { index, part ->
                if (index > 0) append("  ·  ")
                part()
            }
        }
    Text(line, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
}

/** Bits per weight from a quantization name: Q4_K_M, IQ2_XXS, Q8_0, BF16, MXFP4. */
internal fun quantBits(quant: String): Int? {
    val q = quant.uppercase()
    Regex("^[A-Z]*?Q(\\d)").find(q)?.let { return it.groupValues[1].toInt() }
    Regex("^B?F(16|32)$").find(q)?.let { return it.groupValues[1].toInt() }
    Regex("FP(\\d+)").find(q)?.let { return it.groupValues[1].toInt() }
    return null
}

/** Context length the way people say it: 32k, 128k. */
internal fun contextLabel(tokens: Int): String = if (tokens >= 1024) "${tokens / 1024}k" else tokens.toString()

private fun ramLabel(device: DeviceProfile): String? =
    device.totalRamBytes.takeIf { it > 0 }?.let { "${Math.ceil(it / 1_073_741_824.0).toInt()} GB" }

private fun deviceReasoning(device: DeviceProfile): String {
    val where =
        when (device.bestBackend) {
            Backend.NPU -> "Models run on this phone's NPU, the fastest and most power-efficient option, with the GPU and CPU as fallbacks."
            Backend.GPU ->
                "The NPU backend isn't built for this chip, so models run on its Adreno GPU, with the CPU as a fallback. " +
                    "You can still try the NPU from a model's options."
            Backend.CPU ->
                "This phone doesn't have a Snapdragon chip, so models run on the CPU. It works everywhere but is slower, " +
                    "so smaller models give the best experience. You can still try the GPU from a model's options."
        }
    val size =
        if (device.bestBackend == Backend.CPU) {
            "On the CPU, speed runs out before memory: bigger models load but write slowly."
        } else {
            "The comfortable size leaves room for the conversation and for Android and your other apps; bigger models can load but may be stopped by Android."
        }
    return "$where $size"
}

/** A note when the chosen backend isn't one this chip is known to run. */
private fun backendCaveat(
    backend: String,
    device: DeviceProfile?,
): String? {
    if (device == null) return null
    val chosen = Backend.fromUnit(backend)
    return when {
        chosen == Backend.NPU && !device.npu ->
            "The NPU backend isn't built for this chip, so loading may fail. Auto uses the ${device.bestBackend.label} instead."
        chosen == Backend.GPU && !device.qualcomm ->
            "The GPU backend is made for Snapdragon's Adreno GPUs; on this phone it may be slow or fail to load."
        else -> null
    }
}
