package app.sunflower.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sunflower.ui.theme.Motion
import app.sunflower.ui.theme.PillShape

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
    // The main action glows faintly in its own colour, as if lit from within.
    val glow = style == SunButtonStyle.Primary && enabled

    Row(
        modifier =
            modifier
                .pressScale(source)
                .then(if (glow) Modifier.shadow(14.dp, PillShape, ambientColor = colors.primary, spotColor = colors.primary) else Modifier)
                .clip(PillShape)
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

/**
 * Icon button. Bare by default: the icon sits on whatever is behind it and
 * only a soft disc appears under the finger. Pass [container] for a filled one.
 */
@Composable
fun SunIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = Color.Transparent,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val pressed by source.collectIsPressedAsState()
    val touch by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        Motion.enter(),
        label = "iconTouch",
    )
    Box(
        modifier =
            modifier
                .size(size)
                .pressScale(source, 0.86f)
                .clip(CircleShape)
                .background(container)
                .background(touch)
                .clickable(source, indication = null, enabled = enabled) {
                    haptics.tick()
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}
