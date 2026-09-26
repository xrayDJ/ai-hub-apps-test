package app.sunflower.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
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
import app.sunflower.ui.theme.Motion
import java.util.Locale

private const val STREAMING_KEY = "streaming"

@Composable
fun ChatScreen(
    state: ChatState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    var input by rememberSaveable { mutableStateOf("") }
    var editingPrompt by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val itemCount = state.messages.size + if (state.streaming != null) 1 else 0

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
                actions = { SunIconButton(SunIcons.Script, "System prompt", { editingPrompt = true }) },
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
                            if (message.role == ConversationRepository.ROLE_USER) {
                                UserMessage(message.content, Modifier.animateItem())
                            } else {
                                AssistantMessage(
                                    content = message.content,
                                    thinking = message.thinking,
                                    thinkingOpen = false,
                                    working = false,
                                    stats = statsLine(message),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        state.streaming?.let { live ->
                            item(key = STREAMING_KEY) {
                                AssistantMessage(
                                    content = live.content,
                                    thinking = live.thinking,
                                    thinkingOpen = live.thinkingOpen,
                                    working = true,
                                    stats = null,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
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

            Composer(
                text = input,
                onTextChange = { input = it },
                onSend = {
                    onSend(input)
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
    val (text, color) =
        when (model) {
            is ModelStatus.Ready -> "${model.name} · ${model.backend}" to colors.tertiary
            is ModelStatus.Loading -> "Loading ${model.name}…" to colors.onSurfaceVariant
            ModelStatus.None -> "No model loaded" to colors.onSurfaceVariant
        }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        modifier = Modifier.clickable(onClick = onOpenModels),
    )
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    SunflowerMark(modifier = modifier, size = 72.dp)
}

@Composable
private fun UserMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
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
                        .padding(horizontal = 16.dp, vertical = 11.dp),
            )
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
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        SunflowerMark(size = 24.dp, spinning = working, bloom = false, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).animateContentSize(Motion.snappy()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!thinking.isNullOrBlank() || thinkingOpen) {
                ThinkingBlock(thinking.orEmpty(), live = thinkingOpen)
            }
            if (content.isNotEmpty()) {
                SelectionContainer {
                    Text(content, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
                }
            }
            if (stats != null) {
                Text(stats, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
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
