package app.sunflower.ui.chat

import androidx.activity.compose.BackHandler
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
    onSystemPromptChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: (modelId: String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    var input by rememberSaveable { mutableStateOf("") }
    var editingPrompt by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lastUserId = state.messages.lastOrNull { it.role == ConversationRepository.ROLE_USER }?.id
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
        if (itemCount > 0) {
            following = true
            listState.animateScrollToItem(itemCount - 1)
        }
    }
    LaunchedEffect(state.streaming?.content?.length, state.streaming?.thinking?.length) {
        if (following && itemCount > 0) listState.scrollToItem(itemCount - 1, Int.MAX_VALUE)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenHeader(
                title = state.title,
                onBack = onBack,
                subtitle = { ModelSubtitle(state.model, onOpenModels) },
                actions = {
                    SunIconButton(
                        SunIcons.Tune,
                        "Model settings",
                        { (state.model as? ModelStatus.Ready)?.let { onOpenSettings(it.id) } ?: onOpenModels() },
                    )
                    SunIconButton(SunIcons.Script, "System prompt", { editingPrompt = true })
                },
            )

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
                                    modifier = Modifier.messageAnimation(this),
                                )
                            } else {
                                AssistantMessage(
                                    content = message.content,
                                    thinking = message.thinking,
                                    thinkingOpen = false,
                                    working = false,
                                    stats = statsLine(message),
                                    // The latest reply keeps its actions in view; older ones show them on tap.
                                    showActions = (isLast && !state.generating) || selectedId == message.id,
                                    onTap = toggle,
                                    onCopy = copy,
                                    onRegenerate = if (isLast && state.canSend) onRegenerate else null,
                                    modifier = Modifier.messageAnimation(this),
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
                        container = colors.surfaceContainerHighest,
                    )
                }
            }

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
                    val editing = editingId
                    if (editing != null) onEdit(editing, input) else onSend(input)
                    editingId = null
                    input = ""
                    haptics.confirm()
                },
                onStop = onStop,
                generating = state.generating,
                ready = state.canSend,
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

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    SunflowerMark(modifier = modifier, size = 72.dp)
}

@Composable
private fun UserMessage(
    text: String,
    showActions: Boolean,
    onTap: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onPrimaryContainer,
                modifier =
                    Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                        .background(colors.primaryContainer)
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
    working: Boolean,
    stats: String?,
    showActions: Boolean,
    onTap: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        SunflowerMark(size = 24.dp, spinning = working, bloom = false, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!thinking.isNullOrBlank() || thinkingOpen) {
                ThinkingBlock(thinking.orEmpty(), live = thinkingOpen)
            }
            if (content.isNotEmpty()) {
                SelectionContainer {
                    MarkdownText(content, Modifier.clickable(interactionSource = null, indication = null, onClick = onTap))
                }
            }
            if (stats != null && !showActions) {
                Text(stats, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant.copy(alpha = 0.7f))
            }
            MessageActions(showActions) {
                ActionButton(SunIcons.Copy, "Copy", onCopy)
                if (onRegenerate != null) ActionButton(SunIcons.Regenerate, "Regenerate", onRegenerate)
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

/** Reasoning stays folded away unless the user opens it. */
@Composable
private fun ThinkingBlock(
    text: String,
    live: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.snappy(), label = "chevron")
    Column {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { open = !open }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (live) "Thinking…" else "Thought process",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(SunIcons.ChevronDown, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp).rotate(rotation))
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
                        .border(width = 1.dp, color = colors.outlineVariant, shape = RoundedCornerShape(12.dp))
                        .padding(12.dp),
            )
        }
    }
}

private fun statsLine(message: MessageEntity): String? {
    val speed = message.decodeTokensPerSec?.takeIf { it > 0 } ?: return null
    val parts = mutableListOf(String.format(Locale.US, "%.1f tok/s", speed))
    message.ttftMs?.takeIf { it > 0 }?.let { parts += String.format(Locale.US, "%.1f s to first token", it / 1000) }
    return parts.joinToString("  ·  ")
}

@Composable
private fun SystemPromptEditor(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    BackHandler(onBack = onDismiss)
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SunIconButton(Icons.Filled.Close, "Close", onDismiss)
            Spacer(Modifier.weight(1f))
            SunButton("Save", { onSave(text.trim()) }, icon = Icons.Filled.Check, enabled = text.isNotBlank())
        }
        var explain by rememberSaveable { mutableStateOf(false) }
        Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("System prompt", style = MaterialTheme.typography.displaySmall, color = colors.onBackground)
            Spacer(Modifier.size(6.dp))
            InfoHintButton(explain, { explain = !explain })
        }
        InfoHintText(explain, "Instructions the model reads before every message in this chat: its role, tone and rules.")
        Spacer(Modifier.size(12.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(colors.surfaceContainer)
                    .padding(18.dp),
        )
        SunButton(
            "Reset to default",
            { text = app.sunflower.data.DEFAULT_SYSTEM_PROMPT },
            style = SunButtonStyle.Ghost,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
