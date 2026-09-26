package app.sunflower.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The handful of icons Material's core set lacks, drawn on a 24-unit grid with 2-unit strokes. */
object SunIcons {
    private fun icon(
        name: String,
        block: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            ).build()

    /** Three sliders: the parameters sheet. */
    val Tune =
        icon("Tune") {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 18f); lineTo(20f, 18f)
            moveTo(9f, 4f); lineTo(9f, 8f)
            moveTo(15f, 10f); lineTo(15f, 14f)
            moveTo(7f, 16f); lineTo(7f, 20f)
        }

    /** Stacked layers: the model library. */
    val Layers =
        icon("Layers") {
            moveTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); lineTo(3f, 8f); close()
            moveTo(3f, 12.5f); lineTo(12f, 17.5f); lineTo(21f, 12.5f)
            moveTo(3f, 17f); lineTo(12f, 22f); lineTo(21f, 17f)
        }

    /** Speech lines: the system prompt. */
    val Script =
        icon("Script") {
            moveTo(5f, 4f); lineTo(19f, 4f); lineTo(19f, 16f); lineTo(11f, 16f); lineTo(6f, 20f); lineTo(6f, 16f); lineTo(5f, 16f); close()
            moveTo(9f, 8.5f); lineTo(15f, 8.5f)
            moveTo(9f, 12f); lineTo(13f, 12f)
        }

    /** Arrow up out of a tray: import a file. */
    val Import =
        icon("Import") {
            moveTo(12f, 15f); lineTo(12f, 3f)
            moveTo(7f, 8f); lineTo(12f, 3f); lineTo(17f, 8f)
            moveTo(4f, 15f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 15f)
        }

    /** Filled rounded square: stop generating. */
    val Stop =
        ImageVector
            .Builder("Stop", 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 6f); lineTo(16f, 6f)
                arcTo(2f, 2f, 0f, false, true, 18f, 8f); lineTo(18f, 16f)
                arcTo(2f, 2f, 0f, false, true, 16f, 18f); lineTo(8f, 18f)
                arcTo(2f, 2f, 0f, false, true, 6f, 16f); lineTo(6f, 8f)
                arcTo(2f, 2f, 0f, false, true, 8f, 6f); close()
            }.build()

    /** Chevron, rotated by callers for expand/collapse. */
    val ChevronDown =
        icon("ChevronDown") {
            moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
        }

    /** Two overlapping sheets: copy. */
    val Copy =
        icon("Copy") {
            moveTo(9f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); lineTo(9f, 20f); close()
            moveTo(5f, 15f); lineTo(4f, 15f); lineTo(4f, 4f); lineTo(15f, 4f); lineTo(15f, 5f)
        }

    /** Circular arrow: regenerate. */
    val Regenerate =
        icon("Regenerate") {
            moveTo(20f, 11f)
            arcTo(8f, 8f, 0f, true, false, 17.66f, 17.66f)
            moveTo(20f, 4f); lineTo(20f, 11f); lineTo(13f, 11f)
        }

    /** Pencil: edit. */
    val Edit =
        icon("Edit") {
            moveTo(4f, 20f); lineTo(8f, 19f); lineTo(19f, 8f); lineTo(16f, 5f); lineTo(5f, 16f); close()
            moveTo(14f, 7f); lineTo(17f, 10f)
        }

    /** Padlock: encryption status. */
    val Lock =
        icon("Lock") {
            moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 21f); lineTo(6f, 21f); close()
            moveTo(8.5f, 11f); lineTo(8.5f, 7.5f)
            arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 7.5f)
            lineTo(15.5f, 11f)
        }
}
