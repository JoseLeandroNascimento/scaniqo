package com.joseleandro.scaniqo.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

enum class EmptyStateIllustration {
    DOCUMENTS, FILTERED_DOCUMENTS, TAGS, SIGNATURES, SEARCH, NO_RESULTS,
    TRASH, MERGE, CERTIFICATES, BACKUP,
}

@Composable
internal fun EmptyStateArtwork(
    illustration: EmptyStateIllustration,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    if (illustration == EmptyStateIllustration.DOCUMENTS ||
        illustration == EmptyStateIllustration.FILTERED_DOCUMENTS
    ) {
        DocumentEmptyArtwork(modifier)
        return
    }
    Canvas(modifier.size(width = 160.dp, height = 112.dp).clearAndSetSemantics {}) {
        withTransform({
            scale(size.width / 160f, size.height / 112f, pivot = Offset.Zero)
        }) {
            drawOval(colors.secondaryContainer.copy(alpha = .45f), Offset(15f, 13f), Size(130f, 89f))
            drawOval(colors.onSurface.copy(alpha = .06f), Offset(29f, 96f), Size(102f, 6f))
            when (illustration) {
                EmptyStateIllustration.DOCUMENTS,
                EmptyStateIllustration.FILTERED_DOCUMENTS -> Unit
                EmptyStateIllustration.TAGS -> {
                    rotate(-20f, Offset(65f, 59f)) {
                        tag(38f, 22f, colors.secondaryContainer, colors.onSecondaryContainer, 1.2f)
                    }
                    rotate(16f, Offset(98f, 61f)) {
                        tag(74f, 25f, colors.tertiaryContainer, colors.onTertiaryContainer, 1.2f)
                    }
                    drawCircle(colors.primary.copy(alpha = .35f), 4f, Offset(31f, 83f))
                    drawCircle(colors.tertiary.copy(alpha = .5f), 3f, Offset(130f, 31f))
                }
                EmptyStateIllustration.SIGNATURES -> {
                    rotate(-7f, Offset(77f, 55f)) { paper(38f, 14f, 76f, 82f, colors) }
                    val signature = Path().apply {
                        moveTo(51f, 73f)
                        cubicTo(82f, 43f, 57f, 43f, 60f, 73f)
                        cubicTo(63f, 87f, 77f, 56f, 79f, 69f)
                        cubicTo(83f, 82f, 86f, 61f, 93f, 69f)
                    }
                    drawPath(signature, colors.primary, style = Stroke(2.6f, cap = StrokeCap.Round))
                    drawLine(colors.primary.copy(alpha = .4f), Offset(50f, 82f), Offset(93f, 82f), 1.5f, StrokeCap.Round)
                    rotate(36f, Offset(117f, 55f)) {
                        drawRoundRect(colors.tertiaryContainer, Offset(111f, 22f), Size(12f, 57f), CornerRadius(3f))
                        drawRoundRect(colors.onTertiaryContainer, Offset(111f, 22f), Size(12f, 57f), CornerRadius(3f),
                            style = Stroke(2f))
                        drawPath(Path().apply {
                            moveTo(111f, 79f); lineTo(117f, 90f); lineTo(123f, 79f); close()
                        }, colors.onTertiaryContainer)
                        drawLine(colors.onTertiaryContainer, Offset(111f, 33f), Offset(123f, 33f), 2f)
                    }
                }
                EmptyStateIllustration.SEARCH -> {
                    paper(34f, 13f, 68f, 79f, colors)
                    magnifier(107f, 68f, colors)
                }
                EmptyStateIllustration.NO_RESULTS -> {
                    drawRoundRect(colors.surface, Offset(26f, 26f), Size(79f, 62f), CornerRadius(10f))
                    drawRoundRect(colors.outlineVariant, Offset(26f, 26f), Size(79f, 62f), CornerRadius(10f),
                        style = Stroke(2f))
                    repeat(3) { index ->
                        drawLine(colors.outlineVariant, Offset(40f + index * 18, 46f), Offset(49f + index * 18, 46f), 2f, StrokeCap.Round)
                    }
                    drawLine(colors.outlineVariant, Offset(40f, 62f), Offset(67f, 62f), 2f, StrokeCap.Round)
                    magnifier(104f, 70f, colors)
                }
                EmptyStateIllustration.TRASH -> {
                    drawPath(Path().apply {
                        moveTo(48f, 39f); lineTo(110f, 39f); lineTo(104f, 94f)
                        lineTo(55f, 94f); close()
                    }, colors.secondaryContainer)
                    drawPath(Path().apply {
                        moveTo(48f, 39f); lineTo(110f, 39f); lineTo(104f, 94f)
                        lineTo(55f, 94f); close()
                    }, colors.onSecondaryContainer, style = Stroke(2f, join = StrokeJoin.Round))
                    rotate(-9f, Offset(80f, 29f)) {
                        drawRoundRect(colors.surface, Offset(44f, 25f), Size(71f, 10f), CornerRadius(3f))
                        drawRoundRect(colors.onSecondaryContainer, Offset(44f, 25f), Size(71f, 10f), CornerRadius(3f),
                            style = Stroke(2f))
                        drawRoundRect(colors.onSecondaryContainer, Offset(69f, 18f), Size(22f, 7f), CornerRadius(3f),
                            style = Stroke(2f))
                    }
                    drawLine(colors.onSecondaryContainer.copy(alpha = .35f), Offset(68f, 52f), Offset(70f, 82f), 2f, StrokeCap.Round)
                    drawLine(colors.onSecondaryContainer.copy(alpha = .35f), Offset(91f, 52f), Offset(89f, 82f), 2f, StrokeCap.Round)
                    checkBadge(117f, 82f, colors)
                }
                EmptyStateIllustration.MERGE -> {
                    rotate(-10f, Offset(56f, 52f)) { paper(30f, 15f, 51f, 70f, colors) }
                    rotate(9f, Offset(107f, 56f)) { paper(80f, 22f, 51f, 70f, colors) }
                    drawCircle(colors.tertiaryContainer, 16f, Offset(80f, 81f))
                    drawLine(colors.onTertiaryContainer, Offset(72f, 81f), Offset(88f, 81f), 2.5f, StrokeCap.Round)
                    drawLine(colors.onTertiaryContainer, Offset(80f, 73f), Offset(80f, 89f), 2.5f, StrokeCap.Round)
                }
                EmptyStateIllustration.CERTIFICATES -> {
                    paper(35f, 17f, 85f, 72f, colors)
                    val ribbon = Path().apply {
                        moveTo(92f, 76f); lineTo(87f, 102f); lineTo(99f, 96f)
                        lineTo(108f, 103f); lineTo(112f, 76f); close()
                    }
                    drawPath(ribbon, colors.tertiaryContainer)
                    drawCircle(colors.secondaryContainer, 18f, Offset(102f, 72f))
                    drawCircle(colors.primary, 18f, Offset(102f, 72f), style = Stroke(2.5f))
                    drawCircle(colors.primary.copy(alpha = .3f), 11f, Offset(102f, 72f), style = Stroke(1.5f))
                    drawPath(Path().apply {
                        moveTo(96f, 72f); lineTo(100f, 76f); lineTo(108f, 68f)
                    }, colors.primary, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                EmptyStateIllustration.BACKUP -> {
                    paper(35f, 17f, 67f, 76f, colors)
                    drawCircle(colors.tertiaryContainer, 26f, Offset(110f, 72f))
                    drawCircle(colors.onTertiaryContainer, 26f, Offset(110f, 72f), style = Stroke(2.5f))
                    drawLine(colors.onTertiaryContainer, Offset(110f, 72f), Offset(110f, 57f), 2.5f, StrokeCap.Round)
                    drawLine(colors.onTertiaryContainer, Offset(110f, 72f), Offset(121f, 78f), 2.5f, StrokeCap.Round)
                    drawCircle(colors.onTertiaryContainer, 2.5f, Offset(110f, 72f))
                }
            }
        }
    }
}

@Composable
private fun DocumentEmptyArtwork(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.size(width = 160.dp, height = 112.dp).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(CoreResourcesR.drawable.ic_tool_pages),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colors.primary,
                )
            }
        }
    }
}

