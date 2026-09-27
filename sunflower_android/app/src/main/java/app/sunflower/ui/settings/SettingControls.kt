package app.sunflower.ui.settings

import app.sunflower.ui.theme.lift
import app.sunflower.ui.theme.CardShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.sunflower.ui.components.InfoHintButton
import app.sunflower.ui.components.InfoHintText
import app.sunflower.ui.components.SunChip
import app.sunflower.ui.components.SunIconButton
import app.sunflower.ui.components.SunIcons
import app.sunflower.ui.components.rememberHaptics
import app.sunflower.ui.theme.CodeStyle
import app.sunflower.ui.theme.MonoFamily
import app.sunflower.ui.theme.Motion
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A titled group of settings. */
@Composable
fun SettingSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .lift(CardShape)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) { content() }
    }
}

/**
 * Title, value and the two quiet affordances every setting shares:
 * an info button (explanation on demand) and a reset button (only when changed).
 */
@Composable
private fun SettingHeader(
    title: String,
    explanation: String,
    value: (@Composable () -> Unit)?,
    changed: Boolean,
    onReset: () -> Unit,
    enabled: Boolean = true,
) {
    var explain by rememberSaveable(title) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            )
            InfoHintButton(explain, { explain = !explain })
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(changed && enabled, enter = fadeIn(Motion.enter()) + scaleIn(Motion.bouncy()), exit = fadeOut(Motion.exit()) + scaleOut(Motion.exit())) {
                SunIconButton(SunIcons.Regenerate, "Reset $title", onReset, size = 32.dp, container = Color.Transparent, tint = colors.onSurfaceVariant)
            }
            value?.invoke()
        }
        InfoHintText(explain, explanation)
    }
}

/** A value shown in mono; tap it to type an exact number. */
@Composable
private fun EditableValue(
    display: String,
    onCommit: (String) -> Unit,
    enabled: Boolean,
    decimal: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }
    var text by remember(editing) { mutableStateOf(if (editing) display.filter { it.isDigit() || it == '.' || it == ',' || it == '-' } else display) }
    val focus = remember { FocusRequester() }
    val style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFamily, color = if (enabled) colors.primary else colors.onSurfaceVariant)
    Box(
        Modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (editing) colors.surfaceContainerHighest else Color.Transparent)
            .clickable(enabled = enabled) { editing = true }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (editing) {
            LaunchedEffect(Unit) { focus.requestFocus() }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        onCommit(text)
                        editing = false
                    }),
                modifier =
                    Modifier
                        .width(72.dp)
                        .focusRequester(focus)
                        .onFocusChanged { if (!it.isFocused && editing) editing = false },
            )
        } else {
            Text(display, style = style)
        }
    }
}

/** Continuous setting (temperature, probabilities, penalties). */
@Composable
fun FloatSetting(
    title: String,
    explanation: String,
    value: Float,
    default: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
    format: (Float) -> String = { String.format(Locale.US, "%.2f", it) },
    enabled: Boolean = true,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    fun snap(v: Float) = ((v / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
    Column(Modifier.padding(vertical = 6.dp)) {
        SettingHeader(
            title,
            explanation,
            value = { EditableValue(format(shown), { it.replace(',', '.').toFloatOrNull()?.let { v -> onChange(snap(v)) } }, enabled, decimal = true) },
            changed = abs(value - default) > step / 2,
            onReset = { onChange(default) },
            enabled = enabled,
        )
        SunSlider(
            value = shown,
            range = range,
            enabled = enabled,
            onValueChange = { dragging = snap(it) },
            onFinished = {
                dragging?.let(onChange)
                dragging = null
            },
        )
    }
}

/**
 * Setting picked from a fixed list of values (context sizes, batch sizes,
 * thread counts). The slider moves in steps; typing snaps to the nearest option.
 */
@Composable
fun StepSetting(
    title: String,
    explanation: String,
    value: Int,
    default: Int,
    options: List<Int>,
    onChange: (Int) -> Unit,
    format: (Int) -> String = { it.toString() },
    enabled: Boolean = true,
    footer: (@Composable (preview: Int) -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    val currentIndex = options.indexOfNearest(value)
    val shownIndex = draggingIndex ?: currentIndex
    val shown = options[shownIndex]
    Column(Modifier.padding(vertical = 6.dp)) {
        SettingHeader(
            title,
            explanation,
            value = {
                EditableValue(format(shown), { typed ->
                    typed.toIntOrNull()?.let { onChange(options[options.indexOfNearest(it)]) }
                }, enabled, decimal = false)
            },
            changed = value != default,
            onReset = { onChange(default) },
            enabled = enabled,
        )
        if (options.size > 1) {
            SunSlider(
                value = shownIndex.toFloat(),
                range = 0f..(options.size - 1).toFloat(),
                steps = (options.size - 2).coerceAtLeast(0),
                enabled = enabled,
                onValueChange = {
                    val index = it.roundToInt().coerceIn(0, options.lastIndex)
                    if (index != draggingIndex) haptics.tick()
                    draggingIndex = index
                },
                onFinished = {
                    draggingIndex?.let { onChange(options[it]) }
                    draggingIndex = null
                },
            )
        }
        footer?.invoke(shown)
    }
}

private fun List<Int>.indexOfNearest(value: Int): Int = indices.minByOrNull { abs(this[it].toLong() - value) } ?: 0

@Composable
private fun SunSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
    steps: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onFinished,
        valueRange = range,
        steps = steps,
        enabled = enabled,
        colors =
            SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.surfaceContainerHighest,
                activeTickColor = colors.onPrimary.copy(alpha = 0.4f),
                inactiveTickColor = colors.onSurfaceVariant.copy(alpha = 0.3f),
            ),
    )
}

