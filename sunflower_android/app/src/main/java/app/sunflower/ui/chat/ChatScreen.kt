package app.sunflower.ui.chat

import app.sunflower.ui.theme.rememberShimmer
import app.sunflower.ui.theme.lift
import app.sunflower.ui.theme.SunflowerTheme
import app.sunflower.ui.theme.SmoothCornerShape
import app.sunflower.ui.theme.CardShape
import app.sunflower.ui.components.chatBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.sunflower.ui.theme.MonoFamily
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.animation.animateColorAsState
import android.Manifest
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sunflower.data.ConversationRepository
import app.sunflower.data.db.ModelEntity
import app.sunflower.data.displayName
import app.sunflower.ui.components.RenameField
import app.sunflower.engine.Reasoning
import app.sunflower.data.db.MessageEntity
import app.sunflower.ui.components.InfoHintButton
import app.sunflower.ui.components.InfoHintText
import app.sunflower.ui.components.ScreenHeader
import app.sunflower.ui.components.SunButton
import app.sunflower.ui.components.SunButtonStyle
import app.sunflower.ui.components.SunIconButton
import app.sunflower.ui.components.SunIcons
import app.sunflower.ui.components.SunflowerMark
import app.sunflower.ui.components.rememberHaptics
import app.sunflower.ui.markdown.MarkdownText
import app.sunflower.ui.markdown.copyToClipboard
import app.sunflower.ui.theme.Motion
import kotlinx.coroutines.launch
import java.util.Locale

/** Items fade and slide in, but vanish at once: fading removals can leave ghost rows behind. */
private fun Modifier.messageAnimation(scope: LazyItemScope): Modifier =
    with(scope) { this@messageAnimation.animateItem(fadeInSpec = tween(Motion.MEDIUM), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = null) }

