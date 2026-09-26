package app.sunflower.ui.models

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
import app.sunflower.engine.Backend
import app.sunflower.engine.CrashReport
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
) {
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
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SunflowerMark(size = 72.dp, spinning = state.importing)
                    Text(
                        if (state.importing) "Reading model…" else "No models yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        engine = state.engine,
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
    onDismissCrash: () -> Unit,
    onRetryCrashedBackends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val mine = engine.modelId == model.id
    val loaded = mine && engine is InferenceEngine.State.Ready
    val busy = engine is InferenceEngine.State.Loading
    val border by animateColorAsState(if (loaded) colors.primary else colors.outlineVariant, Motion.enter(), label = "cardBorder")
    val backend = ModelSettings.fromJson(model.settings).load.backend
    val source = remember { MutableInteractionSource() }

    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainer)
            .border(1.dp, border, MaterialTheme.shapes.large)
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
        Text(
            describe(model, context),
            style = CodeStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = colors.onSurfaceVariant,
        )

        if (crashReport != null) CrashNotice(crashReport, onDismissCrash)

        when {
            model.architecture == EAGLE3_ARCH ->
                Text(
                    "EAGLE3 head: not a chat model. Choose it under Speculative decoding in the settings of the model it was made for.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            copyProgress != null -> {
                SunProgress(copyProgress)
                Text("Copying ${(copyProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            mine && engine is InferenceEngine.State.Loading ->
                StatusRow(spinning = true, text = "Loading on ${engine.backend.label}…")
            loaded && engine is InferenceEngine.State.Ready ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusRow(spinning = false, text = "Running on ${engine.backend.label}", modifier = Modifier.weight(1f))
                    SunButton("Unload", onUnload, style = SunButtonStyle.Ghost)
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

private fun describe(
    model: ModelEntity,
    context: android.content.Context,
): String =
    listOfNotNull(
        model.architecture,
        model.sizeLabel,
        model.quantization,
        model.sizeBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
        model.contextLength?.let { "${it / 1024}k ctx" },
        "MTP".takeIf { (model.nextnLayers ?: 0) > 0 },
    ).joinToString("  ·  ")

private fun backendSummary(
    backend: String,
    model: ModelEntity,
): String =
    Backend.fromUnit(backend)?.label
        ?: Backend.fromUnit(model.lastBackend)?.let { "Auto · last on ${it.label}" }
        ?: "Auto"
