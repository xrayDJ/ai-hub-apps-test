package app.sunflower.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * One place for every curve, so the whole app moves with the same personality:
 * quick to respond, soft to settle.
 */
object Motion {
    /** Fast out, long soft landing. For things entering the screen. */
    val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Symmetric, for things changing in place. */
    val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    const val SHORT = 180
    const val MEDIUM = 320
    const val LONG = 520

    /** Press feedback: immediate, with a hint of bounce on release. */
    fun <T> press() = spring<T>(dampingRatio = 0.5f, stiffness = 900f)

    /** Layout and size changes: responsive without wobble. */
    fun <T> snappy() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Playful moments (the flower blooming, a send landing). */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)

    fun <T> enter() = tween<T>(MEDIUM, easing = EaseOutQuint)

    fun <T> exit() = tween<T>(SHORT, easing = EaseInOut)

    val slide = tween<IntOffset>(MEDIUM, easing = EaseOutQuint)
}
