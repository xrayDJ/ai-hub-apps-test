package app.sunflower.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.sunflower.ui.theme.Motion
import java.text.NumberFormat

/**
 * A hairline showing how full the model's context window is. Tap for numbers.
 * Past the window, the oldest messages are left out of what the model sees.
 */
@Composable
fun ContextMeter(
    used: Int,
    size: Int,
    replyReserve: Int,
    modifier: Modifier = Modifier,
) {
    // History beyond this is trimmed, so the reply always has room.
    val limit = (size - replyReserve).coerceAtLeast(1)
    val trimming = used > limit
    val colors = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val fraction = (used.toFloat() / size).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, Motion.snappy(), label = "contextFill")
    val color by animateColorAsState(
        when {
            trimming -> colors.secondary
            fraction > 0.75f -> colors.secondary.copy(alpha = 0.8f)
            else -> colors.primary.copy(alpha = 0.7f)
        },
        Motion.enter(),
        label = "contextColor",
    )
    val numbers = remember { NumberFormat.getIntegerInstance() }
    Column(
        modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, null) { open = !open }
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(color))
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
        ) {
            Text(
                buildString {
                    append("≈ ${numbers.format(used)} of ${numbers.format(size)} tokens in the context window (${(fraction * 100).toInt()}%).")
                    append(" ${numbers.format(replyReserve)} are kept free for the reply")
                    if (trimming) append(", so the oldest messages are being left out.") else append("; past ≈ ${numbers.format(limit)} the oldest messages are left out.")
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
