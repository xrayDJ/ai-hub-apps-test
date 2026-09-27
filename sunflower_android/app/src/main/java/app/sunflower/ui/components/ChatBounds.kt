package app.sunflower.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale

/** The navigation-wide shared transition, when screens are hosted in one. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit animation of the screen this composable belongs to. */
val LocalScreenAnimation = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Ties an element to a chat screen: a chat's row on the home screen and the
 * chat itself share [key], so opening the chat grows the row into the screen and
 * going back shrinks it home. The screen's content fades in over the row as it
 * grows, clipped to [shape]. Without a shared transition around, this does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.chatBounds(
    key: String,
    shape: Shape,
): Modifier =
    composed {
        val shared = LocalSharedTransition.current
        val screen = LocalScreenAnimation.current
        if (shared == null || screen == null) {
            Modifier
        } else {
            with(shared) {
                Modifier.sharedBounds(
                    rememberSharedContentState("chat-$key"),
                    animatedVisibilityScope = screen,
                    enter = fadeIn(tween(durationMillis = 260, delayMillis = 60)),
                    exit = fadeOut(tween(durationMillis = 180)),
                    boundsTransform = ChatBoundsTransform,
                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Crop, Alignment.TopCenter),
                    clipInOverlayDuringTransition = OverlayClip(shape),
                )
            }
        }
    }

/** Geometry settles without wobble; a card growing into a screen shouldn't bounce. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val ChatBoundsTransform = BoundsTransform { _, _ -> spring(dampingRatio = 1f, stiffness = 280f) }
