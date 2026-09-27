package app.sunflower.ui.home

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import app.sunflower.data.SearchHit
import app.sunflower.ui.components.RenameField
import androidx.compose.animation.core.animateFloatAsState
import app.sunflower.ui.components.rememberHaptics
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sunflower.data.db.ConversationEntity
import app.sunflower.data.displayName
import app.sunflower.engine.GenieXRuntime
import app.sunflower.engine.InferenceEngine
import app.sunflower.ui.components.SunButton
import app.sunflower.ui.components.SunButtonStyle
import app.sunflower.ui.components.SunIconButton
import app.sunflower.ui.components.SunIcons
import app.sunflower.ui.components.SunflowerMark
import app.sunflower.ui.components.pressScale
import app.sunflower.ui.theme.Motion
import app.sunflower.ui.theme.SunflowerTheme

@Composable
fun HomeScreen(
    state: HomeState,
    onNewChat: () -> Unit,
    onOpenChat: (conversationId: String, messageId: String?) -> Unit,
    onOpenModels: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (id: String, title: String) -> Unit,
    onSetPinned: (id: String, pinned: Boolean) -> Unit,
    onQueryChange: (String?) -> Unit,
    onResetStorage: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // One row at a time shows its actions, rename field or delete confirmation.
    var rowMode by remember { mutableStateOf<Pair<String, RowMode>?>(null) }
    BackHandler(enabled = state.query != null || rowMode != null) {
        if (rowMode != null) rowMode = null else onQueryChange(null)
    }
    val pinned = state.conversations.filter { it.pinned }
    val recent = state.conversations.filterNot { it.pinned }
    fun LazyListScope.section(
        title: String,
        conversations: List<ConversationEntity>,
    ) {
        item(key = "section-$title") {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.padding(top = 26.dp, bottom = 4.dp).animateItem(),
            )
        }
        items(conversations, key = { it.id }) { conversation ->
            val mode = rowMode?.takeIf { it.first == conversation.id }?.second ?: RowMode.Plain
            ConversationRow(
                conversation = conversation,
                mode = mode,
                onClick = { if (rowMode != null) rowMode = null else onOpenChat(conversation.id, null) },
                onLongClick = { rowMode = conversation.id to RowMode.Actions },
                onMode = { next -> rowMode = next?.let { conversation.id to it } },
                onPin = {
                    onSetPinned(conversation.id, !conversation.pinned)
                    rowMode = null
                },
                onRename = { title ->
                    onRename(conversation.id, title)
                    rowMode = null
                },
                onDelete = {
                    rowMode = null
                    onDelete(conversation.id)
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // A warm glow bleeding in from the top corner: the one flourish on an otherwise quiet screen.
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.radialGradient(
                        listOf(SunflowerTheme.extras.glow, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(900f, -120f),
                        radius = 1100f,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Header(
                    onOpenModels = onOpenModels,
                    onOpenSettings = onOpenSettings,
                    onSearch = if (state.conversations.isNotEmpty() && state.query == null) ({ onQueryChange("") }) else null,
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            if (state.query != null) {
                item(key = "search") {
                    SearchField(state.query, onQueryChange, Modifier.padding(top = 16.dp))
                }
                if (state.searching) {
                    if (state.results.isEmpty()) {
                        item(key = "no-results") {
                            Text(
                                "No chats match",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp).animateItem(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    items(state.results, key = { "hit-" + it.conversation.id }) { hit ->
                        SearchResultRow(hit, { onOpenChat(hit.conversation.id, hit.messageId) }, Modifier.animateItem())
                    }
                } else {
                    if (pinned.isNotEmpty()) section("Pinned", pinned)
                    if (recent.isNotEmpty()) section("Recent", recent)
                }
            } else {
                item(key = "model") { ModelCard(state.runtime, state.engine, onOpenModels, Modifier.padding(top = 24.dp)) }
                if (state.storageError != null) {
                    item(key = "storage") { StorageRecovery(state.storageError, onResetStorage) }
                } else {
                    if (pinned.isNotEmpty()) section("Pinned", pinned)
                    if (recent.isNotEmpty()) section("Recent", recent)
                }
            }
        }

        SunButton(
            text = "New chat",
            icon = Icons.Filled.Add,
            onClick = onNewChat,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun Header(
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SunflowerMark(size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Text("sunflower", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        if (onSearch != null) {
            SunIconButton(Icons.Outlined.Search, "Search chats", onSearch)
            Spacer(Modifier.width(8.dp))
        }
        SunIconButton(SunIcons.Layers, "Models", onOpenModels)
        Spacer(Modifier.width(8.dp))
        SunIconButton(Icons.Outlined.Settings, "Settings", onOpenSettings)
    }
}

@Composable
private fun ModelCard(
    runtime: GenieXRuntime.State,
    engine: InferenceEngine.State,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val status: String
    var detail: String? = null
    var ok = false
    var busy = false
    when {
        runtime is GenieXRuntime.State.Failed -> {
            status = "Runtime unavailable"
            detail = runtime.reason
        }
        engine is InferenceEngine.State.Ready -> {
            status = engine.model.displayName
            detail = "Running on ${engine.backend.label}"
            ok = true
        }
        engine is InferenceEngine.State.Loading -> {
            status = engine.model.displayName
            detail = "Loading on ${engine.backend.label}…"
            busy = true
        }
        engine is InferenceEngine.State.Failed -> {
            status = "Couldn't load ${engine.model.displayName}"
        }
        runtime == GenieXRuntime.State.Starting -> {
            status = "Starting"
            busy = true
        }
        else -> status = "No model loaded"
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraLarge)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) SunflowerMark(size = 16.dp, spinning = true, bloom = false) else StatusDot(ok = ok, busy = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(status, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        SunButton("Models", onOpenModels, style = SunButtonStyle.Ghost)
    }
}

@Composable
private fun StatusDot(
    ok: Boolean,
    busy: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val alpha by animateFloatAsState(if (busy) 0.4f else 1f, Motion.snappy(), label = "dot")
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(if (ok) colors.tertiary else colors.primary),
    )
}

enum class RowMode { Plain, Actions, Renaming, ConfirmDelete }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    mode: RowMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMode: (RowMode?) -> Unit,
    onPin: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .combinedClickable(
                interactionSource = source,
                indication = ripple(),
                enabled = mode != RowMode.Renaming,
                onClick = onClick,
                onLongClick = {
                    haptics.confirm()
                    onLongClick()
                },
            ).padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
            label = "rowMode",
            modifier = Modifier.weight(1f),
        ) { current ->
            when (current) {
                RowMode.Plain ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                conversation.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                conversation.modelName ?: "No model yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            relativeTime(conversation.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                    }
                RowMode.Actions ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        SunButton(if (conversation.pinned) "Unpin" else "Pin", onPin, style = SunButtonStyle.Ghost)
                        SunButton("Rename", { onMode(RowMode.Renaming) }, style = SunButtonStyle.Ghost)
                        SunButton("Delete", { onMode(RowMode.ConfirmDelete) }, style = SunButtonStyle.Ghost)
                        Spacer(Modifier.weight(1f))
                        SunIconButton(Icons.Outlined.Close, "Close", { onMode(null) }, size = 40.dp, container = Color.Transparent)
                    }
                RowMode.Renaming -> RenameField(conversation.title, onRename, onCancel = { onMode(null) })
                RowMode.ConfirmDelete ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Delete this chat?",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        SunButton("Cancel", { onMode(null) }, style = SunButtonStyle.Ghost)
                        SunButton("Delete", onDelete, style = SunButtonStyle.Tonal)
                    }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Search chats", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = { onQueryChange(it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        SunIconButton(Icons.Outlined.Close, "Close search", { onQueryChange(null) }, container = Color.Transparent)
    }
}

@Composable
private fun SearchResultRow(
    hit: SearchHit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .clickable(interactionSource = source, indication = ripple(), onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hit.conversation.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(relativeTime(hit.conversation.updatedAt), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        hit.snippet?.let { snippet ->
            val highlighted =
                remember(snippet, colors.primary) {
                    buildAnnotatedString {
                        append(snippet.text)
                        addStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold), snippet.matchStart, snippet.matchEnd)
                    }
                }
            Text(
                highlighted,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** The encrypted database couldn't be opened: explain, and offer a clean start. */
@Composable
private fun StorageRecovery(
    error: String,
    onReset: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirming by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, colors.error.copy(alpha = 0.5f), MaterialTheme.shapes.large)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Saved chats can't be opened", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(
            "The encrypted storage or its key is damaged ($error). Resetting erases saved chats, prompts and model settings " +
                "and starts fresh. Model files on your phone are not touched.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        if (confirming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Erase and restart?", style = MaterialTheme.typography.labelLarge, color = colors.error, modifier = Modifier.weight(1f))
                SunButton("Cancel", { confirming = false }, style = SunButtonStyle.Ghost)
                SunButton("Reset", onReset, style = SunButtonStyle.Tonal)
            }
        } else {
            SunButton("Reset storage", { confirming = true }, style = SunButtonStyle.Tonal)
        }
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SunIcons.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
