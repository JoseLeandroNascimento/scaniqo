package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun DocumentMergeIllustration(
    selectedCount: Int,
    isWorking: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val selection = animateFloatAsState(
        targetValue = selectedCount.coerceIn(0, 2).toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "merge selection",
    )
    val transition = rememberInfiniteTransition(label = "document merge")
    val cycle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isWorking) 2400 else 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "floating pages",
    )
    val working = animateFloatAsState(
        targetValue = if (isWorking) 1f else 0f,
        animationSpec = tween(300),
        label = "merge activity",
    )
    Canvas(modifier.size(width = 230.dp, height = 132.dp)) {
        val drawingScale = minOf(size.width / 230f, size.height / 132f)
        val selected = selection.value.coerceIn(0f, 2.25f)
        val phase = cycle.value
        val float = sin(phase * 2f * PI.toFloat())
        val convergence = (when {
            phase < 0.55f -> FastOutSlowInEasing.transform(phase / 0.55f)
            phase < 0.8f -> 1f
            else -> 1f - FastOutSlowInEasing.transform((phase - 0.8f) / 0.2f)
        }) * working.value
        val spread = (52f - selected * 5f) * (1f - convergence)
        val lift = float * 3f * (1f - working.value)
        val leftAlpha = (0.48f + selected.coerceAtMost(1f) * 0.52f).coerceIn(0f, 1f)
        val rightAlpha = (0.48f + (selected - 1f).coerceIn(0f, 1f) * 0.52f)
        translate(
            left = (size.width - 230f * drawingScale) / 2f,
            top = (size.height - 132f * drawingScale) / 2f,
        ) {
            scale(drawingScale, pivot = Offset.Zero) {
                drawCircle(
                    color = colors.primary.copy(alpha = 0.055f),
                    radius = 60f + convergence * 3f,
                    center = Offset(115f, 66f),
                )
                drawCircle(
                    color = colors.tertiary.copy(alpha = 0.06f),
                    radius = 35f,
                    center = Offset(157f, 76f),
                )
                drawOval(
                    color = colors.primary.copy(alpha = 0.08f),
                    topLeft = Offset(68f + convergence * 12f, 108f),
                    size = Size(94f - convergence * 24f, 9f),
                )
                mergePage(
                    center = Offset(115f + spread, 64f - lift),
                    rotation = (14f - float * 2f) * (1f - convergence),
                    paper = colors.surface,
                    outline = colors.outlineVariant.copy(alpha = rightAlpha),
                    accent = colors.tertiary.copy(alpha = rightAlpha),
                    shadow = colors.onSurface.copy(alpha = 0.04f),
                )
                mergePage(
                    center = Offset(115f - spread, 64f + lift),
                    rotation = (-14f + float * 2f) * (1f - convergence),
                    paper = colors.surface,
                    outline = colors.outlineVariant.copy(alpha = leftAlpha),
                    accent = colors.primary.copy(alpha = leftAlpha),
                    shadow = colors.onSurface.copy(alpha = 0.04f),
                )
                mergePage(
                    center = Offset(115f, 72f - convergence * 3f),
                    rotation = 0f,
                    paper = colors.surfaceContainerLowest,
                    outline = colors.primary.copy(alpha = 0.22f),
                    accent = colors.primary,
                    shadow = colors.onSurface.copy(alpha = 0.06f),
                )
                val sparkleAlpha = 0.35f + convergence * 0.65f
                mergeSparkle(
                    center = Offset(185f, 28f - lift),
                    radius = 4f + convergence * 2f,
                    color = colors.tertiary.copy(alpha = sparkleAlpha),
                )
                mergeSparkle(
                    center = Offset(44f, 91f + lift),
                    radius = 3f,
                    color = colors.primary.copy(alpha = sparkleAlpha * 0.7f),
                )
                drawCircle(
                    color = colors.primary.copy(alpha = 0.25f),
                    radius = 2f,
                    center = Offset(69f, 24f + lift),
                )
                drawCircle(
                    color = colors.tertiary.copy(alpha = 0.35f),
                    radius = 2.5f,
                    center = Offset(181f, 101f - lift),
                )
            }
        }
    }
}

private fun DrawScope.mergePage(
    center: Offset,
    rotation: Float,
    paper: Color,
    outline: Color,
    accent: Color,
    shadow: Color,
) {
    rotate(rotation, pivot = center) {
        val topLeft = center - Offset(25f, 34f)
        drawRoundRect(
            color = shadow,
            topLeft = topLeft + Offset(0f, 3f),
            size = Size(50f, 68f),
            cornerRadius = CornerRadius(8f),
        )
        drawRoundRect(
            color = paper,
            topLeft = topLeft,
            size = Size(50f, 68f),
            cornerRadius = CornerRadius(8f),
        )
        drawRoundRect(
            color = outline,
            topLeft = topLeft,
            size = Size(50f, 68f),
            cornerRadius = CornerRadius(8f),
            style = Stroke(width = 1f),
        )
        drawRoundRect(
            color = accent.copy(alpha = accent.alpha * 0.13f),
            topLeft = topLeft + Offset(10f, 11f),
            size = Size(17f, 17f),
            cornerRadius = CornerRadius(5f),
        )
        val mark = topLeft + Offset(18.5f, 19.5f)
        drawLine(accent, mark - Offset(3f, 0f), mark + Offset(3f, 0f), 1.8f, StrokeCap.Round)
        drawLine(accent, mark - Offset(0f, 3f), mark + Offset(0f, 3f), 1.8f, StrokeCap.Round)
        for (line in 0..2) {
            val start = topLeft + Offset(11f, 39f + line * 8f)
            drawLine(
                color = accent.copy(alpha = accent.alpha * if (line == 0) 0.45f else 0.2f),
                start = start,
                end = start + Offset(if (line == 2) 17f else 28f, 0f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.mergeSparkle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + radius * 0.2f, center.y - radius * 0.2f, center.x + radius, center.y)
        quadraticTo(center.x + radius * 0.2f, center.y + radius * 0.2f, center.x, center.y + radius)
        quadraticTo(center.x - radius * 0.2f, center.y + radius * 0.2f, center.x - radius, center.y)
        quadraticTo(center.x - radius * 0.2f, center.y - radius * 0.2f, center.x, center.y - radius)
        close()
    }
    drawPath(path = path, color = color)
}
