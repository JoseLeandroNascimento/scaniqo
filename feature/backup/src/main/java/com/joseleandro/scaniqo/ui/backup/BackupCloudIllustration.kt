package com.joseleandro.scaniqo.ui.backup

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
internal fun BackupCloudIllustration(
    isWorking: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "backup cloud")
    val cycle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isWorking) 2200 else 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cloud floating",
    )
    val activity = animateFloatAsState(
        targetValue = if (isWorking) 1f else 0f,
        animationSpec = tween(350),
        label = "backup activity",
    )
    val cloud = remember {
        Path().apply {
            moveTo(66f, 90f)
            cubicTo(54f, 90f, 46f, 82f, 46f, 71f)
            cubicTo(46f, 61f, 53f, 53f, 63f, 52f)
            cubicTo(65f, 38f, 77f, 28f, 91f, 28f)
            cubicTo(105f, 28f, 117f, 37f, 121f, 50f)
            cubicTo(134f, 49f, 145f, 58f, 145f, 71f)
            cubicTo(145f, 82f, 136f, 90f, 125f, 90f)
            close()
        }
    }
    Canvas(modifier.size(width = 200.dp, height = 116.dp)) {
        val drawingScale = minOf(size.width / 200f, size.height / 116f)
        val wave = sin(cycle.value * 2f * PI.toFloat())
        val lift = wave * (2.5f + activity.value * 2f)
        translate(
            left = (size.width - 200f * drawingScale) / 2f,
            top = (size.height - 116f * drawingScale) / 2f,
        ) {
            scale(drawingScale, pivot = Offset.Zero) {
                drawCircle(
                    color = colors.primary.copy(alpha = 0.055f),
                    radius = 48f,
                    center = Offset(98f, 56f),
                )
                drawCircle(
                    color = colors.tertiary.copy(alpha = 0.06f),
                    radius = 27f,
                    center = Offset(140f, 63f),
                )
                drawOval(
                    color = colors.primary.copy(alpha = 0.07f),
                    topLeft = Offset(65f + wave, 101f),
                    size = Size(76f - wave * 2f, 7f),
                )
                backupDocument(
                    center = Offset(136f, 57f - lift),
                    rotation = 12f + wave * (2f + activity.value * 2f),
                    paper = colors.surfaceContainerLowest,
                    outline = colors.outlineVariant,
                    accent = colors.tertiary,
                    shadow = colors.onSurface.copy(alpha = 0.04f),
                )
                translate(top = lift) {
                    drawPath(path = cloud, color = colors.primaryContainer)
                    drawPath(
                        path = cloud,
                        color = colors.primary.copy(alpha = 0.16f),
                        style = Stroke(width = 1f),
                    )
                    drawCircle(
                        color = colors.onPrimaryContainer,
                        radius = 2.2f,
                        center = Offset(84f, 64f),
                    )
                    drawCircle(
                        color = colors.onPrimaryContainer,
                        radius = 2.2f,
                        center = Offset(105f, 64f),
                    )
                    drawArc(
                        color = colors.onPrimaryContainer,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(88f, 66f),
                        size = Size(13f, 10f),
                        style = Stroke(width = 2f, cap = StrokeCap.Round),
                    )
                    drawOval(
                        color = colors.tertiary.copy(alpha = 0.18f),
                        topLeft = Offset(73f, 69f),
                        size = Size(9f, 4f),
                    )
                    drawOval(
                        color = colors.tertiary.copy(alpha = 0.18f),
                        topLeft = Offset(107f, 69f),
                        size = Size(9f, 4f),
                    )
                }
                backupSparkle(
                    center = Offset(39f, 42f - lift),
                    radius = 4.5f + wave * 0.5f,
                    color = colors.primary.copy(alpha = 0.6f),
                )
                backupSparkle(
                    center = Offset(159f, 91f + lift),
                    radius = 3.5f,
                    color = colors.tertiary.copy(alpha = 0.65f),
                )
                drawCircle(
                    color = colors.tertiary.copy(alpha = 0.4f),
                    radius = 2.5f,
                    center = Offset(164f, 29f + lift),
                )
                drawCircle(
                    color = colors.primary.copy(alpha = 0.3f),
                    radius = 2f,
                    center = Offset(38f, 86f - lift),
                )
            }
        }
    }
}

private fun DrawScope.backupDocument(
    center: Offset,
    rotation: Float,
    paper: Color,
    outline: Color,
    accent: Color,
    shadow: Color,
) {
    rotate(rotation, pivot = center) {
        val topLeft = center - Offset(19f, 27f)
        drawRoundRect(
            color = shadow,
            topLeft = topLeft + Offset(0f, 3f),
            size = Size(38f, 54f),
            cornerRadius = CornerRadius(7f),
        )
        drawRoundRect(
            color = paper,
            topLeft = topLeft,
            size = Size(38f, 54f),
            cornerRadius = CornerRadius(7f),
        )
        drawRoundRect(
            color = outline,
            topLeft = topLeft,
            size = Size(38f, 54f),
            cornerRadius = CornerRadius(7f),
            style = Stroke(width = 1f),
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.16f),
            topLeft = topLeft + Offset(8f, 9f),
            size = Size(13f, 12f),
            cornerRadius = CornerRadius(3f),
        )
        for (line in 0..2) {
            val start = topLeft + Offset(8f, 29f + line * 7f)
            drawLine(
                color = accent.copy(alpha = if (line == 0) 0.45f else 0.24f),
                start = start,
                end = start + Offset(if (line == 2) 13f else 22f, 0f),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.backupSparkle(center: Offset, radius: Float, color: Color) {
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