private fun DrawScope.paper(x: Float, y: Float, width: Float, height: Float, colors: ColorScheme) {
    val fold = 13f
    val outline = Path().apply {
        moveTo(x + 5f, y); lineTo(x + width - fold, y); lineTo(x + width, y + fold)
        lineTo(x + width, y + height - 4f)
        quadraticTo(x + width, y + height, x + width - 4f, y + height)
        lineTo(x + 4f, y + height)
        quadraticTo(x, y + height, x, y + height - 4f)
        lineTo(x, y + 5f); quadraticTo(x, y, x + 5f, y); close()
    }
    drawPath(outline, colors.surface)
    drawPath(outline, colors.outlineVariant, style = Stroke(2f, join = StrokeJoin.Round))
    drawPath(Path().apply {
        moveTo(x + width - fold, y); lineTo(x + width - fold, y + fold)
        lineTo(x + width, y + fold); close()
    }, colors.secondaryContainer)
    repeat(3) { index ->
        val lineY = y + 26f + index * 9f
        drawLine(colors.primary.copy(alpha = if (index == 0) .6f else .22f),
            Offset(x + 12f, lineY), Offset(x + width - if (index == 2) 22f else 12f, lineY),
            2.5f, StrokeCap.Round)
    }
}

private fun DrawScope.tag(x: Float, y: Float, fill: Color, outline: Color, scale: Float = 1f) {
    withTransform({ translate(x, y); scale(scale, scale, Offset.Zero) }) {
        val tag = Path().apply {
            moveTo(0f, 16f); lineTo(18f, 0f); lineTo(36f, 16f)
            lineTo(36f, 56f); lineTo(0f, 56f); close()
        }
        drawPath(tag, fill)
        drawPath(tag, outline.copy(alpha = .65f), style = Stroke(2f, join = StrokeJoin.Round))
        drawCircle(outline, 3f, Offset(18f, 16f))
        drawLine(outline.copy(alpha = .5f), Offset(11f, 35f), Offset(25f, 35f), 2f, StrokeCap.Round)
        drawLine(outline.copy(alpha = .3f), Offset(11f, 43f), Offset(21f, 43f), 2f, StrokeCap.Round)
    }
}

