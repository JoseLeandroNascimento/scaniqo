package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Circular scrubber with the same four orientations supported by the saved page layout. */
@Composable
internal fun DocumentRotationDial(
    rotationQuarterTurns: Int,
    enabled: Boolean,
    onRotationChange: (Int) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    RotationDial(
        rotationDegrees = (((rotationQuarterTurns % 4) + 4) % 4) * 90f,
        enabled = enabled,
        onRotationChange = { onRotationChange((it / 90f).roundToInt()) },
        onGestureStart = onGestureStart,
        onGestureEnd = onGestureEnd,
        description = stringResource(R.string.layout_interactive_rotate),
        leftLabel = stringResource(R.string.layout_interactive_rotate_left),
        rightLabel = stringResource(R.string.layout_interactive_rotate_right),
        modifier = modifier,
        compact = compact,
        rotationStep = 90f,
        buttonStep = 90f,
        progressRange = 0f..270f,
        testTagPrefix = "layout",
    )
}

@Composable
internal fun RotationDial(
    rotationDegrees: Float,
    enabled: Boolean,
    onRotationChange: (Float) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    description: String,
    leftLabel: String,
    rightLabel: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    rotationStep: Float = 1f,
    buttonStep: Float = 15f,
    progressRange: ClosedFloatingPointRange<Float> = -180f..180f,
    testTagPrefix: String = "signature",
) {
    fun normalize(degrees: Float): Float {
        val stepped = (degrees / rotationStep).roundToInt() * rotationStep
        val wrapped = ((stepped % 360f) + 360f) % 360f
        return if (progressRange.start < 0f && wrapped >= 180f) wrapped - 360f else wrapped
    }
    val degrees = normalize(rotationDegrees)
    val currentDegrees by rememberUpdatedState(degrees)
    val currentOnChange by rememberUpdatedState(onRotationChange)
    val currentOnStart by rememberUpdatedState(onGestureStart)
    val currentOnEnd by rememberUpdatedState(onGestureEnd)
    val angleLabel = stringResource(R.string.tools_rotation_angle, degrees.roundToInt())
    val colors = MaterialTheme.colorScheme
    val accent = if (enabled) colors.primary else colors.onSurface.copy(alpha = .38f)
    val track = colors.outlineVariant.copy(alpha = if (enabled) .7f else .3f)
    var dragging by remember { mutableStateOf(false) }
    var targetAngle by remember { mutableFloatStateOf(degrees) }
    LaunchedEffect(degrees) {
        val wrappedAngle = ((targetAngle % 360f) + 360f) % 360f
        val shortestTurn = ((degrees - wrappedAngle + 540f) % 360f) - 180f
        targetAngle += shortestTurn
    }
    val animatedAngle by animateFloatAsState(targetAngle, tween(if (dragging) 0 else 160), label = "rotation_dial")

    fun selectAngle(angle: Float): Boolean {
        if (!enabled || !angle.isFinite()) return false
        val next = normalize(angle)
        if (abs(next - currentDegrees) < .001f) return false
        currentOnStart()
        try {
            currentOnChange(next)
        } finally {
            currentOnEnd()
        }
        return true
    }

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = { selectAngle(currentDegrees - buttonStep) },
            enabled = enabled,
            modifier = Modifier.size(48.dp).testTag("${testTagPrefix}_rotate_counterclockwise"),
        ) {
            Icon(painterResource(R.drawable.ic_layout_rotate_left), leftLabel, Modifier.size(22.dp))
        }
        Box(
            Modifier
                .size(if (compact) 112.dp else 144.dp)
                .testTag("${testTagPrefix}_rotation_dial")
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    stateDescription = angleLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        degrees.coerceIn(progressRange), progressRange,
                        if (rotationStep == 90f) 2 else 0,
                    )
                    if (enabled) {
                        setProgress { degrees ->
                            degrees.isFinite() && selectAngle(degrees.coerceIn(progressRange))
                        }
                        customActions = listOf(
                            CustomAccessibilityAction(leftLabel) { selectAngle(currentDegrees - buttonStep) },
                            CustomAccessibilityAction(rightLabel) { selectAngle(currentDegrees + buttonStep) },
                        )
                    } else {
                        disabled()
                    }
                }
                .onKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionRight, Key.DirectionUp -> selectAngle(currentDegrees + buttonStep)
                        Key.DirectionLeft, Key.DirectionDown -> selectAngle(currentDegrees - buttonStep)
                        Key.MoveHome -> selectAngle(0f)
                        Key.MoveEnd -> selectAngle(progressRange.endInclusive)
                        else -> false
                    }
                }
                .focusable(enabled)
                .pointerInput(enabled, rotationStep, progressRange) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(size.width, size.height) / 2f - 16.dp.toPx()
                        val distance = (down.position - center).getDistance()
                        // Leave the center free so the panel can still be scrolled from there.
                        if (distance < radius - 24.dp.toPx() || distance > radius + 24.dp.toPx()) {
                            return@awaitEachGesture
                        }
                        var lastAngle = currentDegrees
                        fun rotateTo(position: Offset) {
                            val delta = position - center
                            if (delta.getDistance() < radius * .35f) return
                            val degrees = atan2(delta.y, delta.x) * 180f / PI.toFloat() + 90f
                            val next = normalize(degrees)
                            if (abs(next - lastAngle) >= .001f) {
                                lastAngle = next
                                currentOnChange(next)
                            }
                        }
                        down.consume()
                        dragging = true
                        currentOnStart()
                        try {
                            rotateTo(down.position)
                            do {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (change.isConsumed) break
                                rotateTo(change.position)
                                change.consume()
                                if (!change.pressed) break
                            } while (true)
                        } finally {
                            dragging = false
                            currentOnEnd()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f - 16.dp.toPx()
                drawCircle(track, radius, style = Stroke(2.dp.toPx()))
                repeat(24) { tick ->
                    val radians = (tick * 15f - 90f) * PI.toFloat() / 180f
                    val major = tick % 6 == 0
                    val inner = radius - if (major) 8.dp.toPx() else 4.dp.toPx()
                    drawLine(
                        color = if (major) accent.copy(alpha = .55f) else track,
                        start = center + Offset(cos(radians), sin(radians)) * inner,
                        end = center + Offset(cos(radians), sin(radians)) * (radius - 1.dp.toPx()),
                        strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                val radians = (animatedAngle - 90f) * PI.toFloat() / 180f
                val thumbCenter = center + Offset(cos(radians), sin(radians)) * radius
                drawCircle(colors.surfaceContainerLow, 10.dp.toPx(), thumbCenter)
                drawCircle(accent, 7.dp.toPx(), thumbCenter)
                drawCircle(colors.onPrimary.copy(alpha = if (enabled) 1f else .38f), 2.dp.toPx(), thumbCenter)
            }
            Column(
                Modifier.clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Canvas(Modifier.size(28.dp)) {
                    rotate(animatedAngle) {
                        val paper = Size(size.width * .58f, size.height * .76f)
                        val origin = Offset((size.width - paper.width) / 2, (size.height - paper.height) / 2)
                        drawRoundRect(
                            accent.copy(alpha = .1f), origin, paper,
                            CornerRadius(2.dp.toPx()),
                        )
                        drawRoundRect(
                            accent, origin, paper, CornerRadius(2.dp.toPx()),
                            style = Stroke(1.5.dp.toPx()),
                        )
                        repeat(3) { line ->
                            val y = origin.y + paper.height * (.32f + line * .18f)
                            drawLine(
                                accent.copy(alpha = .6f),
                                Offset(origin.x + paper.width * .22f, y),
                                Offset(origin.x + paper.width * (if (line == 2) .58f else .78f), y),
                                1.dp.toPx(), StrokeCap.Round,
                            )
                        }
                    }
                }
                Text(
                    angleLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    maxLines = 1,
                )
            }
        }
        FilledTonalIconButton(
            onClick = { selectAngle(currentDegrees + buttonStep) },
            enabled = enabled,
            modifier = Modifier.size(48.dp).testTag("${testTagPrefix}_rotate_clockwise"),
        ) {
            Icon(painterResource(CoreResourcesR.drawable.ic_layout_rotate_right), rightLabel, Modifier.size(22.dp))
        }
    }
}

@Preview(name = "Girar circular", widthDp = 320, heightDp = 168, locale = "pt-rBR")
@Preview(
    name = "Girar circular escuro", widthDp = 320, heightDp = 168,
    uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "pt-rBR",
)
@Composable
private fun DocumentRotationDialPreview() {
    var rotation by remember { mutableIntStateOf(0) }
    ScaniqoTheme {
        Surface {
            DocumentRotationDial(
                rotationQuarterTurns = rotation,
                enabled = true,
                onRotationChange = { rotation = it },
                onGestureStart = {},
                onGestureEnd = {},
            )
        }
    }
}
