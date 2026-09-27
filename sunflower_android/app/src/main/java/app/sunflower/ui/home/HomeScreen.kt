package app.sunflower.ui.home

import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.widthIn
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sunflower.data.SearchHit
import app.sunflower.data.db.ConversationEntity
import app.sunflower.data.displayName
import app.sunflower.engine.GenieXRuntime
import app.sunflower.engine.InferenceEngine
import app.sunflower.ui.components.RenameField
import app.sunflower.ui.components.SunButton
import app.sunflower.ui.components.SunButtonStyle
import app.sunflower.ui.components.SunIconButton
import app.sunflower.ui.components.SunIcons
import app.sunflower.ui.components.SunflowerMark
import app.sunflower.ui.components.chatBounds
import app.sunflower.ui.components.pressScale
import app.sunflower.ui.components.rememberHaptics
import app.sunflower.ui.theme.CardShape
import app.sunflower.ui.theme.Motion
import app.sunflower.ui.theme.PillShape
import app.sunflower.ui.theme.SunflowerTheme
import app.sunflower.ui.theme.lift
import app.sunflower.ui.theme.rememberShimmer

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
    val status = ModelStatus.of(state.runtime, state.engine)
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
                modifier = Modifier.padding(start = 4.dp, top = 26.dp, bottom = 4.dp).animateItem(),
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

    // The warm light in the corner follows the model: full when one is ready, dim when none is.
    val glow by animateFloatAsState(
        when (status) {
            is ModelStatus.Ready -> 1f
            is ModelStatus.Loading, ModelStatus.Starting -> 0.6f
            else -> 0.25f
        },
        tween(1400),
        label = "glow",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(460.dp)
                .graphicsLayer { alpha = glow }
                .background(
                    Brush.radialGradient(
                        listOf(SunflowerTheme.extras.glow, Color.Transparent),
                        center = Offset(900f, -120f),
                        radius = 1100f,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Header(
                    status = status,
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
            } else if (state.storageError != null) {
                item(key = "storage") { StorageRecovery(state.storageError, Modifier.padding(top = 24.dp)) { onResetStorage() } }
            } else {
                if (pinned.isNotEmpty()) section("Pinned", pinned)
                if (recent.isNotEmpty()) section("Recent", recent)
            }
        }

        NewChatButton(
            onClick = onNewChat,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
        )
    }
}

/** The main action. Its yellow surface is what grows into the new chat. */
@Composable
private fun NewChatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Box(modifier.pressScale(source)) {
        Box(
            Modifier
                .matchParentSize()
                .chatBounds("new", PillShape)
                .shadow(14.dp, PillShape, ambientColor = colors.primary, spotColor = colors.primary)
                .background(colors.primary, PillShape),
        )
        Row(
            Modifier
                .clip(PillShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button) {
                    haptics.tick()
                    onClick()
                }.padding(start = 20.dp, end = 24.dp, top = 15.dp, bottom = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            Text("New chat", style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
        }
    }
}

/** What the home screen says about the model, in words rather than a coloured dot. */
private sealed interface ModelStatus {
    data object Starting : ModelStatus

    data class RuntimeFailed(val reason: String) : ModelStatus

    data object None : ModelStatus

    data class Loading(val name: String, val backend: String) : ModelStatus

    data class Ready(val name: String, val backend: String) : ModelStatus

    data class Failed(val name: String) : ModelStatus

    companion object {
        fun of(
            runtime: GenieXRuntime.State,
            engine: InferenceEngine.State,
        ): ModelStatus =
            when {
                runtime is GenieXRuntime.State.Failed -> RuntimeFailed(runtime.reason)
                engine is InferenceEngine.State.Ready -> Ready(engine.model.displayName, engine.backend.label)
                engine is InferenceEngine.State.Loading -> Loading(engine.model.displayName, engine.backend.label)
                engine is InferenceEngine.State.Failed -> Failed(engine.model.displayName)
                runtime == GenieXRuntime.State.Starting -> Starting
                else -> None
            }
    }
}

@Composable
private fun Header(
    status: ModelStatus,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(4.dp))
            SunflowerMark(
                size = 34.dp,
                spinning = status is ModelStatus.Loading || status == ModelStatus.Starting,
                breathing = status is ModelStatus.Ready,
                dormant = status == ModelStatus.None || status is ModelStatus.Failed || status is ModelStatus.RuntimeFailed,
            )
            Spacer(Modifier.width(10.dp))
            Text("sunflower", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            if (onSearch != null) SunIconButton(Icons.Outlined.Search, "Search chats", onSearch)
            SunIconButton(SunIcons.Layers, "Models", onOpenModels)
            SunIconButton(Icons.Outlined.Settings, "Settings", onOpenSettings)
        }
        StatusLine(status, onOpenModels, Modifier.padding(start = 48.dp))
    }
}

