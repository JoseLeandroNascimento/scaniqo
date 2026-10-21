package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import kotlin.math.roundToInt

/** Camada visual em pixels do palco: alças conservam tamanho mesmo com zoom. */
@Composable
internal fun DocumentFillMarkSelection(
    selection: FillMarkSelectionGeometry,
    draft: PdfOverlay,
    enabled: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val move = stringResource(R.string.fill_selection_move)
    val grow = stringResource(R.string.fill_selection_grow)
    val shrink = stringResource(R.string.fill_selection_shrink)
    val left = stringResource(R.string.fill_mark_move_left)
    val right = stringResource(R.string.fill_mark_move_right)
    val up = stringResource(R.string.fill_mark_move_up)
    val down = stringResource(R.string.fill_mark_move_down)
    val corners = listOf(
        stringResource(R.string.fill_selection_top_left), stringResource(R.string.fill_selection_top_right),
        stringResource(R.string.fill_selection_bottom_right), stringResource(R.string.fill_selection_bottom_left),
    )
    Box(modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        Canvas(Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            val outline = Path().apply {
                selection.corners.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
                close()
            }
            drawPath(outline, accent, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
            if (enabled) selection.visibleHandles.forEach { index ->
                val anchor = if (index == 4) selection.topMiddle else selection.corners[index]
                val handle = selection.handles[index]
                drawLine(accent, Offset(anchor.x, anchor.y), Offset(handle.x, handle.y),
                    1.dp.toPx(), pathEffect = dash)
            }
        }
        if (enabled) selection.visibleHandles.forEach { index ->
            val handle = selection.handles[index]
            val isMove = index == 4
            val actions = if (isMove) listOf(
                CustomAccessibilityAction(left) { onUpdate(draft.movedTo(draft.x - .005f, draft.y)); true },
                CustomAccessibilityAction(right) { onUpdate(draft.movedTo(draft.x + .005f, draft.y)); true },
                CustomAccessibilityAction(up) { onUpdate(draft.movedTo(draft.x, draft.y - .005f)); true },
                CustomAccessibilityAction(down) { onUpdate(draft.movedTo(draft.x, draft.y + .005f)); true },
            ) else listOf(
                CustomAccessibilityAction(grow) { onUpdate(draft.scaledFillMarkAtCorner(index, 1.1f)); true },
                CustomAccessibilityAction(shrink) { onUpdate(draft.scaledFillMarkAtCorner(index, .9f)); true },
            )
            Box(
                Modifier.absoluteOffset {
                    IntOffset((handle.x - selection.targetSize / 2f).roundToInt(),
                        (handle.y - selection.targetSize / 2f).roundToInt())
                }.size(48.dp).semantics {
                    contentDescription = if (isMove) move else corners[index]
                    customActions = actions
                },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    Modifier.size(if (isMove) 32.dp else 22.dp),
                    shape = CircleShape, color = accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary, shadowElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isMove) Icon(
                            painterResource(R.drawable.ic_layout_position), null, Modifier.size(20.dp),
                        ) else Box(Modifier.size(10.dp).background(Color.White, CircleShape))
                    }
                }
            }
        }
    }
}
