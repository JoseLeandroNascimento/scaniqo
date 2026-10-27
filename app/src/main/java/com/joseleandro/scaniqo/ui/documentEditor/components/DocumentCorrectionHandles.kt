package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.documentEditor.util.documentCorrectionHandleValue
import kotlin.math.roundToInt


/** As guias discretas continuam visíveis em repouso para indicar o sentido do arraste. */
@Composable
internal fun CorrectionHandlesOverlay(
    value: Float,
    enabled: Boolean,
    curvature: Boolean,
    onChange: (Float) -> Unit,
    onGestureEnd: () -> Unit = {},
) {
    val latestOnGestureEnd by rememberUpdatedState(onGestureEnd)
    var dragging by remember { mutableStateOf(false) }
    val guideAlpha by animateFloatAsState(if (dragging) .72f else .38f,
        animationSpec = tween(160), label = "correction guide")
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onChange)
    val guide = MaterialTheme.colorScheme.primary
    val description = stringResource(if (curvature) R.string.gesture_curvature_handle else R.string.gesture_straighten_handle)
    val valueText = if (curvature) stringResource(R.string.quality_curve_value, (value * 100).roundToInt())
        else stringResource(R.string.quality_angle_value, value)
    val increase = stringResource(if (curvature) R.string.gesture_curve_decrease else R.string.gesture_angle_increase)
    val decrease = stringResource(if (curvature) R.string.gesture_curve_increase else R.string.gesture_angle_decrease)
    val range = if (curvature) -1f..1f else -15f..15f
    val step = if (curvature) .01f else .1f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paperSize = with(LocalDensity.current) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
        Canvas(Modifier.fillMaxSize().pointerInput(curvature, paperSize, enabled) {
            if (!enabled) return@pointerInput
            var gestureValue = latestValue
            detectDragGestures(
                onDragStart = { gestureValue = latestValue; dragging = true },
                onDragEnd = { dragging = false; latestOnGestureEnd() },
                onDragCancel = { dragging = false; latestOnGestureEnd() },
            ) { change, amount ->
                change.consume()
                gestureValue = documentCorrectionHandleValue(gestureValue, amount, paperSize, curvature)
                latestOnChange(gestureValue)
            }
        }) {
            drawRect(guide.copy(alpha = .28f), style = Stroke(1.dp.toPx()))
            if (curvature) {
                val middle = size.height / 2f
                val curve = Path().apply {
                    moveTo(0f, middle)
                    quadraticTo(size.width / 2f, middle - value * size.height * .7f, size.width, middle)
                }
                drawPath(curve, guide.copy(alpha = guideAlpha), style = Stroke(1.dp.toPx()))
            } else {
                // A referência horizontal e a linha inclinada tornam o ajuste visível antes do novo bitmap.
                val center = Offset(size.width / 2f, size.height / 2f)
                drawLine(guide.copy(alpha = .18f), Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
                rotate(value, center) {
                    drawLine(guide.copy(alpha = guideAlpha), Offset(size.width * .1f, center.y), Offset(size.width * .9f, center.y), 1.dp.toPx())
                }
            }
        }
        val handles = if (curvature) listOf(AbsoluteAlignment.CenterLeft, AbsoluteAlignment.CenterRight) else listOf(Alignment.TopCenter)
        handles.forEach { alignment ->
            val x = when (alignment) {
                AbsoluteAlignment.CenterLeft -> (-24).dp
                AbsoluteAlignment.CenterRight -> 24.dp
                else -> 0.dp
            }
            DocumentPreviewHandle(
                iconRes = R.drawable.ic_gesture_vertical,
                iconRotation = if (curvature) 0f else 90f,
                description = description,
                valueDescription = valueText,
                enabled = enabled,
                modifier = Modifier.align(alignment).absoluteOffset(x, if (curvature) 0.dp else (-24).dp)
                    .pointerInput(curvature, paperSize, enabled) {
                        if (!enabled) return@pointerInput
                        var gestureValue = latestValue
                        detectDragGestures(
                            onDragStart = { gestureValue = latestValue; dragging = true },
                            onDragEnd = { dragging = false; latestOnGestureEnd() },
                            onDragCancel = { dragging = false; latestOnGestureEnd() },
                        ) { change, amount ->
                            change.consume()
                            gestureValue = documentCorrectionHandleValue(gestureValue, amount, paperSize, curvature)
                            latestOnChange(gestureValue)
                        }
                    },
                actions = listOf(
                    CustomAccessibilityAction(decrease) {
                        latestOnChange((latestValue - step).coerceIn(range))
                        latestOnGestureEnd()
                        true
                    },
                    CustomAccessibilityAction(increase) {
                        latestOnChange((latestValue + step).coerceIn(range))
                        latestOnGestureEnd()
                        true
                    },
                ),
            )
        }
    }
}