@Composable
fun ChatScreen(
    state: ChatState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: (messageId: String, text: String) -> Unit,
    onLoadChatModel: () -> Unit,
    onKeepCurrentModel: () -> Unit,
    onToggleThinking: () -> Unit,
    onRename: (String) -> Unit,
    onVersion: (step: Int) -> Unit,
    focusMessageId: String?,
    onSystemPromptChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: (modelId: String) -> Unit,
    promptActions: PromptLibraryActions,
    /** Shared with this chat's row on the home screen, which grows into it. */
    boundsKey: String = "new",
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    var input by rememberSaveable { mutableStateOf("") }
    var editingPrompt by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    // A message opened from search is brought into view once, instead of the end of the chat.
    var pendingFocus by rememberSaveable { mutableStateOf(focusMessageId) }
    var highlightId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Asked once, on the first message: lets the "writing a reply" notification show while in the background.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askForNotificationsOnce() {
        val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val listState = rememberLazyListState()
    val lastUserId = state.messages.lastOrNull { it.role == ConversationRepository.ROLE_USER }?.id
    // A message the user just sent rises out of the message box into place.
    var riseArmed by remember { mutableStateOf(false) }
    var riseAfter by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lastUserId) {
        if (riseArmed && lastUserId != riseAfter) {
            delay(1_000)
            riseArmed = false
        }
    }
    // Flipping versions slides the new one in from the side it was pulled from.
    var versionFlip by remember { mutableIntStateOf(0) }
    val flipVersion: (Int) -> Unit = { dir ->
        versionFlip = dir
        onVersion(dir)
    }
    val lastId = state.messages.lastOrNull()?.id
    val showStreaming = state.streaming?.let { live -> state.messages.none { it.id == live.messageId } } == true
    val itemCount = state.messages.size + if (showStreaming) 1 else 0

    // Follow the conversation while the user is at the bottom; stop following
    // as soon as they scroll up to read, resume when they come back down.
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) -> if (scrolling) following = !canScrollForward }
    }
    LaunchedEffect(itemCount) {
        val focus = pendingFocus
        val focusIndex = focus?.let { id -> state.messages.indexOfFirst { it.id == id } } ?: -1
        if (focusIndex >= 0) {
            pendingFocus = null
            following = false
            listState.scrollToItem(focusIndex)
            highlightId = focus
            delay(1_600)
            highlightId = null
        } else if (itemCount > 0) {
            pendingFocus = null
            highlightId = null
            following = true
            listState.animateScrollToItem(itemCount - 1)
        }
    }
    LaunchedEffect(state.streaming?.content?.length, state.streaming?.thinking?.length) {
        if (following && itemCount > 0) listState.scrollToItem(itemCount - 1, Int.MAX_VALUE)
    }

    LaunchedEffect(lastId) {
        if (versionFlip != 0) {
            delay(150)
            versionFlip = 0
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .chatBounds(boundsKey, CardShape)
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenHeader(
                title = state.title,
                onBack = onBack,
                subtitle = { ModelSubtitle(state.model, onOpenModels) },
                onTitleClick = if (state.messages.isNotEmpty()) ({ renaming = true }) else null,
                actions = {
                    SunIconButton(
                        SunIcons.Tune,
                        "Model settings",
                        { (state.model as? ModelStatus.Ready)?.let { onOpenSettings(it.id) } ?: onOpenModels() },
                    )
                    SunIconButton(SunIcons.Script, "System prompt", { editingPrompt = true })
                },
            )

            AnimatedVisibility(
                visible = renaming,
                enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
                exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
            ) {
                RenameField(
                    initial = state.title,
                    onSave = {
                        onRename(it)
                        renaming = false
                    },
                    onCancel = { renaming = false },
                    modifier = Modifier.padding(start = 24.dp, end = 16.dp, bottom = 6.dp),
                )
            }

            val contextSize = state.contextSize
            if (contextSize != null && state.messages.isNotEmpty()) {
                ContextMeter(used = state.contextUsed, size = contextSize, replyReserve = state.replyReserve)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (itemCount == 0) {
                    EmptyChat(Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            val isLast = message.id == lastId
                            val glow by animateColorAsState(
                                if (highlightId == message.id) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                                Motion.enter(),
                                label = "focusGlow",
                            )
                            val glowModifier = Modifier.background(glow, RoundedCornerShape(18.dp))
                            val toggle = { selectedId = if (selectedId == message.id) null else message.id }
                            val copy = {
                                copyToClipboard(context, message.content)
                                haptics.tick()
                                selectedId = null
                            }
                            if (message.role == ConversationRepository.ROLE_USER) {
                                val canEdit = message.id == lastUserId && state.canSend
                                UserMessage(
                                    text = message.content,
                                    rise = riseArmed && message.id == lastUserId && message.id != riseAfter,
                                    showActions = selectedId == message.id,
                                    onTap = toggle,
                                    onCopy = copy,
                                    onEdit =
                                        if (canEdit) {
                                            {
                                                editingId = message.id
                                                input = message.content
                                                selectedId = null
                                            }
                                        } else {
                                            null
                                        },
                                    modifier = Modifier.messageAnimation(this).then(glowModifier),
                                )
                            } else {
                                AssistantMessage(
                                    content = message.content,
                                    thinking = message.thinking,
                                    thinkingOpen = false,
                                    thinkingMs = message.thinkingMs,
                                    working = false,
                                    stats = statsLine(message),
                                    // The latest reply keeps its actions in view; older ones show them on tap.
                                    showActions = (isLast && !state.generating) || selectedId == message.id,
                                    onTap = toggle,
                                    onCopy = copy,
                                    onRegenerate = if (isLast && state.canSend) onRegenerate else null,
                                    versions = state.versions?.takeIf { isLast && !state.generating },
                                    onVersion = flipVersion,
                                    enterFrom = if (isLast) versionFlip else 0,
                                    modifier = Modifier.messageAnimation(this).then(glowModifier),
                                )
                            }
                        }
                        state.streaming?.takeIf { live -> state.messages.none { it.id == live.messageId } }?.let { live ->
                            // Same key the saved message will use, so finishing a reply updates
                            // this row in place instead of swapping one list item for another.
                            item(key = live.messageId) {
                                AssistantMessage(
                                    content = live.content,
                                    thinking = live.thinking,
                                    thinkingOpen = live.thinkingOpen,
                                    thinkingStartedAt = live.thinkingStartedAt,
                                    thinkingMs = live.thinkingMs,
                                    working = true,
                                    stats = null,
                                    showActions = false,
                                    onTap = {},
                                    onCopy = {},
                                    onRegenerate = null,
                                    modifier = Modifier.messageAnimation(this),
                                )
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !following && itemCount > 0,
                    enter = fadeIn(Motion.enter()) + scaleIn(Motion.bouncy()),
                    exit = fadeOut(Motion.exit()) + scaleOut(Motion.exit()),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                ) {
                    SunIconButton(
                        icon = SunIcons.ChevronDown,
                        contentDescription = "Jump to latest",
                        onClick = {
                            following = true
                            scope.launch { listState.animateScrollToItem(itemCount - 1, 0) }
                        },
                        size = 40.dp,
                        modifier = Modifier.lift(CircleShape, elevation = 8.dp),
                    )
                }
            }

            ChatModelOffer(state.chatModel, state.model, onLoadChatModel, onKeepCurrentModel)

            AnimatedVisibility(
                visible = state.failure != null,
                enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
                exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Reply failed: ${state.failure.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    SunButton("Retry", onRetry, style = SunButtonStyle.Ghost, enabled = state.model is ModelStatus.Ready && !state.generating)
                }
            }

            AnimatedVisibility(
                visible = editingId != null,
                enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
                exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Editing message", style = MaterialTheme.typography.labelMedium, color = colors.primary, modifier = Modifier.weight(1f))
                    SunButton(
                        "Cancel",
                        {
                            editingId = null
                            input = ""
                        },
                        style = SunButtonStyle.Ghost,
                    )
                }
            }

            Composer(
                text = input,
                onTextChange = { input = it },
                onSend = {
                    riseAfter = lastUserId
                    riseArmed = true
                    askForNotificationsOnce()
                    val editing = editingId
                    if (editing != null) onEdit(editing, input) else onSend(input)
                    editingId = null
                    input = ""
                    haptics.confirm()
                },
                onStop = onStop,
                generating = state.generating,
                ready = state.canSend,
                thinking = if (state.reasoning == Reasoning.Switchable && state.model is ModelStatus.Ready) state.thinking else null,
                onToggleThinking = onToggleThinking,
                modifier =
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        AnimatedVisibility(
            visible = editingPrompt,
            enter = fadeIn(Motion.enter()) + slideInVertically(Motion.slide) { it / 4 },
            exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.slide) { it / 4 },
        ) {
            SystemPromptEditor(
                initial = state.systemPrompt,
                library = state.promptLibrary,
                actions = promptActions,
                onSave = {
                    onSystemPromptChange(it)
                    editingPrompt = false
                },
                onDismiss = { editingPrompt = false },
            )
        }
    }
}

@Composable
private fun ModelSubtitle(
    model: ModelStatus,
    onOpenModels: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelMedium
    Row(Modifier.clickable(onClick = onOpenModels), verticalAlignment = Alignment.CenterVertically) {
        when (model) {
            is ModelStatus.Ready -> {
                // Long GGUF names are shortened in the middle so the backend always shows.
                Text(model.name, style = style, color = colors.tertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f, fill = false))
                Text("  ·  ${model.backend}", style = style, color = colors.tertiary, maxLines = 1)
            }
            is ModelStatus.Loading ->
                Text("Loading ${model.name}…", style = style, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            ModelStatus.None -> Text("No model loaded", style = style, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * Offers the model this chat last ran with when another one (or none) is loaded.
 * Quiet: one line above the composer that goes away once either choice is made.
 */
@Composable
private fun ChatModelOffer(
    chatModel: ModelEntity?,
    loaded: ModelStatus,
    onLoad: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Keep the last name while the row animates out.
    var shown by remember { mutableStateOf(chatModel) }
    if (chatModel != null) shown = chatModel
    AnimatedVisibility(
        visible = chatModel != null,
        enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
        exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "This chat used ${shown?.displayName.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
            if (loaded is ModelStatus.Ready) SunButton("Keep current", onKeep, style = SunButtonStyle.Ghost)
            SunButton(if (loaded is ModelStatus.Ready) "Switch" else "Load", onLoad, style = SunButtonStyle.Ghost)
        }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    SunflowerMark(modifier = modifier, size = 72.dp)
}

@Composable
private fun UserMessage(
    text: String,
    /** Just sent: rises out of the message box instead of appearing in place. */
    rise: Boolean,
    showActions: Boolean,
    onTap: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val extras = SunflowerTheme.extras
    // 1 = still down at the message box, 0 = in place.
    val travel = remember { Animatable(if (rise) 1f else 0f) }
    LaunchedEffect(Unit) { if (travel.value > 0f) travel.animateTo(0f, Motion.lively()) }
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val t = travel.value
                translationY = t * 110.dp.toPx()
                val scale = 1f - 0.1f * t
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.75f * t.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(1f, 1f)
            },
        horizontalAlignment = Alignment.End,
    ) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = extras.onBubble,
                modifier =
                    Modifier
                        .widthIn(max = 320.dp)
                        .lift(BubbleShape, elevation = 0.dp, color = extras.bubble)
                        .clickable(onClick = onTap)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
        MessageActions(showActions) {
            ActionButton(SunIcons.Copy, "Copy", onCopy)
            if (onEdit != null) ActionButton(SunIcons.Edit, "Edit", onEdit)
        }
    }
}

@Composable
private fun AssistantMessage(
    content: String,
    thinking: String?,
    thinkingOpen: Boolean,
    thinkingStartedAt: Long? = null,
    thinkingMs: Long? = null,
    working: Boolean,
    stats: String?,
    showActions: Boolean,
    onTap: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    versions: Versions? = null,
    onVersion: (Int) -> Unit = {},
    /** A version just flipped to: slides in from the right (1) or left (-1). */
    enterFrom: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val shift = remember { Animatable(enterFrom * with(density) { 72.dp.toPx() }) }
    val shown = remember { Animatable(if (enterFrom != 0) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enterFrom != 0) {
            launch { shown.animateTo(1f, tween(320)) }
            shift.animateTo(0f, Motion.lively())
        }
    }
    // Swiping the latest reply sideways flips between its versions, with resistance past the ends.
    val swipe =
        if (versions == null) {
            Modifier
        } else {
            Modifier.pointerInput(versions) {
                val threshold = 56.dp.toPx()
                fun settle() {
                    scope.launch { shift.animateTo(0f, Motion.lively()) }
                }
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val dir =
                            when {
                                shift.value < -threshold -> 1
                                shift.value > threshold -> -1
                                else -> 0
                            }
                        if (dir != 0 && versions.index + dir in 0 until versions.count) {
                            haptics.tick()
                            onVersion(dir)
                            // Should the flip not happen after all, come back.
                            scope.launch {
                                delay(700)
                                shift.animateTo(0f, Motion.lively())
                            }
                        } else {
                            settle()
                        }
                    },
                    onDragCancel = { settle() },
                ) { change, amount ->
                    change.consume()
                    val next = shift.value + amount
                    val dir = if (next < 0) 1 else -1
                    val resistance = if (versions.index + dir in 0 until versions.count) 0.6f else 0.18f
                    scope.launch { shift.snapTo(shift.value + amount * resistance) }
                }
            }
        }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        SunflowerMark(size = 24.dp, spinning = working, bloom = false, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .then(swipe)
                .graphicsLayer {
                    translationX = shift.value
                    alpha = shown.value
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!thinking.isNullOrBlank() || thinkingOpen) {
                ThinkingBlock(thinking.orEmpty(), live = thinkingOpen, startedAt = thinkingStartedAt, durationMs = thinkingMs)
            }
            if (content.isNotEmpty()) {
                SelectionContainer {
                    MarkdownText(content, Modifier.clickable(interactionSource = null, indication = null, onClick = onTap), live = working)
                }
            }
            if (stats != null && !showActions) {
                Text(stats, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant.copy(alpha = 0.7f))
            }
            MessageActions(showActions) {
                ActionButton(SunIcons.Copy, "Copy", onCopy)
                if (onRegenerate != null) ActionButton(SunIcons.Regenerate, "Regenerate", onRegenerate)
                if (versions != null) VersionSwitcher(versions, onVersion)
                if (stats != null) {
                    Text(
                        stats,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/** A quiet row of icon buttons that folds in and out. */
@Composable
private fun MessageActions(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
        exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** ‹ 2/3 ›: flips between versions of the last exchange. */
@Composable
private fun VersionSwitcher(
    versions: Versions,
    onStep: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        SunIconButton(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            "Previous version",
            { onStep(-1) },
            size = 34.dp,
            container = Color.Transparent,
            tint = colors.onSurfaceVariant.copy(alpha = if (versions.index > 0) 1f else 0.35f),
            enabled = versions.index > 0,
        )
        Text(
            "${versions.index + 1}/${versions.count}",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily),
            color = colors.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = "Version ${versions.index + 1} of ${versions.count}" },
        )
        SunIconButton(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            "Next version",
            { onStep(1) },
            size = 34.dp,
            container = Color.Transparent,
            tint = colors.onSurfaceVariant.copy(alpha = if (versions.index < versions.count - 1) 1f else 0.35f),
            enabled = versions.index < versions.count - 1,
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    SunIconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        size = 34.dp,
        container = Color.Transparent,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Reasoning stays folded away unless the user opens it. While it's being
 * written, the header counts up and shows the latest line, faded, so it feels
 * alive without taking over the chat.
 */
@Composable
private fun ThinkingBlock(
    text: String,
    live: Boolean,
    startedAt: Long?,
    durationMs: Long?,
) {
    val colors = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.snappy(), label = "chevron")
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    if (live && startedAt != null) {
        LaunchedEffect(startedAt) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(250)
            }
        }
    }
    val header =
        when {
            live -> "Thinking…" + (startedAt?.let { " ${((now - it) / 1000).coerceAtLeast(0)} s" } ?: "")
            durationMs != null -> "Thought for ${formatSeconds(durationMs)}"
            else -> "Thought process"
        }
    Column {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { open = !open }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) {
                Text(header, style = MaterialTheme.typography.labelMedium.copy(brush = rememberShimmer(colors.onSurfaceVariant, colors.onSurface)))
            } else {
                Text(header, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
            Icon(SunIcons.ChevronDown, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp).rotate(rotation))
        }
        // Folded and still thinking: one faded line of the latest reasoning.
        AnimatedVisibility(
            visible = live && !open && text.isNotBlank(),
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit()),
        ) {
            Text(
                text.lineSequence().lastOrNull { it.isNotBlank() }.orEmpty().trim(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(top = 6.dp)
                        .lift(CardShape, elevation = 0.dp)
                        .padding(14.dp),
            )
        }
    }
}

private fun formatSeconds(ms: Long): String =
    if (ms < 60_000) "${(ms / 1000).coerceAtLeast(1)} s" else "${ms / 60_000} min ${(ms % 60_000) / 1000} s"

private fun statsLine(message: MessageEntity): String? {
    val speed = message.decodeTokensPerSec?.takeIf { it > 0 } ?: return null
    val parts = mutableListOf(String.format(Locale.US, "%.1f tok/s", speed))
    // The runtime's first-token time isn't always meaningful (e.g. with speculation); skip values too small to be real.
    message.ttftMs?.takeIf { it >= 5 }?.let { ms ->
        parts += if (ms < 1000) "${ms.toInt()} ms to first token" else String.format(Locale.US, "%.1f s to first token", ms / 1000)
    }
    val drafted = message.draftTokens
    val accepted = message.draftAccepted
    if (drafted != null && drafted > 0 && accepted != null) {
        parts += "${(accepted * 100 / drafted)}% of $drafted guesses accepted"
    }
    return parts.joinToString("  ·  ")
}


/** The user's bubble: soft all round, a little tighter at the corner nearest the message box. */
private val BubbleShape = SmoothCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 7.dp, bottomStart = 20.dp)