/** One quiet line under the name: which model is ready and where, or what's happening. Tapping it opens Models. */
@Composable
private fun StatusLine(
    status: ModelStatus,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Open models", onClick = onOpenModels)
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        AnimatedContent(
            targetState = status,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(320, delayMillis = 80)) togetherWith fadeOut(tween(160)) },
            label = "status",
        ) { current ->
            when (current) {
                is ModelStatus.Ready ->
                    Row {
                        Text(
                            current.name,
                            style = style.copy(fontWeight = FontWeight.Medium),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = NAME_MAX).drawnUnderline(colors.primary),
                        )
                        Text("  ·  ${current.backend}", style = style, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                is ModelStatus.Loading -> {
                    val shimmer = style.copy(brush = rememberShimmer(colors.onSurfaceVariant, colors.onSurface))
                    Row {
                        Text(current.name, style = shimmer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = NAME_MAX))
                        Text("  ·  loading on ${current.backend}…", style = shimmer, maxLines = 1)
                    }
                }
                ModelStatus.Starting ->
                    Text("Starting…", style = style.copy(brush = rememberShimmer(colors.onSurfaceVariant, colors.onSurface)))
                is ModelStatus.Failed ->
                    Row {
                        Text(
                            "Couldn't load ${current.name}",
                            style = style,
                            color = colors.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = NAME_MAX + 60.dp),
                        )
                        Text("  ·  Details", style = style, color = colors.onSurface, maxLines = 1)
                    }
                is ModelStatus.RuntimeFailed ->
                    Column {
                        Text("Runtime unavailable", style = style, color = colors.error)
                        Text(current.reason, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                ModelStatus.None ->
                    Row {
                        Text("No model  ·  ", style = style, color = colors.onSurfaceVariant)
                        Text("Choose one", style = style.copy(fontWeight = FontWeight.Medium), color = colors.onSurface)
                    }
            }
        }
    }
}

/** A thin underline that draws itself in from the left when it first appears. */
private fun Modifier.drawnUnderline(color: Color): Modifier =
    composed {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, Motion.lively()) }
        padding(bottom = 3.dp).drawBehind {
            val y = size.height + 2.dp.toPx()
            val end = size.width * progress.value.coerceIn(0f, 1f)
            drawLine(color, Offset(0f, y), Offset(end, y), strokeWidth = 1.5.dp.toPx())
        }
    }

enum class RowMode { Plain, Actions, Renaming, ConfirmDelete }