private fun DrawScope.magnifier(x: Float, y: Float, colors: ColorScheme) {
    drawCircle(colors.secondaryContainer, 22f, Offset(x, y))
    drawCircle(colors.primary, 22f, Offset(x, y), style = Stroke(3f))
    drawLine(colors.primary, Offset(x + 16f, y + 16f), Offset(x + 31f, y + 31f), 7f, StrokeCap.Round)
    drawArc(colors.surface.copy(alpha = .8f), 210f, 65f, false,
        Offset(x - 13f, y - 13f), Size(26f, 26f), style = Stroke(2.5f, cap = StrokeCap.Round))
}

private fun DrawScope.checkBadge(x: Float, y: Float, colors: ColorScheme) {
    drawCircle(colors.tertiaryContainer, 15f, Offset(x, y))
    drawPath(Path().apply {
        moveTo(x - 6f, y); lineTo(x - 1f, y + 5f); lineTo(x + 7f, y - 5f)
    }, colors.onTertiaryContainer, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Preview(name = "Documentos vazios claro", widthDp = 160, heightDp = 112)
@Preview(
    name = "Documentos vazios escuro",
    widthDp = 160,
    heightDp = 112,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DocumentEmptyArtworkPreview() {
    ScaniqoTheme {
        Surface { DocumentEmptyArtwork() }
    }
}

@Preview(name = "Ilustrações por contexto", widthDp = 360, heightDp = 600)
@Composable
private fun EmptyStateArtworkPreview() {
    ScaniqoTheme {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EmptyStateIllustration.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { EmptyStateArtwork(it) }
                }
            }
        }
    }
}
