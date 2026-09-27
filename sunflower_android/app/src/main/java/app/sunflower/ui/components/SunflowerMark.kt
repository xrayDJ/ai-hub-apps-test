package app.sunflower.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sunflower.ui.theme.Motion
import app.sunflower.ui.theme.SunflowerTheme
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val PETALS = 12
private const val PETAL_STEP = 360f / PETALS

/** Golden angle in radians: the spacing real sunflower seeds grow at. */
private val GOLDEN_ANGLE = (PI * (3 - sqrt(5.0))).toFloat()

/**
 * The Sunflower mark, drawn live so it can move.
 *
 * It blooms open when it first appears, and while [spinning] is true (a model
 * loading or thinking) it turns slowly, then settles back onto a petal when done.
 * [breathing] makes it swell very slightly every few seconds (a model is ready);
 * [dormant] folds the petals in and mutes them (nothing loaded).
 */
@Composable
fun SunflowerMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    spinning: Boolean = false,
    bloom: Boolean = true,
    breathing: Boolean = false,
    dormant: Boolean = false,
) {
    val extras = SunflowerTheme.extras
    val open = remember { Animatable(if (bloom) 0f else 1f) }
    val angle = remember { Animatable(0f) }
    val rest = remember { Animatable(if (dormant) 1f else 0f) }
    val breath = remember { Animatable(0f) }

    LaunchedEffect(Unit) { open.animateTo(1f, Motion.bouncy()) }
    LaunchedEffect(dormant) { rest.animateTo(if (dormant) 1f else 0f, Motion.lively()) }
    LaunchedEffect(breathing) {
        if (breathing) {
            while (isActive) {
                breath.animateTo(1f, tween(2200, easing = Motion.EaseInOut))
                breath.animateTo(0f, tween(2200, easing = Motion.EaseInOut))
            }
        } else {
            breath.animateTo(0f, Motion.soft())
        }
    }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (isActive) {
                angle.animateTo(angle.value + 360f, tween(4800, easing = LinearEasing))
            }
        } else {
            // Come to rest on the nearest petal so the mark always looks deliberate.
            val rest = (Math.round(angle.value / PETAL_STEP) * PETAL_STEP)
            angle.animateTo(rest, Motion.snappy())
        }
    }

    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                val swell = 1f + 0.035f * breath.value
                scaleX = swell
                scaleY = swell
            },
    ) {
        val folded = rest.value
        val progress = open.value * (1f - 0.38f * folded)
        val unit = this.size.minDimension / 64f
        val inner = lerp(extras.petalInner, extras.seedHighlight, 0.75f * folded)
        val outer = lerp(extras.petalOuter, extras.seedHighlight, 0.7f * folded)
        rotate(angle.value + (1f - open.value) * -40f - 14f * folded) {
            petals(inner, unit, rIn = 8f, rOut = 24f * progress, width = 5f * progress, offsetDeg = PETAL_STEP / 2)
            petals(outer, unit, rIn = 9f, rOut = 31f * progress, width = 6.2f * progress, offsetDeg = 0f)
        }
        drawCircle(extras.seed, radius = 11.5f * unit * (0.6f + 0.4f * progress), center = center)
        seeds(extras.seedHighlight, unit, progress)
    }
}

private fun DrawScope.petals(
    color: Color,
    unit: Float,
    rIn: Float,
    rOut: Float,
    width: Float,
    offsetDeg: Float,
) {
    if (rOut <= rIn) return
    val path = Path()
    val c = center
    for (i in 0 until PETALS) {
        val a = Math.toRadians((offsetDeg + i * PETAL_STEP).toDouble()).toFloat()
        val ux = cos(a)
        val uy = sin(a)
        val mid = (rIn + rOut) / 2f
        fun at(
            along: Float,
            across: Float,
        ) = Offset(c.x + (ux * along - uy * across) * unit, c.y + (uy * along + ux * across) * unit)
        val base = at(rIn, 0f)
        val tip = at(rOut, 0f)
        val left = at(mid, width)
        val right = at(mid, -width)
        path.moveTo(base.x, base.y)
        path.quadraticTo(left.x, left.y, tip.x, tip.y)
        path.quadraticTo(right.x, right.y, base.x, base.y)
        path.close()
    }
    drawPath(path, color)
}

private fun DrawScope.seeds(
    color: Color,
    unit: Float,
    progress: Float,
) {
    val count = 34
    val maxR = 9.5f * unit
    for (i in 1..count) {
        val r = maxR * sqrt(i / count.toFloat()) * progress
        val a = i * GOLDEN_ANGLE
        drawCircle(color, radius = 0.9f * unit, center = Offset(center.x + r * cos(a), center.y + r * sin(a)))
    }
}
