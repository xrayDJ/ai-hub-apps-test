package app.sunflower.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sunflower.ui.components.rememberHaptics
import app.sunflower.ui.theme.CodeStyle
import app.sunflower.ui.theme.MonoFamily
import app.sunflower.ui.theme.Motion
import kotlinx.coroutines.delay

/** Renders a model's Markdown reply. Cheap enough to re-run on every streamed frame. */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { RenderBlock(it, color) }
    }
}

@Composable
private fun RenderBlock(
    block: Block,
    color: Color,
) {
    val type = MaterialTheme.typography
    when (block) {
        is Block.Paragraph -> InlineText(block.text, type.bodyLarge, color)
        is Block.Heading -> {
            val style =
                when (block.level) {
                    1 -> type.headlineSmall
                    2 -> type.titleLarge
                    else -> type.titleMedium
                }
            InlineText(block.text, style, color, Modifier.padding(top = 4.dp))
        }
        is Block.Code -> CodeBlock(block)
        is Block.Quote ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.blocks.forEach { RenderBlock(it, MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        is Block.ListBlock ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(Modifier.padding(start = (item.depth * 18).dp)) {
                        val marker =
                            when {
                                block.ordered -> "${item.marker ?: (index + 1)}."
                                item.depth == 0 -> "•"
                                else -> "◦"
                            }
                        Text(
                            marker,
                            style = type.bodyLarge.copy(fontFamily = if (block.ordered) MonoFamily else type.bodyLarge.fontFamily),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = if (block.ordered) 26.dp else 16.dp),
                        )
                        InlineText(item.text, type.bodyLarge, color)
                    }
                }
            }
        is Block.Table -> TableBlock(block, color)
        Block.Rule ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
    }
}

@Composable
private fun InlineText(
    runs: List<Run>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val text =
        remember(runs, colors) {
            buildAnnotatedString {
                runs.forEach { run -> appendRun(run, colors.primary, colors.surfaceContainerHighest) }
            }
        }
    Text(text, style = style, color = color, modifier = modifier)
}

private fun AnnotatedString.Builder.appendRun(
    run: Run,
    accent: Color,
    codeBackground: Color,
) {
    val span =
        SpanStyle(
            fontWeight = if (run.bold) FontWeight.SemiBold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            textDecoration = if (run.strike) TextDecoration.LineThrough else null,
            fontFamily = if (run.code) MonoFamily else null,
            fontSize = if (run.code) 14.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            background = if (run.code) codeBackground else Color.Unspecified,
        )
    val url = run.link
    if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
        // Opens in the browser; Sunflower itself has no network access.
        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)))) {
            withStyle(span) { append(run.text) }
        }
    } else {
        withStyle(span) { append(run.text) }
    }
}

@Composable
private fun CodeBlock(block: Block.Code) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                block.language ?: "code",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AnimatedContent(
                targetState = copied,
                transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
                label = "copy",
            ) { done ->
                Text(
                    if (done) "Copied" else "Copy",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done) colors.tertiary else colors.primary,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                copyToClipboard(context, block.code)
                                haptics.tick()
                                copied = true
                            }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            block.code,
            style = CodeStyle,
            color = colors.onSurface,
            softWrap = false,
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 2.dp),
        )
    }
}

@Composable
private fun TableBlock(
    table: Block.Table,
    color: Color,
) {
    val colors = MaterialTheme.colorScheme
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    // Size columns by their longest cell so rows line up without measuring twice.
    val widths =
        (0 until columns).map { c ->
            val longest = (listOf(table.header) + table.rows).maxOf { row -> row.getOrNull(c)?.sumOf { it.text.length } ?: 0 }
            (longest * 8 + 24).coerceIn(64, 240).dp
        }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .clip(shape)
            .border(1.dp, colors.outlineVariant, shape)
            .horizontalScroll(rememberScrollState()),
    ) {
        Column {
            listOf(table.header).plus(table.rows).forEachIndexed { r, row ->
                Row(Modifier.background(if (r == 0) colors.surfaceContainerHigh else Color.Transparent)) {
                    (0 until columns).forEach { c ->
                        Box(Modifier.width(widths[c]).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            InlineText(
                                row.getOrNull(c).orEmpty(),
                                MaterialTheme.typography.bodyMedium.let { if (r == 0) it.copy(fontWeight = FontWeight.SemiBold) else it },
                                color,
                            )
                        }
                    }
                }
                if (r < table.rows.size) {
                    Box(Modifier.width(widths.fold(0.dp) { acc, w -> acc + w }).height(1.dp).background(colors.outlineVariant))
                }
            }
        }
    }
}

fun copyToClipboard(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Sunflower", text)
    // Keeps the system's copy confirmation from previewing private text.
    clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    clipboard.setPrimaryClip(clip)
}
