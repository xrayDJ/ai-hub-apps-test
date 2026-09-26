package app.sunflower.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sunflower.data.DEFAULT_SYSTEM_PROMPT
import app.sunflower.data.STARTER_PROMPTS
import app.sunflower.data.db.SystemPromptEntity
import app.sunflower.engine.estimateTokens
import app.sunflower.ui.components.InfoHintButton
import app.sunflower.ui.components.InfoHintText
import app.sunflower.ui.components.SunButton
import app.sunflower.ui.components.SunButtonStyle
import app.sunflower.ui.components.SunChip
import app.sunflower.ui.components.SunIconButton
import app.sunflower.ui.components.rememberHaptics
import app.sunflower.ui.theme.Motion

/** Callbacks for the saved-prompt library. */
class PromptLibraryActions(
    val save: (name: String, content: String) -> Unit,
    val rename: (id: String, name: String) -> Unit,
    val delete: (id: String) -> Unit,
    val setDefault: (id: String, isDefault: Boolean) -> Unit,
)

@Composable
fun SystemPromptEditor(
    initial: String,
    library: List<SystemPromptEntity>,
    actions: PromptLibraryActions,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    var showingLibrary by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (showingLibrary) showingLibrary = false else onDismiss() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SunIconButton(Icons.Filled.Close, "Close", onDismiss)
            Spacer(Modifier.weight(1f))
            SunChip("Edit", !showingLibrary, { showingLibrary = false })
            Spacer(Modifier.size(8.dp))
            SunChip("Library", showingLibrary, { showingLibrary = true })
            Spacer(Modifier.weight(1f))
            SunButton("Use", { onSave(text.trim()) }, icon = Icons.Filled.Check, enabled = text.isNotBlank())
        }

        AnimatedContent(
            targetState = showingLibrary,
            transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
            label = "promptView",
            modifier = Modifier.weight(1f),
        ) { inLibrary ->
            if (inLibrary) {
                PromptLibraryList(
                    saved = library,
                    current = text,
                    actions = actions,
                    onPick = {
                        text = it
                        showingLibrary = false
                        haptics.tick()
                    },
                )
            } else {
                Column {
                    var explain by rememberSaveable { mutableStateOf(false) }
                    Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("System prompt", style = MaterialTheme.typography.displaySmall, color = colors.onBackground)
                        Spacer(Modifier.size(6.dp))
                        InfoHintButton(explain, { explain = !explain })
                    }
                    InfoHintText(
                        explain,
                        "Instructions the model reads before every message in this chat: its role, tone and rules. " +
                            "It takes up room in the context window on every turn, about ${estimateTokens(text)} tokens now.",
                    )
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
                    AnimatedVisibility(
                        visible = naming,
                        enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
                        exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
                    ) {
                        NameField(
                            initial = library.firstOrNull { it.content == text }?.name.orEmpty(),
                            action = "Save",
                            onDone = { name ->
                                actions.save(name, text.trim())
                                naming = false
                                haptics.confirm()
                            },
                            onCancel = { naming = false },
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val saved = library.any { it.content == text.trim() }
                        SunButton(
                            if (saved) "Saved in library" else "Save to library",
                            { naming = true },
                            style = SunButtonStyle.Ghost,
                            enabled = text.isNotBlank() && !saved && !naming,
                        )
                        Spacer(Modifier.weight(1f))
                        SunButton("Reset", { text = DEFAULT_SYSTEM_PROMPT }, style = SunButtonStyle.Ghost, enabled = text != DEFAULT_SYSTEM_PROMPT)
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptLibraryList(
    saved: List<SystemPromptEntity>,
    current: String,
    actions: PromptLibraryActions,
    onPick: (String) -> Unit,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { SectionLabel(if (saved.isEmpty()) "Your prompts: none saved yet" else "Your prompts") }
        items(saved, key = { it.id }) { prompt ->
            SavedPromptCard(
                prompt = prompt,
                selected = prompt.content == current.trim(),
                expanded = expandedId == prompt.id,
                onToggle = { expandedId = if (expandedId == prompt.id) null else prompt.id },
                onUse = { onPick(prompt.content) },
                actions = actions,
                modifier = Modifier.animateItem(),
            )
        }
        item { SectionLabel("Starters", Modifier.padding(top = 12.dp)) }
        items(STARTER_PROMPTS, key = { "starter:" + it.name }) { starter ->
            PromptCard(
                name = starter.name,
                content = starter.content,
                selected = starter.content == current.trim(),
                isDefault = false,
                onClick = { onPick(starter.content) },
            )
        }
    }
}

@Composable
private fun SavedPromptCard(
    prompt: SystemPromptEntity,
    selected: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUse: () -> Unit,
    actions: PromptLibraryActions,
    modifier: Modifier = Modifier,
) {
    var renaming by remember(prompt.id) { mutableStateOf(false) }
    var confirmingDelete by remember(prompt.id) { mutableStateOf(false) }
    Column(modifier) {
        PromptCard(
            name = prompt.name,
            content = prompt.content,
            selected = selected,
            isDefault = prompt.isDefault,
            onClick = onUse,
            onLongClickArea = onToggle,
            expanded = expanded,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                when {
                    renaming ->
                        NameField(
                            initial = prompt.name,
                            action = "Rename",
                            onDone = {
                                actions.rename(prompt.id, it)
                                renaming = false
                            },
                            onCancel = { renaming = false },
                        )
                    confirmingDelete ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Delete “${prompt.name}”?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            SunButton("Cancel", { confirmingDelete = false }, style = SunButtonStyle.Ghost)
                            SunButton("Delete", { actions.delete(prompt.id) }, style = SunButtonStyle.Tonal)
                        }
                    else ->
                        Row {
                            SunButton(
                                if (prompt.isDefault) "Default ✓" else "Make default",
                                { actions.setDefault(prompt.id, !prompt.isDefault) },
                                style = SunButtonStyle.Ghost,
                            )
                            SunButton("Rename", { renaming = true }, style = SunButtonStyle.Ghost)
                            SunButton("Delete", { confirmingDelete = true }, style = SunButtonStyle.Ghost)
                        }
                }
            }
        }
    }
}

@Composable
private fun PromptCard(
    name: String,
    content: String,
    selected: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
    onLongClickArea: (() -> Unit)? = null,
    expanded: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainer)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isDefault) {
                    Text(
                        "  ★ default",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }
            }
            Text(content, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onLongClickArea != null) {
            // Management actions live behind this quiet toggle, not on the card itself.
            Text(
                if (expanded) "Done" else "•••",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onLongClickArea)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun NameField(
    initial: String,
    action: String,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(initial) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (name.isEmpty()) Text("Name", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onDone(name) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SunButton("Cancel", onCancel, style = SunButtonStyle.Ghost)
        SunButton(action, { onDone(name) }, style = SunButtonStyle.Tonal, enabled = name.isNotBlank())
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}
