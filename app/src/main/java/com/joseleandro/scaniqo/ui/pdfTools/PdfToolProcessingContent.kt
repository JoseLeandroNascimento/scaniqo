package com.joseleandro.scaniqo.ui.pdfTools

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfUtility
import com.joseleandro.scaniqo.ui.branding.systemAnimationScale
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.abs

@Composable
internal fun PdfToolProcessingContent(
    tool: PdfUtility,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!busy) return
    val titleRes = when (tool) {
        PdfUtility.EXTRACT_PAGES -> R.string.utility_extract_title
        PdfUtility.PDF_TO_IMAGES -> R.string.utility_images_export_title
        PdfUtility.COMPRESS_PDF -> R.string.utility_workspace_compress_title
        else -> return
    }
    val title = stringResource(titleRes)
    val status = stringResource(R.string.utility_workspace_busy)
    val context = LocalContext.current
    val motionEnabled = remember(context) { systemAnimationScale(context) > 0f }
    val phase = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "PDF tool processing")
        val cycle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "Tool activity",
        )
        cycle
    } else 0.5f
    val activity = FastOutSlowInEasing.transform(1f - abs((phase * 2f) - 1f))
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "$title. $status"
            liveRegion = LiveRegionMode.Polite
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val unit = minOf(size.width / 240f, size.height / 158f)
                translate(
                    left = (size.width - (240f * unit)) / 2f,
                    top = (size.height - (158f * unit)) / 2f,
                ) {
                    scale(unit, pivot = Offset.Zero) {
                        drawCircle(colors.primary.copy(alpha = 0.07f), 67f, Offset(120f, 77f))
                        drawCircle(colors.tertiary.copy(alpha = 0.06f), 35f, Offset(184f, 70f))
                        when (tool) {
                            PdfUtility.EXTRACT_PAGES -> drawExtraction(
                                activity, colors.primary, colors.tertiary,
                                colors.surfaceContainerLowest, colors.outlineVariant,
                            )
                            PdfUtility.PDF_TO_IMAGES -> drawConversion(
                                activity, colors.primary, colors.tertiary,
                                colors.surfaceContainerLowest, colors.outlineVariant,
                            )
                            PdfUtility.COMPRESS_PDF -> drawCompression(
                                activity, colors.primary, colors.tertiary,
                                colors.surfaceContainerLowest, colors.outlineVariant,
                            )
                        }
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

private fun DrawScope.drawExtraction(
    activity: Float,
    primary: Color,
    accent: Color,
    paper: Color,
    outline: Color,
) {
    val travel = activity * 46f
    toolPaper(100f + travel, 23f - (activity * 14f), 52f, 67f, paper, outline, accent)
    toolPaper(106f + travel, 75f + activity * 12f, 52f, 67f, paper, outline, accent)
    toolPaper(33f, 32f, 77f, 99f, paper, outline, primary)
    drawLine(
        color = primary.copy(alpha = 0.72f),
        start = Offset(115f, 78f),
        end = Offset(129f + activity * 15f, 78f),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = accent.copy(alpha = 0.35f + activity * 0.45f),
        radius = 3f + activity * 2f,
        center = Offset(202f, 31f),
    )
}

private fun DrawScope.drawConversion(
    activity: Float,
    primary: Color,
    accent: Color,
    paper: Color,
    outline: Color,
) {
    val travel = activity * 18f
    toolPaper(30f, 28f, 81f, 106f, paper, outline, primary)
    toolPaper(125f + travel, 35f, 82f, 96f, paper, outline, accent, picture = true)
    for (index in 0..2) {
        drawCircle(
            color = primary.copy(alpha = (0.22f + activity * 0.5f).coerceAtMost(0.75f)),
            radius = 2.2f + activity,
            center = Offset(121f + index * 8f + activity * 6f, 79f - index * 5f),
        )
    }
}

private fun DrawScope.drawCompression(
    activity: Float,
    primary: Color,
    accent: Color,
    paper: Color,
    outline: Color,
) {
    val gap = 14f - activity * 10f
    val width = 81f - activity * 9f
    for (index in 2 downTo 0) {
        toolPaper(
            x = 120f - width / 2f + index * 4f,
            y = 28f + index * gap,
            width = width,
            height = 102f - activity * 8f,
            paper = paper,
            outline = if (index == 0) primary.copy(alpha = 0.6f) else outline,
            accent = if (index == 0) primary else accent,
        )
    }
    val arrowInset = activity * 9f
    for (side in listOf(-1f, 1f)) {
        val tail = 120f + side * (99f - arrowInset)
        val tip = 120f + side * (75f - arrowInset)
        drawLine(primary, Offset(tail, 83f), Offset(tip, 83f), 3f, StrokeCap.Round)
        drawLine(primary, Offset(tip + side * 7f, 77f), Offset(tip, 83f), 3f, StrokeCap.Round)
        drawLine(primary, Offset(tip + side * 7f, 89f), Offset(tip, 83f), 3f, StrokeCap.Round)
    }
}

private fun DrawScope.toolPaper(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    paper: Color,
    outline: Color,
    accent: Color,
    picture: Boolean = false,
) {
    val topLeft = Offset(x, y)
    val pageSize = Size(width, height)
    drawRoundRect(outline.copy(alpha = 0.12f), topLeft + Offset(0f, 3f), pageSize, CornerRadius(7f))
    drawRoundRect(paper, topLeft, pageSize, CornerRadius(7f))
    drawRoundRect(outline, topLeft, pageSize, CornerRadius(7f), style = Stroke(1.3f))
    if (picture) {
        val imageTopLeft = topLeft + Offset(10f, 13f)
        val imageSize = Size(width - 20f, height - 35f)
        drawRoundRect(accent.copy(alpha = 0.15f), imageTopLeft, imageSize, CornerRadius(5f))
        drawCircle(accent.copy(alpha = 0.72f), 5f, imageTopLeft + Offset(imageSize.width * 0.72f, 14f))
        val hill = Path().apply {
            moveTo(imageTopLeft.x + 4f, imageTopLeft.y + imageSize.height - 5f)
            lineTo(imageTopLeft.x + imageSize.width * 0.4f, imageTopLeft.y + imageSize.height * 0.46f)
            lineTo(imageTopLeft.x + imageSize.width * 0.62f, imageTopLeft.y + imageSize.height * 0.7f)
            lineTo(imageTopLeft.x + imageSize.width * 0.78f, imageTopLeft.y + imageSize.height * 0.55f)
            lineTo(imageTopLeft.x + imageSize.width - 4f, imageTopLeft.y + imageSize.height - 5f)
            close()
        }
        drawPath(hill, accent.copy(alpha = 0.75f))
        drawLine(
            color = outline,
            start = topLeft + Offset(11f, height - 12f),
            end = topLeft + Offset(width - 12f, height - 12f),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    } else {
        drawRoundRect(accent.copy(alpha = 0.65f), topLeft + Offset(11f, 13f),
            Size(width * 0.35f, 5f), CornerRadius(2.5f))
        for (index in 0..3) {
            val lineY = y + 31f + index * ((height - 40f) / 4f)
            drawLine(outline, Offset(x + 11f, lineY),
                Offset(x + width - if (index == 3) 25f else 11f, lineY),
                2.2f, StrokeCap.Round)
        }
    }
}

@Preview(name = "Extraindo páginas", widthDp = 390, locale = "pt-rBR")
@Composable
private fun ExtractProcessingPreview() {
    ScaniqoTheme { PdfToolProcessingContent(PdfUtility.EXTRACT_PAGES, busy = true) }
}

@Preview(name = "Convertendo imagens", widthDp = 390, locale = "pt-rBR")
@Composable
private fun ImagesProcessingPreview() {
    ScaniqoTheme { PdfToolProcessingContent(PdfUtility.PDF_TO_IMAGES, busy = true) }
}

@Preview(name = "Comprimindo PDF", widthDp = 390, locale = "pt-rBR")
@Composable
private fun CompressionProcessingPreview() {
    ScaniqoTheme { PdfToolProcessingContent(PdfUtility.COMPRESS_PDF, busy = true) }
}
