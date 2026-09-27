package app.sunflower.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Rounded corners with continuous curvature ("squircle" corners): the curve
 * eases out of the straight edge instead of meeting it abruptly, which reads
 * softer than a circular arc. Each corner spreads over [EXTENT] times its
 * radius, so a smooth 20 dp corner looks about as round as a plain 20 dp one.
 */
data class SmoothCornerShape(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp,
) : Shape {
    constructor(all: Dp) : this(all, all, all, all)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val limit = min(w, h) / 2f
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun extent(radius: Dp) = min(with(density) { radius.toPx() } * EXTENT, limit).coerceAtLeast(0f)
        val tl = extent(if (rtl) topEnd else topStart)
        val tr = extent(if (rtl) topStart else topEnd)
        val br = extent(if (rtl) bottomStart else bottomEnd)
        val bl = extent(if (rtl) bottomEnd else bottomStart)
        val path =
            Path().apply {
                moveTo(tl, 0f)
                lineTo(w - tr, 0f)
                cubicTo(w - tr * HANDLE, 0f, w, tr * HANDLE, w, tr)
                lineTo(w, h - br)
                cubicTo(w, h - br * HANDLE, w - br * HANDLE, h, w - br, h)
                lineTo(bl, h)
                cubicTo(bl * HANDLE, h, 0f, h - bl * HANDLE, 0f, h - bl)
                lineTo(0f, tl)
                cubicTo(0f, tl * HANDLE, tl * HANDLE, 0f, tl, 0f)
                close()
            }
        return Outline.Generic(path)
    }

    private companion object {
        const val EXTENT = 1.528f
        const val HANDLE = 0.22f
    }
}

/** Cards and list rows. */
val CardShape = SmoothCornerShape(22.dp)

/** Pills: the message box, search field, main buttons. */
val PillShape = SmoothCornerShape(100.dp)

/**
 * A raised surface lit from above: a faint fall of light across it, a lit
 * top edge and a soft, wide shadow. Replaces flat fills with outlines.
 * [ring] draws a thin accent outline for selected or active surfaces.
 */
fun Modifier.lift(
    shape: Shape = CardShape,
    elevation: Dp = 10.dp,
    ring: Color = Color.Unspecified,
    /** A solid fill instead of the default fall of light. */
    color: Color = Color.Unspecified,
): Modifier =
    composed {
        val extras = SunflowerTheme.extras
        val fill = if (color != Color.Unspecified) SolidColor(color) else Brush.verticalGradient(listOf(extras.liftTop, extras.liftBottom))
        this
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false, ambientColor = extras.shadow, spotColor = extras.shadow) else Modifier)
            .clip(shape)
            .background(fill, shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val edge =
                    Brush.verticalGradient(
                        0f to extras.liftEdge,
                        1f to Color.Transparent,
                        endY = min(size.height, 26.dp.toPx()),
                    )
                val edgeStroke = Stroke(1.5.dp.toPx())
                val ringStroke = Stroke(2.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    drawOutline(outline, edge, style = edgeStroke)
                    if (ring != Color.Unspecified) drawOutline(outline, ring, style = ringStroke)
                }
            }
    }

/** A light sweeping across text, for things in progress ("Loading…", "Thinking…"). */
@Composable
fun rememberShimmer(
    base: Color,
    highlight: Color,
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val width = 360f
    return Brush.linearGradient(
        0f to base,
        0.4f to base,
        0.5f to highlight,
        0.6f to base,
        1f to base,
        start = Offset(x * width - width, 0f),
        end = Offset(x * width, 0f),
        tileMode = TileMode.Clamp,
    )
}
