package com.joseleandro.scaniqo.ui.onboarding

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.joseleandro.scaniqo.ui.branding.systemAnimationScale
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun OnboardingIllustration(
    page: Int,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val preview = LocalInspectionMode.current
    val motionAllowed = remember(context, lifecycleState, preview) {
        !preview && systemAnimationScale(context) > 0f
    }
    val moving = animate && motionAllowed && lifecycleState == Lifecycle.State.RESUMED
    val cycle = remember(page) { Animatable(0.25f) }
    LaunchedEffect(moving, page) {
        if (moving) {
            cycle.snapTo(0f)
            cycle.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(5600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            cycle.snapTo(0.25f)
        }
    }
    val scan = painterResource(CoreResourcesR.drawable.ic_document_scan)
    val work = painterResource(CoreResourcesR.drawable.ic_tag_work)
    val study = painterResource(CoreResourcesR.drawable.ic_tag_study)
    val heart = painterResource(CoreResourcesR.drawable.ic_tag_heart)
    val edit = painterResource(CoreResourcesR.drawable.ic_tool_pdf_edit)
    val share = painterResource(CoreResourcesR.drawable.ic_document_share)
    val document = painterResource(CoreResourcesR.drawable.ic_tag_document)
    val check = painterResource(CoreResourcesR.drawable.ic_check_24)
    val signature = remember {
        Path().apply {
            moveTo(104f, 183f)
            cubicTo(114f, 171f, 124f, 154f, 124f, 163f)
            cubicTo(122f, 184f, 112f, 201f, 111f, 190f)
            cubicTo(112f, 179f, 136f, 167f, 133f, 178f)
            cubicTo(129f, 190f, 138f, 183f, 143f, 176f)
            cubicTo(140f, 188f, 151f, 178f, 155f, 176f)
            cubicTo(152f, 189f, 163f, 178f, 166f, 180f)
            cubicTo(172f, 185f, 183f, 177f, 192f, 178f)
            moveTo(119f, 199f)
            quadraticTo(160f, 189f, 194f, 194f)
        }
    }
    val signatureMeasure = remember(signature) { PathMeasure().apply { setPath(signature, false) } }
    Canvas(modifier.size(320.dp, 300.dp).clearAndSetSemantics {}) {
        val drawingScale = minOf(size.width / 320f, size.height / 300f)
        val phase = cycle.value
        val wave = sin(phase * 2f * PI.toFloat())
        translate(
            left = (size.width - 320f * drawingScale) / 2f,
            top = (size.height - 300f * drawingScale) / 2f,
        ) {
            scale(drawingScale, pivot = Offset.Zero) {
                onboardingBackdrop(colors, wave)
                when (page) {
                    0 -> scanningScene(colors, wave, scan)
                    1 -> organizationScene(colors, wave, work, study, heart)
                    2 -> editingScene(colors, phase, moving, signatureMeasure, edit, check)
                    else -> sharingScene(colors, phase, wave, document, share, check)
                }
            }
        }
    }
}

private fun DrawScope.onboardingBackdrop(colors: ColorScheme, wave: Float) {
    drawCircle(colors.primary.copy(alpha = 0.065f), 112f, Offset(160f, 147f))
    drawCircle(colors.tertiary.copy(alpha = 0.055f), 67f, Offset(208f, 174f))
    drawOval(
        colors.primary.copy(alpha = 0.065f),
        Offset(85f + wave * 2f, 266f),
        Size(150f - wave * 4f, 12f),
    )
    onboardingSparkle(Offset(61f, 74f - wave * 3f), 7f, colors.primary.copy(alpha = 0.65f))
    onboardingSparkle(Offset(269f, 217f + wave * 3f), 5f, colors.tertiary.copy(alpha = 0.65f))
    drawCircle(colors.tertiary.copy(alpha = 0.5f), 3f, Offset(255f, 56f + wave * 2f))
    drawCircle(colors.primary.copy(alpha = 0.25f), 2.5f, Offset(48f, 205f - wave * 2f))
}

private fun DrawScope.scanningScene(colors: ColorScheme, wave: Float, scan: Painter) {
    rotate(-4f + wave, Offset(160f, 146f)) {
        onboardingPaper(Offset(93f, 57f), Size(134f, 183f), colors)
        drawRoundRect(
            colors.primaryContainer.copy(alpha = 0.8f),
            Offset(109f, 74f), Size(102f, 73f), CornerRadius(9f),
        )
        drawCircle(colors.primary.copy(alpha = 0.5f), 10f, Offset(184f, 93f))
        val landscape = Path().apply {
            moveTo(114f, 137f)
            lineTo(140f, 106f)
            quadraticTo(143f, 102f, 147f, 107f)
            lineTo(164f, 127f)
            lineTo(176f, 115f)
            quadraticTo(179f, 112f, 183f, 116f)
            lineTo(206f, 137f)
            close()
        }
        drawPath(landscape, colors.primary.copy(alpha = 0.42f))
        onboardingLine(Offset(111f, 166f), 79f, colors.onSurface.copy(alpha = 0.28f), 4f)
        onboardingLine(Offset(111f, 180f), 96f, colors.onSurface.copy(alpha = 0.12f))
        onboardingLine(Offset(111f, 192f), 88f, colors.onSurface.copy(alpha = 0.12f))
        onboardingLine(Offset(111f, 204f), 59f, colors.onSurface.copy(alpha = 0.12f))
        drawCircle(colors.primary.copy(alpha = 0.14f), 4f, Offset(203f, 221f))
    }
    val frame = Path().apply {
        moveTo(94f, 44f); lineTo(78f, 44f); quadraticTo(67f, 44f, 67f, 55f); lineTo(67f, 72f)
        moveTo(226f, 44f); lineTo(242f, 44f); quadraticTo(253f, 44f, 253f, 55f); lineTo(253f, 72f)
        moveTo(67f, 217f); lineTo(67f, 234f); quadraticTo(67f, 245f, 78f, 245f); lineTo(94f, 245f)
        moveTo(253f, 217f); lineTo(253f, 234f); quadraticTo(253f, 245f, 242f, 245f); lineTo(226f, 245f)
    }
    drawPath(frame, colors.primary, style = Stroke(3.5f, cap = StrokeCap.Round))
    val beamY = 145f + wave * 66f
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, colors.primary.copy(alpha = 0.12f)),
            startY = beamY - 31f,
            endY = beamY,
        ),
        Offset(78f, beamY - 31f), Size(164f, 31f),
    )
    drawLine(colors.primary.copy(alpha = 0.8f), Offset(78f, beamY), Offset(242f, beamY), 2f)
    drawCircle(colors.primary, 3.5f, Offset(78f, beamY))
    drawCircle(colors.primary, 3.5f, Offset(242f, beamY))
    onboardingBadge(Offset(229f, 237f), 27f, scan, colors.primary, colors.onPrimary, colors)
}