@Composable
fun ToggleSetting(
    title: String,
    explanation: String,
    checked: Boolean,
    default: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 6.dp)) {
        SettingHeader(
            title,
            explanation,
            value = {
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        haptics.tick()
                        onChange(it)
                    },
                    enabled = enabled,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.onSurfaceVariant,
                            uncheckedTrackColor = colors.surfaceContainerHighest,
                            uncheckedBorderColor = colors.outline,
                        ),
                )
            },
            changed = checked != default,
            onReset = { onChange(default) },
            enabled = enabled,
        )
    }
}

/** One choice out of a few labelled options, shown as chips that wrap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceSetting(
    title: String,
    explanation: String,
    options: List<T>,
    selected: T,
    default: T,
    label: (T) -> String,
    onChange: (T) -> Unit,
    enabled: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        SettingHeader(title, explanation, value = null, changed = selected != default, onReset = { onChange(default) }, enabled = enabled)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            options.forEach { option ->
                SunChip(label(option), option == selected, { if (enabled) onChange(option) })
            }
        }
        footer?.invoke()
    }
}

/** Multi-line text (grammars, chat templates). Saves when it loses focus. */
@Composable
fun TextAreaSetting(
    title: String,
    explanation: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    minLines: Int = 4,
    above: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 6.dp)) {
        SettingHeader(
            title,
            explanation,
            value = null,
            changed = value.isNotEmpty(),
            onReset = {
                text = ""
                onChange("")
            },
            enabled = enabled,
        )
        above?.invoke()
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainerHigh)
                .padding(12.dp),
        ) {
            if (text.isEmpty()) Text(placeholder, style = CodeStyle, color = colors.onSurfaceVariant)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                textStyle = CodeStyle.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                minLines = minLines,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused && text != value) onChange(text) },
            )
        }
    }
}

/** Editable list of stop sequences shown as removable chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopSequencesSetting(
    title: String,
    explanation: String,
    values: List<String>,
    onChange: (List<String>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    fun add() {
        // Written as \n in the field so newlines can be entered on one line.
        val entry = draft.replace("\\n", "\n")
        if (entry.isNotEmpty() && entry !in values) onChange(values + entry)
        draft = ""
    }
    Column(Modifier.padding(vertical = 6.dp)) {
        SettingHeader(title, explanation, value = null, changed = values.isNotEmpty(), onReset = { onChange(emptyList()) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { stop ->
                Row(
                    Modifier
                        .clip(CircleShape)
                        .border(1.dp, colors.outline, CircleShape)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stop.replace("\n", "\\n"), style = CodeStyle, color = colors.onSurface)
                    SunIconButton(Icons.Filled.Close, "Remove", { onChange(values - stop) }, size = 30.dp, container = Color.Transparent, tint = colors.onSurfaceVariant)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainerHigh)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (draft.isEmpty()) Text("Add a stop sequence", style = CodeStyle, color = colors.onSurfaceVariant)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = CodeStyle.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "Add",
                style = MaterialTheme.typography.labelLarge,
                color = if (draft.isNotEmpty()) colors.primary else colors.onSurfaceVariant,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = draft.isNotEmpty()) { add() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Stacked bar of how RAM would be used: model weights, context memory, and
 * what's left. Turns red when the total goes past what's currently free.
 */
@Composable
fun MemoryBar(
    modelBytes: Long,
    contextBytes: Long?,
    totalBytes: Long,
    availableBytes: Long,
    format: (Long) -> String,
) {
    val colors = MaterialTheme.colorScheme
    val needed = modelBytes + (contextBytes ?: 0)
    val tight = needed > availableBytes
    val modelShare by animateFloatAsState((modelBytes.toFloat() / totalBytes).coerceIn(0f, 1f), Motion.snappy(), label = "modelShare")
    val contextShare by animateFloatAsState(((contextBytes ?: 0).toFloat() / totalBytes).coerceIn(0f, 1f - modelShare), Motion.snappy(), label = "ctxShare")
    val contextColor by animateColorAsState(if (tight) colors.error else colors.secondary, Motion.enter(), label = "ctxColor")
    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest),
        ) {
            if (modelShare > 0f) Box(Modifier.fillMaxHeight().weight(modelShare).background(colors.primary))
            if (contextShare > 0f) Box(Modifier.fillMaxHeight().weight(contextShare).background(contextColor))
            val rest = 1f - modelShare - contextShare
            if (rest > 0f) Spacer(Modifier.weight(rest))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(colors.primary, "Model ${format(modelBytes)}")
            Legend(contextColor, "Context ${contextBytes?.let { "≈ ${format(it)}" } ?: "unknown"}")
        }
        Text(
            if (tight) {
                "Needs ${format(needed)}, but only ${format(availableBytes)} of ${format(totalBytes)} is free right now. Loading may fail."
            } else {
                "${format(needed)} of ${format(availableBytes)} free (${format(totalBytes)} total)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (tight) colors.error else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Legend(
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A number typed in directly, for values with no useful slider range (seeds). */
@Composable
fun NumberEntrySetting(
    title: String,
    explanation: String,
    value: Int,
    default: Int,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        SettingHeader(
            title,
            explanation,
            value = { EditableValue(value.toString(), { typed -> typed.toIntOrNull()?.let(onChange) }, enabled, decimal = false) },
            changed = value != default,
            onReset = { onChange(default) },
            enabled = enabled,
        )
    }
}
