package app.sunflower.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.sunflower.R

/** Bricolage Grotesque: the loud voice. Headlines, numbers, the wordmark. */
val DisplayFamily =
    FontFamily(
        Font(R.font.display_regular, FontWeight.Normal),
        Font(R.font.display_semibold, FontWeight.SemiBold),
        Font(R.font.display_extrabold, FontWeight.ExtraBold),
    )

/** Inter: everything people read at length. */
val BodyFamily =
    FontFamily(
        Font(R.font.body_regular, FontWeight.Normal),
        Font(R.font.body_medium, FontWeight.Medium),
        Font(R.font.body_semibold, FontWeight.SemiBold),
    )

/** JetBrains Mono: code, tokens, parameter values. */
val MonoFamily =
    FontFamily(
        Font(R.font.mono_regular, FontWeight.Normal),
        Font(R.font.mono_semibold, FontWeight.SemiBold),
    )

private fun display(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.ExtraBold,
    tracking: Double = -0.02,
) = TextStyle(fontFamily = DisplayFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em)

private fun body(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(fontFamily = BodyFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em)

val SunflowerTypography =
    Typography(
        displayLarge = display(56, 56, tracking = -0.035),
        displayMedium = display(44, 46, tracking = -0.03),
        displaySmall = display(36, 40),
        headlineLarge = display(32, 36),
        headlineMedium = display(26, 30),
        headlineSmall = display(22, 26, FontWeight.SemiBold, -0.01),
        titleLarge = display(20, 24, FontWeight.SemiBold, -0.01),
        titleMedium = body(16, 22, FontWeight.SemiBold),
        titleSmall = body(14, 20, FontWeight.SemiBold),
        bodyLarge = body(16, 24),
        bodyMedium = body(14, 20),
        bodySmall = body(12, 16),
        labelLarge = body(15, 20, FontWeight.SemiBold),
        labelMedium = body(12, 16, FontWeight.Medium, 0.01),
        labelSmall = body(11, 14, FontWeight.Medium, 0.04),
    )

val CodeStyle = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp, lineHeight = 20.sp)