private fun DrawScope.organizationScene(
    colors: ColorScheme,
    wave: Float,
    work: Painter,
    study: Painter,
    heart: Painter,
) {
    rotate(12f - wave * 2f, Offset(180f, 131f)) {
        onboardingPaper(Offset(120f, 50f), Size(121f, 154f), colors)
        onboardingLine(Offset(137f, 72f), 64f, colors.primary.copy(alpha = 0.4f), 4f)
        onboardingLine(Offset(137f, 86f), 84f, colors.onSurface.copy(alpha = 0.12f))
    }
    rotate(-10f + wave * 2f, Offset(128f, 130f)) {
        onboardingPaper(Offset(70f, 60f), Size(121f, 154f), colors)
        onboardingLine(Offset(87f, 82f), 63f, colors.tertiary.copy(alpha = 0.5f), 4f)
        onboardingLine(Offset(87f, 96f), 84f, colors.onSurface.copy(alpha = 0.12f))
    }
    val folder = Path().apply {
        moveTo(66f, 119f)
        lineTo(66f, 108f)
        quadraticTo(66f, 97f, 78f, 97f)
        lineTo(124f, 97f)
        quadraticTo(131f, 97f, 136f, 105f)
        lineTo(144f, 116f)
        lineTo(245f, 116f)
        quadraticTo(258f, 116f, 258f, 129f)
        lineTo(250f, 237f)
        quadraticTo(249f, 250f, 236f, 250f)
        lineTo(81f, 250f)
        quadraticTo(68f, 250f, 67f, 237f)
        close()
    }
    drawPath(folder, colors.primaryContainer)
    drawPath(folder, colors.primary.copy(alpha = 0.18f), style = Stroke(1.2f))
    val accents = listOf(colors.primary, colors.tertiary, colors.secondary)
    val icons = listOf(work, study, heart)
    for (row in 0..2) {
        val y = 132f + row * 35f + if (row == 0) wave * 3f else 0f
        val x = 80f + if (row == 0) wave * 3f else 0f
        drawRoundRect(colors.onSurface.copy(alpha = 0.035f), Offset(x, y + 3f), Size(162f, 29f), CornerRadius(9f))
        drawRoundRect(colors.surfaceContainerLowest, Offset(x, y), Size(162f, 29f), CornerRadius(9f))
        drawCircle(accents[row].copy(alpha = 0.12f), 10f, Offset(x + 17f, y + 14.5f))
        onboardingGlyph(icons[row], Offset(x + 10f, y + 7.5f), 14f, accents[row])
        onboardingLine(Offset(x + 36f, y + 11f), 57f - row * 7f, colors.onSurface.copy(alpha = 0.3f))
        onboardingLine(Offset(x + 36f, y + 20f), 36f, colors.onSurface.copy(alpha = 0.11f), 2.3f)
        drawRoundRect(accents[row].copy(alpha = 0.12f), Offset(x + 121f, y + 9f), Size(29f, 11f), CornerRadius(5.5f))
    }
    rotate(13f + wave * 3f, Offset(243f, 81f)) {
        drawRoundRect(colors.tertiaryContainer, Offset(211f, 66f - wave * 3f), Size(64f, 28f), CornerRadius(9f))
        drawCircle(colors.tertiary.copy(alpha = 0.5f), 3f, Offset(222f, 80f - wave * 3f))
        onboardingLine(Offset(233f, 80f - wave * 3f), 28f, colors.onTertiaryContainer.copy(alpha = 0.55f), 3f)
    }
}

