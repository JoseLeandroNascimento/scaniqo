package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.effectiveContentPosition
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLayoutControl
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLayoutHandle
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutGestures
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutHandleGesture
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutRotation
import kotlin.math.roundToInt

/** Os controles ficam nas bordas; o documento continua visível durante todo o gesto. */
@Composable
internal fun DocumentLayoutHandles(
    pageId: String,
    control: DocumentLayoutControl,
    layout: PdfPageLayout,
    enabled: Boolean,
    sourceWidth: Float,
    sourceHeight: Float,
    onLayoutChange: (PdfPageLayout) -> Unit,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
) {
    val guide = MaterialTheme.colorScheme.primary
    var gestureActive by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    fun startGesture() { gestureActive = true; onGestureStart() }
    fun endGesture() { gestureActive = false; onGestureEnd() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paperSize = with(LocalDensity.current) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
        Canvas(Modifier.fillMaxSize().documentLayoutGestures(
            pageId, control, layout, sourceWidth, sourceHeight, enabled, onLayoutChange,
            ::startGesture, ::endGesture,
        )) {
            if (enabled) {
                drawRect(guide.copy(alpha = .28f), style = Stroke(1.dp.toPx()))
                if (control == DocumentLayoutControl.POSITION && gestureActive) {
                    val inset = 2.dp.toPx()
                    val right = size.width - inset
                    val bottom = size.height - inset
                    val position = layout.effectiveContentPosition()
                    if (position.vertical <= .01f) drawLine(guide, Offset(inset, inset), Offset(right, inset), 2.dp.toPx())
                    if (position.vertical >= .99f) drawLine(guide, Offset(inset, bottom), Offset(right, bottom), 2.dp.toPx())
                    if (position.horizontal <= .01f) drawLine(guide, Offset(inset, inset), Offset(inset, bottom), 2.dp.toPx())
                    if (position.horizontal >= .99f) drawLine(guide, Offset(right, inset), Offset(right, bottom), 2.dp.toPx())
                    if (position.horizontal in .49f.. .51f) drawLine(
                        guide.copy(alpha = .22f), Offset(size.width / 2, 0f),
                        Offset(size.width / 2, size.height), 1.dp.toPx(),
                    )
                    if (position.vertical in .49f.. .51f) drawLine(
                        guide.copy(alpha = .22f), Offset(0f, size.height / 2),
                        Offset(size.width, size.height / 2), 1.dp.toPx(),
                    )
                }
            }
        }
        when (control) {
            DocumentLayoutControl.POSITION -> {
                val horizontalNames = listOf(
                    stringResource(R.string.tools_align_left),
                    stringResource(R.string.tools_align_center),
                    stringResource(R.string.tools_align_right),
                )
                val verticalNames = listOf(
                    stringResource(R.string.tools_align_top),
                    stringResource(R.string.tools_align_center),
                    stringResource(R.string.tools_align_bottom),
                )
                val fractions = listOf(0f, .5f, 1f)
                val alignments = fractions.flatMapIndexed { row, vertical ->
                    fractions.mapIndexed { column, horizontal ->
                        PdfContentPosition(horizontal, vertical) to
                            if (row == 1 && column == 1) horizontalNames[1]
                            else "${verticalNames[row]}, ${horizontalNames[column]}"
                    }
                }
                DocumentPreviewHandle(
                    iconRes = R.drawable.ic_layout_position,
                    description = stringResource(R.string.gesture_move_handle),
                    valueDescription = alignments.firstOrNull {
                        it.first == layout.effectiveContentPosition()
                    }?.second ?: stringResource(R.string.layout_interactive_position),
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.TopCenter).absoluteOffset(y = (-24).dp)
                        .documentLayoutHandleGesture(
                            pageId, DocumentLayoutHandle.MOVE, layout, paperSize,
                            sourceWidth, sourceHeight, enabled, onLayoutChange,
                            ::startGesture, ::endGesture,
                        ),
                    actions = alignments.map { (position, label) ->
                        CustomAccessibilityAction(label) {
                            onLayoutChange(layout.copy(contentPosition = position)); true
                        }
                    },
                )
            }
            DocumentLayoutControl.ROTATION -> {
                val left = stringResource(R.string.layout_interactive_rotate_left)
                val right = stringResource(R.string.layout_interactive_rotate_right)
                fun turn(delta: Int) = onLayoutChange(layout.copy(
                    rotationQuarterTurns = documentLayoutRotation(layout.rotationQuarterTurns, delta),
                ))
                DocumentPreviewHandle(
                    iconRes = R.drawable.ic_layout_rotate_right,
                    description = stringResource(R.string.gesture_rotate_handle),
                    valueDescription = stringResource(R.string.tools_rotation_angle, layout.rotationQuarterTurns * 90),
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.TopCenter).absoluteOffset(y = (-24).dp)
                        .documentLayoutHandleGesture(
                            pageId, DocumentLayoutHandle.ROTATE, layout, paperSize,
                            sourceWidth, sourceHeight, enabled, onLayoutChange,
                            ::startGesture, ::endGesture,
                        ),
                    actions = listOf(
                        CustomAccessibilityAction(left) { turn(-1); true },
                        CustomAccessibilityAction(right) { turn(1); true },
                    ),
                    onClick = { turn(1) },
                )
            }
            DocumentLayoutControl.MARGINS -> Unit
            DocumentLayoutControl.SIZE -> {
                val increase = stringResource(R.string.gesture_scale_increase)
                val decrease = stringResource(R.string.gesture_scale_decrease)
                listOf(true, false).forEach { topLeft ->
                    DocumentPreviewHandle(
                        iconRes = R.drawable.ic_gesture_resize,
                        description = stringResource(R.string.gesture_scale_handle),
                        valueDescription = stringResource(R.string.tools_content_size_value, (layout.contentScale * 100).roundToInt()),
                        enabled = enabled,
                        modifier = Modifier
                            .align(if (topLeft) AbsoluteAlignment.TopLeft else AbsoluteAlignment.BottomRight)
                            .absoluteOffset(if (topLeft) (-24).dp else 24.dp, if (topLeft) (-24).dp else 24.dp)
                            .documentLayoutHandleGesture(
                                pageId,
                                if (topLeft) DocumentLayoutHandle.SCALE_TOP_LEFT else DocumentLayoutHandle.SCALE_BOTTOM_RIGHT,
                                layout, paperSize, sourceWidth, sourceHeight, enabled, onLayoutChange,
                                ::startGesture, ::endGesture,
                            ),
                        actions = listOf(
                            CustomAccessibilityAction(increase) {
                                onLayoutChange(layout.copy(contentScale = (layout.contentScale + .05f).coerceIn(.25f, 2f))); true
                            },
                            CustomAccessibilityAction(decrease) {
                                onLayoutChange(layout.copy(contentScale = (layout.contentScale - .05f).coerceIn(.25f, 2f))); true
                            },
                        ),
                    )
                }
            }
        }
    }
}