/**
 * A chat in the list. A press sinks it slightly; a long press lifts it off the
 * page and unfolds its actions underneath. Tapping grows it into the chat.
 */
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
    val pressed by source.collectIsPressedAsState()
    val lifted = mode != RowMode.Plain
    val scale by animateFloatAsState(
        when {
            lifted -> 1.02f
            pressed -> 0.975f
            else -> 1f
        },
        Motion.lively(),
        label = "rowScale",
    )
    val raise by animateFloatAsState(if (lifted) 1f else 0f, Motion.lively(), label = "rowRaise")
    val elevation by animateDpAsState(if (lifted) 22.dp else 10.dp, Motion.lively(), label = "rowElevation")
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = -3.dp.toPx() * raise
                },
        ) {
            // Only the surface grows into the chat; the text stays behind and fades with the list.
            Box(
                Modifier
                    .matchParentSize()
                    .chatBounds(conversation.id, CardShape)
                    .lift(CardShape, elevation = elevation),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .combinedClickable(
                        interactionSource = source,
                        indication = null,
                        onClick = onClick,
                        onLongClickLabel = "Chat options",
                        onLongClick = {
                            haptics.confirm()
                            onLongClick()
                        },
                    ).heightIn(min = 68.dp)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(
                            visible = conversation.pinned,
                            enter = fadeIn(Motion.enter()) + expandHorizontallyFromStart(),
                            exit = fadeOut(Motion.exit()) + shrinkHorizontallyToStart(),
                        ) {
                            PetalGlyph(Modifier.padding(end = 7.dp))
                        }
                        Text(
                            conversation.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        conversation.modelName ?: "No model yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(relativeTime(conversation.updatedAt), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }

        AnimatedVisibility(
            visible = lifted,
            enter = expandVertically(Motion.lively()) + fadeIn(tween(220)),
            exit = shrinkVertically(Motion.soft()) + fadeOut(tween(160)),
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
                label = "rowOptions",
                modifier = Modifier.padding(top = 8.dp),
            ) { current ->
                when (current) {
                    RowMode.Renaming ->
                        RenameField(
                            conversation.title,
                            onRename,
                            onCancel = { onMode(null) },
                            modifier = Modifier.padding(start = 18.dp, end = 4.dp),
                        )
                    RowMode.ConfirmDelete ->
                        Row(Modifier.padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Delete this chat?", style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                            SunButton("Cancel", { onMode(null) }, style = SunButtonStyle.Ghost)
                            SunButton("Delete", onDelete, style = SunButtonStyle.Tonal)
                        }
                    else ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RowAction(if (conversation.pinned) "Unpin" else "Pin", onPin)
                            RowAction("Rename", { onMode(RowMode.Renaming) })
                            RowAction("Delete", { onMode(RowMode.ConfirmDelete) }, color = colors.error)
                            Spacer(Modifier.weight(1f))
                            SunIconButton(Icons.Outlined.Close, "Close", { onMode(null) }, size = 40.dp)
                        }
                }
            }
        }
    }
}

private fun expandHorizontallyFromStart() = androidx.compose.animation.expandHorizontally(Motion.lively(), expandFrom = Alignment.Start)

private fun shrinkHorizontallyToStart() = androidx.compose.animation.shrinkHorizontally(Motion.soft(), shrinkTowards = Alignment.Start)

/** A text action under a lifted row. */
@Composable
private fun RowAction(
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val pressed by source.collectIsPressedAsState()
    val touch by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f) else Color.Transparent,
        Motion.enter(),
        label = "actionTouch",
    )
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier =
            Modifier
                .pressScale(source, 0.92f)
                .clip(PillShape)
                .background(touch)
                .clickable(interactionSource = source, indication = null, role = Role.Button) {
                    haptics.tick()
                    onClick()
                }.heightIn(min = 44.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** A single petal: marks a pinned chat. */
@Composable
private fun PetalGlyph(modifier: Modifier = Modifier) {
    val petal = SunflowerTheme.extras.petalOuter
    Box(
        modifier
            .size(width = 9.dp, height = 13.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val path =
                    Path().apply {
                        moveTo(w / 2f, 0f)
                        cubicTo(w * 1.05f, h * 0.28f, w * 1.05f, h * 0.72f, w / 2f, h)
                        cubicTo(-w * 0.05f, h * 0.72f, -w * 0.05f, h * 0.28f, w / 2f, 0f)
                        close()
                    }
                drawPath(path, petal)
            },
    )
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
    Row(
        modifier
            .fillMaxWidth()
            .lift(PillShape)
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
        SunIconButton(Icons.Outlined.Close, "Close search", { onQueryChange(null) })
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
    Box(modifier.fillMaxWidth().pressScale(source, 0.975f)) {
        Box(
            Modifier
                .matchParentSize()
                .chatBounds(hit.conversation.id, CardShape)
                .lift(CardShape),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable(interactionSource = source, indication = null, onClick = onClick)
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
}

private fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** The encrypted database couldn't be opened: explain, and offer a clean start. */
@Composable
private fun StorageRecovery(
    error: String,
    modifier: Modifier = Modifier,
    onReset: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, colors.error.copy(alpha = 0.5f), CardShape)
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

/** Long model names stop here so the backend stays next to them. */
private val NAME_MAX = 170.dp