private fun DrawScope.editingScene(
    colors: ColorScheme,
    phase: Float,
    moving: Boolean,
    signature: PathMeasure,
    edit: Painter,
    check: Painter,
) {
    val progress = if (moving) (phase / 0.66f).coerceIn(0.02f, 1f) else 1f
    val inkAlpha = if (moving && phase > 0.92f) ((1f - phase) / 0.08f).coerceIn(0f, 1f) else 1f
    rotate(-5f, Offset(155f, 148f)) {
        onboardingPaper(Offset(78f, 47f), Size(153f, 200f), colors)
        drawRoundRect(colors.primary.copy(alpha = 0.14f), Offset(96f, 65f), Size(33f, 33f), CornerRadius(9f))
        onboardingGlyph(edit, Offset(103f, 72f), 19f, colors.primary)
        onboardingLine(Offset(143f, 75f), 66f, colors.onSurface.copy(alpha = 0.3f), 4f)
        onboardingLine(Offset(143f, 88f), 43f, colors.onSurface.copy(alpha = 0.12f))
        onboardingLine(Offset(97f, 115f), 111f, colors.onSurface.copy(alpha = 0.12f))
        onboardingLine(Offset(97f, 128f), 98f, colors.onSurface.copy(alpha = 0.12f))
        drawRoundRect(colors.primary.copy(alpha = 0.055f), Offset(95f, 149f), Size(119f, 64f), CornerRadius(9f))
        drawRoundRect(
            colors.primary.copy(alpha = 0.24f),
            Offset(95f, 149f), Size(119f, 64f), CornerRadius(9f),
            style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
        )
        val ink = Path()
        signature.getSegment(0f, signature.length * progress, ink, true)
        drawPath(ink, colors.primary.copy(alpha = inkAlpha), style = Stroke(2.5f, cap = StrokeCap.Round))
        if (progress > 0.95f) {
            drawLine(
                colors.primary.copy(alpha = inkAlpha * 0.6f),
                Offset(117f, 201f), Offset(194f, 194f), 1.5f, StrokeCap.Round,
            )
        }
        val tip = signature.getPosition(signature.length * progress)
        rotate(28f, tip) {
            drawRoundRect(colors.onSurface.copy(alpha = 0.06f), tip + Offset(-2f, -65f), Size(11f, 55f), CornerRadius(4f))
            drawRoundRect(colors.primary, tip + Offset(-5f, -69f), Size(10f, 55f), CornerRadius(4f))
            drawRoundRect(colors.onPrimary.copy(alpha = 0.4f), tip + Offset(-2f, -62f), Size(2f, 37f), CornerRadius(1f))
            val nib = Path().apply {
                moveTo(tip.x - 5f, tip.y - 14f)
                lineTo(tip.x + 5f, tip.y - 14f)
                lineTo(tip.x, tip.y)
                close()
            }
            drawPath(nib, colors.tertiary)
        }
        onboardingLine(Offset(98f, 229f), 53f, colors.onSurface.copy(alpha = 0.13f), 2f)
    }
    onboardingBadge(Offset(244f, 89f), 25f, edit, colors.tertiaryContainer, colors.onTertiaryContainer, colors)
    onboardingBadge(Offset(228f, 236f), 23f, check, colors.primary, colors.onPrimary, colors)
}

