package app.sunflower.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import app.sunflower.ui.theme.Motion

/** Shrinks slightly while pressed and springs back on release. */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.95f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.press(), label = "pressScale")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Small, consistent haptic vocabulary. */
class Haptics(private val view: View) {
    /** A light tick: taps, toggles, slider detents. */
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** Something happened: a message sent, a model loaded. */
    fun confirm() = view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

    /** Something was refused or failed. */
    fun reject() = view.performHapticFeedback(HapticFeedbackConstants.REJECT)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
