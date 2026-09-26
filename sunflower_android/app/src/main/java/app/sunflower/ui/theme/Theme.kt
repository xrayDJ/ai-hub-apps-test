package app.sunflower.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

val SunflowerShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/** Dark first: the light scheme exists, but dark is what Sunflower is designed around. */
@Composable
fun SunflowerTheme(
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSunflowerExtras provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = SunflowerTypography,
            shapes = SunflowerShapes,
            content = content,
        )
    }
}

object SunflowerTheme {
    val extras: SunflowerExtras
        @Composable get() = LocalSunflowerExtras.current
}
