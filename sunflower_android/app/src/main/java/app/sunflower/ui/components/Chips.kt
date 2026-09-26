package app.sunflower.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.sunflower.ui.theme.Motion

/** Small selectable pill, for picking one of a few options. */
@Composable
fun SunChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val bg by animateColorAsState(if (selected) colors.primary else Color.Transparent, Motion.enter(), label = "chipBg")
    val fg by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, Motion.enter(), label = "chipFg")
    val border by animateColorAsState(if (selected) colors.primary else colors.outline, Motion.enter(), label = "chipBorder")
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier =
            modifier
                .pressScale(source, 0.92f)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape)
                .clickable(source, ripple(color = fg)) {
                    haptics.tick()
                    onClick()
                }.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Thin rounded progress bar that eases between values. */
@Composable
fun SunProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.snappy(), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
