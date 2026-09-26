package app.sunflower.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sunflower.ui.theme.Motion

enum class SunButtonStyle { Primary, Tonal, Ghost }

/** Pill button with press-scale and a haptic tick. */
@Composable
fun SunButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: SunButtonStyle = SunButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val (bg, fg) =
        when (style) {
            SunButtonStyle.Primary -> colors.primary to colors.onPrimary
            SunButtonStyle.Tonal -> colors.surfaceContainerHighest to colors.onSurface
            SunButtonStyle.Ghost -> Color.Transparent to colors.primary
        }
    val background by animateColorAsState(if (enabled) bg else colors.surfaceContainerHigh, Motion.enter(), label = "buttonBg")
    val content by animateColorAsState(if (enabled) fg else colors.onSurfaceVariant, Motion.enter(), label = "buttonFg")

    Row(
        modifier =
            modifier
                .pressScale(source)
                .clip(CircleShape)
                .background(background)
                .clickable(source, ripple(color = content), enabled = enabled) {
                    haptics.tick()
                    onClick()
                }.padding(horizontal = 22.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** Round icon button used in headers and the composer. */
@Composable
fun SunIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Box(
        modifier =
            modifier
                .size(size)
                .pressScale(source, 0.9f)
                .clip(CircleShape)
                .background(container)
                .clickable(source, ripple(color = tint), enabled = enabled) {
                    haptics.tick()
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}