private fun DrawScope.sharingScene(
    colors: ColorScheme,
    phase: Float,
    wave: Float,
    document: Painter,
    share: Painter,
    check: Painter,
) {
    val connection = Path().apply {
        moveTo(167f, 221f)
        cubicTo(257f, 237f, 282f, 172f, 228f, 145f)
    }
    drawPath(
        connection, colors.primary.copy(alpha = 0.22f),
        style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 7f))),
    )
    val travel = PathMeasure().apply { setPath(connection, false) }
    val dot = travel.getPosition(travel.length * phase)
    drawCircle(colors.primary.copy(alpha = 0.12f), 8f, dot)
    drawCircle(colors.primary, 3.5f, dot)
    rotate(-9f - wave, Offset(125f, 201f)) {
        onboardingPaper(Offset(74f, 143f + wave * 2f), Size(104f, 121f), colors)
        drawRoundRect(colors.tertiaryContainer, Offset(88f, 160f + wave * 2f), Size(31f, 33f), CornerRadius(8f))
        onboardingGlyph(document, Offset(94f, 166f + wave * 2f), 19f, colors.onTertiaryContainer)
        onboardingLine(Offset(89f, 210f + wave * 2f), 70f, colors.onSurface.copy(alpha = 0.26f), 3.5f)
        onboardingLine(Offset(89f, 223f + wave * 2f), 59f, colors.onSurface.copy(alpha = 0.12f))
        onboardingLine(Offset(89f, 236f + wave * 2f), 43f, colors.onSurface.copy(alpha = 0.12f))
    }
    translate(top = -wave * 4f) {
        val cloud = Path().apply {
            moveTo(114f, 164f)
            cubicTo(91f, 164f, 78f, 149f, 78f, 130f)
            cubicTo(78f, 112f, 91f, 99f, 106f, 98f)
            cubicTo(110f, 74f, 129f, 58f, 152f, 58f)
            cubicTo(176f, 58f, 194f, 73f, 200f, 94f)
            cubicTo(225f, 91f, 246f, 106f, 246f, 130f)
            cubicTo(246f, 150f, 230f, 164f, 211f, 164f)
            close()
        }
        drawPath(cloud, colors.primaryContainer)
        drawPath(cloud, colors.primary.copy(alpha = 0.2f), style = Stroke(1.2f))
        drawCircle(colors.primary.copy(alpha = 0.11f), 25f, Offset(162f, 120f))
        val arrow = Path().apply {
            moveTo(162f, 136f)
            lineTo(162f, 104f)
            moveTo(150f, 116f)
            lineTo(162f, 104f)
            lineTo(174f, 116f)
        }
        drawPath(arrow, colors.onPrimaryContainer, style = Stroke(3.2f, cap = StrokeCap.Round))
        onboardingBadge(Offset(233f, 153f), 19f, check, colors.primary, colors.onPrimary, colors)
    }
    onboardingBadge(Offset(67f, 121f + wave * 3f), 23f, share, colors.surfaceContainerLowest, colors.primary, colors)
}

private fun DrawScope.onboardingPaper(topLeft: Offset, paperSize: Size, colors: ColorScheme) {
    drawRoundRect(colors.onSurface.copy(alpha = 0.035f), topLeft + Offset(0f, 6f), paperSize, CornerRadius(15f))
    drawRoundRect(colors.surfaceContainerLowest, topLeft, paperSize, CornerRadius(15f))
    drawRoundRect(colors.outlineVariant.copy(alpha = 0.65f), topLeft, paperSize, CornerRadius(15f), style = Stroke(1f))
}

private fun DrawScope.onboardingBadge(
    center: Offset,
    radius: Float,
    icon: Painter,
    background: Color,
    foreground: Color,
    colors: ColorScheme,
) {
    drawCircle(colors.onSurface.copy(alpha = 0.05f), radius + 1f, center + Offset(0f, 4f))
    drawCircle(colors.surface, radius + 4f, center)
    drawCircle(background, radius, center)
    onboardingGlyph(icon, center - Offset(radius * 0.49f, radius * 0.49f), radius * 0.98f, foreground)
}

private fun DrawScope.onboardingGlyph(painter: Painter, topLeft: Offset, side: Float, color: Color) {
    translate(topLeft.x, topLeft.y) {
        with(painter) { draw(Size(side, side), colorFilter = ColorFilter.tint(color)) }
    }
}

private fun DrawScope.onboardingLine(start: Offset, length: Float, color: Color, width: Float = 3f) {
    drawLine(color, start, start + Offset(length, 0f), width, StrokeCap.Round)
}

private fun DrawScope.onboardingSparkle(center: Offset, radius: Float, color: Color) {
    val shape = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + radius * 0.2f, center.y - radius * 0.2f, center.x + radius, center.y)
        quadraticTo(center.x + radius * 0.2f, center.y + radius * 0.2f, center.x, center.y + radius)
        quadraticTo(center.x - radius * 0.2f, center.y + radius * 0.2f, center.x - radius, center.y)
        quadraticTo(center.x - radius * 0.2f, center.y - radius * 0.2f, center.x, center.y - radius)
        close()
    }
    drawPath(shape, color)
}
