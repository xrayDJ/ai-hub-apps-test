package app.sunflower.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand
val Sunflower = Color(0xFFFFC21A)
val Marigold = Color(0xFFFF8A1F)
val SeedBrown = Color(0xFF2A1A08)
val SeedHighlight = Color(0xFF5C3A12)
val Leaf = Color(0xFF9ED36A)

// Warm neutrals, dark
private val Ink = Color(0xFF0C0B09)
private val Ink1 = Color(0xFF15130F)
private val Ink2 = Color(0xFF1C1914)
private val Ink3 = Color(0xFF24201A)
private val Ink4 = Color(0xFF2D2820)
private val InkLine = Color(0xFF3A3428)
private val InkLineSoft = Color(0xFF2A251D)
private val Parchment = Color(0xFFF6F0E1)
private val Dust = Color(0xFFA8A08E)

// Warm neutrals, light
private val Cream = Color(0xFFFBF7EE)
private val Cream1 = Color(0xFFF4EEDF)
private val Cream2 = Color(0xFFEDE5D2)
private val Cream3 = Color(0xFFE5DCC6)
private val Soil = Color(0xFF1B170F)
private val Bark = Color(0xFF6B6250)

val DarkColors =
    darkColorScheme(
        primary = Sunflower,
        onPrimary = Color(0xFF1C1400),
        primaryContainer = Color(0xFF3A2C05),
        onPrimaryContainer = Color(0xFFFFE08A),
        secondary = Marigold,
        onSecondary = Color(0xFF1F0E00),
        secondaryContainer = Color(0xFF3B2310),
        onSecondaryContainer = Color(0xFFFFD2A8),
        tertiary = Leaf,
        onTertiary = Color(0xFF0F1A05),
        background = Ink,
        onBackground = Parchment,
        surface = Ink,
        onSurface = Parchment,
        surfaceVariant = Ink3,
        onSurfaceVariant = Dust,
        surfaceContainerLowest = Ink,
        surfaceContainerLow = Ink1,
        surfaceContainer = Ink2,
        surfaceContainerHigh = Ink3,
        surfaceContainerHighest = Ink4,
        outline = InkLine,
        outlineVariant = InkLineSoft,
        error = Color(0xFFFF6B5E),
        onError = Color(0xFF2A0500),
        scrim = Color(0xCC000000),
    )

val LightColors =
    lightColorScheme(
        primary = Color(0xFFF2B200),
        onPrimary = Color(0xFF1C1400),
        primaryContainer = Color(0xFFFFE8A3),
        onPrimaryContainer = Color(0xFF3A2C05),
        secondary = Color(0xFFE56F00),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDCC0),
        onSecondaryContainer = Color(0xFF3B2310),
        tertiary = Color(0xFF4F7F23),
        onTertiary = Color.White,
        background = Cream,
        onBackground = Soil,
        surface = Cream,
        onSurface = Soil,
        surfaceVariant = Cream2,
        onSurfaceVariant = Bark,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Cream1,
        surfaceContainer = Cream1,
        surfaceContainerHigh = Cream2,
        surfaceContainerHighest = Cream3,
        outline = Color(0xFFD3C8AE),
        outlineVariant = Color(0xFFE3DAC4),
        error = Color(0xFFC62E20),
        onError = Color.White,
    )

/** Brand colours that Material's scheme has no slot for. */
@Immutable
data class SunflowerExtras(
    val petalOuter: Color,
    val petalInner: Color,
    val seed: Color,
    val seedHighlight: Color,
    val glow: Color,
    /** Raised surfaces: a faint vertical fall of light, a lit top edge and a soft shadow. */
    val liftTop: Color,
    val liftBottom: Color,
    val liftEdge: Color,
    val shadow: Color,
    /** The user's own messages. */
    val bubble: Color,
    val onBubble: Color,
)

val DarkExtras =
    SunflowerExtras(
        petalOuter = Sunflower,
        petalInner = Marigold,
        seed = SeedBrown,
        seedHighlight = SeedHighlight,
        glow = Sunflower.copy(alpha = 0.18f),
        liftTop = Color(0xFF1C1915),
        liftBottom = Color(0xFF15130F),
        liftEdge = Color(0x17FFECC8),
        shadow = Color.Black,
        bubble = Color(0xFF2A2318),
        onBubble = Color(0xFFFBEFD6),
    )

val LightExtras =
    SunflowerExtras(
        petalOuter = Color(0xFFF2B200),
        petalInner = Color(0xFFE56F00),
        seed = SeedBrown,
        seedHighlight = SeedHighlight,
        glow = Color(0xFFF2B200).copy(alpha = 0.22f),
        liftTop = Color(0xFFFFFDF8),
        liftBottom = Color(0xFFF7F1E4),
        liftEdge = Color(0xE6FFFFFF),
        shadow = Color(0xFF5C4A2A),
        bubble = Color(0xFFF1E4C8),
        onBubble = Color(0xFF1B170F),
    )

val LocalSunflowerExtras = staticCompositionLocalOf { DarkExtras }
