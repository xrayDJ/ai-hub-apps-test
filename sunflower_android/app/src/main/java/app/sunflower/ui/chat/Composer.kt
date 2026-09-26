package app.sunflower.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.sunflower.ui.components.SunIconButton
import app.sunflower.ui.components.SunIcons
import app.sunflower.ui.theme.Motion

@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    generating: Boolean,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val canSend = ready && text.isNotBlank()
    val active = canSend || generating
    val shape = RoundedCornerShape(28.dp)
    val buttonScale by animateFloatAsState(if (active) 1f else 0.82f, Motion.bouncy(), label = "sendScale")
    val container by animateColorAsState(if (active) colors.primary else colors.surfaceContainerHighest, Motion.enter(), label = "sendBg")
    val tint by animateColorAsState(if (active) colors.onPrimary else colors.onSurfaceVariant, Motion.enter(), label = "sendFg")

    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text(
                    if (ready || generating) "Message" else "No model loaded",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = generating,
            transitionSpec = { (scaleIn(Motion.bouncy()) + fadeIn(Motion.enter())) togetherWith (scaleOut(Motion.exit()) + fadeOut(Motion.exit())) },
            label = "sendStop",
        ) { isGenerating ->
            SunIconButton(
                icon = if (isGenerating) SunIcons.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (isGenerating) "Stop" else "Send",
                onClick = if (isGenerating) onStop else onSend,
                enabled = isGenerating || canSend,
                container = container,
                tint = tint,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    },
            )
        }
    }
}
