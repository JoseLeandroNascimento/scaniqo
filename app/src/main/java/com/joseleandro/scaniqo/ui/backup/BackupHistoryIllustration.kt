package com.joseleandro.scaniqo.ui.backup

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.PathEffect
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
internal fun BackupHistoryIllustration(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "backup history")
    val cycle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "history floating",
    )
    val trail = remember {
        Path().apply {
            moveTo(43f, 78f)
            cubicTo(28f, 33f, 67f, 19f, 100f, 39f)
            cubicTo(134f, 60f, 161f, 70f, 174f, 36f)
        }
    }
    val trailStroke = remember {
        Stroke(width = 1.8f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f)))
    }
    Canvas(modifier.size(width = 216.dp, height = 124.dp)) {
        val drawingScale = minOf(size.width / 216f, size.height / 124f)
        val wave = sin(cycle.value * 2f * PI.toFloat())
        translate(
            left = (size.width - 216f * drawingScale) / 2f,
            top = (size.height - 124f * drawingScale) / 2f,
        ) {
            scale(drawingScale, pivot = Offset.Zero) {
                drawCircle(colors.primary.copy(alpha = 0.055f), radius = 45f, center = Offset(105f, 59f))
                drawCircle(colors.tertiary.copy(alpha = 0.045f), radius = 25f, center = Offset(143f, 84f))
                drawPath(trail, color = colors.primary.copy(alpha = 0.26f), style = trailStroke)
                for (point in listOf(Offset(43f, 78f), Offset(174f, 36f))) {
                    drawCircle(colors.surface, radius = 6f, center = point)
                    drawCircle(colors.primary.copy(alpha = 0.25f), radius = 6f, center = point, style = Stroke(1.8f))
                    drawCircle(colors.primary.copy(alpha = 0.12f), radius = 2f, center = point)
                }
                drawOval(
                    color = colors.primary.copy(alpha = 0.065f),
                    topLeft = Offset(78f + wave, 111f),
                    size = Size(71f - wave * 2f, 6f),
                )
                translate(top = wave * 2.5f) {
                    historyPaper(
                        paper = colors.surfaceContainerLowest,
                        ink = colors.onSurface,
                        accent = colors.primary,
                        tint = colors.primaryContainer,
                        blush = colors.tertiary,
                        rotation = -8f + wave * 1.5f,
                    )
                    historyClock(
                        center = Offset(142f, 88f),
                        face = colors.tertiaryContainer,
                        ink = colors.onTertiaryContainer,
                        outline = colors.surfaceContainerLowest,
                    )
                }
                historyTwinkle(
                    center = Offset(65f, 23f - wave * 2f),
                    radius = 4.5f + wave * 0.6f,
                    color = colors.tertiary.copy(alpha = 0.55f + wave * 0.15f),
                )
                historyTwinkle(
                    center = Offset(175f, 91f + wave),
                    radius = 4f - wave * 0.5f,
                    color = colors.primary.copy(alpha = 0.5f - wave * 0.15f),
                )
                drawCircle(colors.tertiary.copy(alpha = 0.35f), radius = 2f, center = Offset(58f, 101f - wave))
            }
        }
    }
}

private fun DrawScope.historyPaper(
    paper: Color,
    ink: Color,
    accent: Color,
    tint: Color,
    blush: Color,
    rotation: Float,
) {
    rotate(rotation, pivot = Offset(109f, 61f)) {
        val topLeft = Offset(81f, 23f)
        val paperSize = Size(56f, 76f)
        drawRoundRect(ink.copy(alpha = 0.035f), topLeft + Offset(0f, 3f), paperSize, CornerRadius(10f))
        drawRoundRect(paper, topLeft, paperSize, CornerRadius(10f))
        drawRoundRect(accent.copy(alpha = 0.2f), topLeft, paperSize, CornerRadius(10f), style = Stroke(1.3f))
        drawRoundRect(tint, topLeft + Offset(11f, 11f), Size(23f, 7f), CornerRadius(3.5f))
        drawCircle(ink, radius = 2f, center = Offset(100f, 57f))
        drawCircle(ink, radius = 2f, center = Offset(118f, 57f))
        drawArc(
            color = ink,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(104f, 59f),
            size = Size(10f, 8f),
            style = Stroke(1.8f, cap = StrokeCap.Round),
        )
        drawOval(blush.copy(alpha = 0.2f), Offset(92f, 62f), Size(7f, 3.5f))
        drawOval(blush.copy(alpha = 0.2f), Offset(119f, 62f), Size(7f, 3.5f))
        drawLine(accent.copy(alpha = 0.25f), Offset(94f, 79f), Offset(121f, 79f), 2.5f, StrokeCap.Round)
        drawLine(accent.copy(alpha = 0.15f), Offset(94f, 86f), Offset(112f, 86f), 2.5f, StrokeCap.Round)
    }
}

private fun DrawScope.historyClock(center: Offset, face: Color, ink: Color, outline: Color) {
    drawCircle(outline, radius = 20f, center = center)
    drawCircle(face, radius = 17f, center = center)
    drawCircle(ink.copy(alpha = 0.18f), radius = 17f, center = center, style = Stroke(1f))
    drawLine(ink, center, center + Offset(-5f, -4f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(ink, center, center + Offset(6f, -7f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawCircle(ink, radius = 1.8f, center = center)
    drawCircle(ink.copy(alpha = 0.25f), radius = 1f, center = center + Offset(0f, -12f))
    drawCircle(ink.copy(alpha = 0.25f), radius = 1f, center = center + Offset(12f, 0f))
    drawCircle(ink.copy(alpha = 0.25f), radius = 1f, center = center + Offset(0f, 12f))
    drawCircle(ink.copy(alpha = 0.25f), radius = 1f, center = center + Offset(-12f, 0f))
}

private fun DrawScope.historyTwinkle(center: Offset, radius: Float, color: Color) {
    drawLine(
        color = color,
        start = center - Offset(0f, radius),
        end = center + Offset(0f, radius),
        strokeWidth = 2f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = center - Offset(radius, 0f),
        end = center + Offset(radius, 0f),
        strokeWidth = 2f,
        cap = StrokeCap.Round,
    )
}
