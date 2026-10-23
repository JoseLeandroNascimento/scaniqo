package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
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
    onLayoutChange: (PdfPageLayout) -> Unit,
) {
    val guide = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paperSize = with(LocalDensity.current) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
        Canvas(Modifier.fillMaxSize().documentLayoutGestures(pageId, control, layout, enabled, onLayoutChange)) {
            if (enabled) {
                drawRect(guide.copy(alpha = .28f), style = Stroke(1.dp.toPx()))
                if (control == DocumentLayoutControl.POSITION) {
                    val inset = 2.dp.toPx()
                    val right = size.width - inset
                    val bottom = size.height - inset
                    when (layout.alignment) {
                        PageAlignment.TOP -> drawLine(guide, Offset(inset, inset), Offset(right, inset), 2.dp.toPx())
                        PageAlignment.BOTTOM -> drawLine(guide, Offset(inset, bottom), Offset(right, bottom), 2.dp.toPx())
                        PageAlignment.LEFT -> drawLine(guide, Offset(inset, inset), Offset(inset, bottom), 2.dp.toPx())
                        PageAlignment.RIGHT -> drawLine(guide, Offset(right, inset), Offset(right, bottom), 2.dp.toPx())
                        PageAlignment.CENTER -> {
                            drawLine(guide.copy(alpha = .22f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                            drawLine(guide.copy(alpha = .22f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                        }
                    }
                }
            }
        }
        when (control) {
            DocumentLayoutControl.POSITION -> {
                val alignments = listOf(
                    PageAlignment.LEFT to stringResource(R.string.tools_align_left),
                    PageAlignment.CENTER to stringResource(R.string.tools_align_center),
                    PageAlignment.RIGHT to stringResource(R.string.tools_align_right),
                    PageAlignment.TOP to stringResource(R.string.tools_align_top),
                    PageAlignment.BOTTOM to stringResource(R.string.tools_align_bottom),
                )
                DocumentPreviewHandle(
                    iconRes = R.drawable.ic_layout_position,
                    description = stringResource(R.string.gesture_move_handle),
                    valueDescription = alignments.first { it.first == layout.alignment }.second,
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.TopCenter).absoluteOffset(y = (-24).dp)
                        .documentLayoutHandleGesture(pageId, DocumentLayoutHandle.MOVE, layout, paperSize, enabled, onLayoutChange),
                    actions = alignments.map { (alignment, label) ->
                        CustomAccessibilityAction(label) { onLayoutChange(layout.copy(alignment = alignment)); true }
                    },
                    onClick = { onLayoutChange(layout.copy(alignment = PageAlignment.CENTER)) },
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
                        .documentLayoutHandleGesture(pageId, DocumentLayoutHandle.ROTATE, layout, paperSize, enabled, onLayoutChange),
                    actions = listOf(
                        CustomAccessibilityAction(left) { turn(-1); true },
                        CustomAccessibilityAction(right) { turn(1); true },
                    ),
                    onClick = { turn(1) },
                )
            }
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
                                layout, paperSize, enabled, onLayoutChange,
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
