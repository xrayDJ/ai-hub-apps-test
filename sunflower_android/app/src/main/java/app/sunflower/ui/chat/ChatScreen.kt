package app.sunflower.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun ChatScreen(
    state: ChatState,
    onSend: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    var input by rememberSaveable { mutableStateOf("") }
    var editingPrompt by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Follow new messages to the bottom.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
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
                subtitle = {
                    Text(
                        if (state.modelLoaded) "Model ready" else "No model loaded",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.modelLoaded) colors.tertiary else colors.onSurfaceVariant,
                        modifier = if (state.modelLoaded) Modifier else Modifier.clickable(onClick = onOpenModels),
                    )
                },
                actions = { SunIconButton(SunIcons.Script, "System prompt", { editingPrompt = true }) },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    EmptyChat(Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageItem(message, Modifier.animateItem())
                        }
                    }
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
private fun EmptyChat(modifier: Modifier = Modifier) {
    SunflowerMark(modifier = modifier, size = 72.dp)
}

@Composable
private fun MessageItem(
    message: MessageEntity,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    if (message.role == ConversationRepository.ROLE_USER) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                message.content,
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
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            SunflowerMark(size = 26.dp, bloom = false)
            Spacer(Modifier.size(12.dp))
            Text(message.content, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
        }
    }
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
