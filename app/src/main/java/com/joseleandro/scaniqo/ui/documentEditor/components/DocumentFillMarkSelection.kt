package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
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

@Composable
internal fun DocumentFillMarkSelection(
    selection: FillMarkSelectionGeometry,
    draft: PdfOverlay,
    enabled: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val reveal = remember(draft.id) { Animatable(0f) }
    LaunchedEffect(draft.id) { reveal.animateTo(1f, animationSpec = tween(120)) }
    val visualProgress = if (LocalInspectionMode.current) 1f else reveal.value
    val resizeLabel = stringResource(R.string.fill_v4_selection_resize)
    val grow = stringResource(R.string.fill_selection_grow)
    val shrink = stringResource(R.string.fill_selection_shrink)
    val left = stringResource(R.string.fill_mark_move_left)
    val right = stringResource(R.string.fill_mark_move_right)
    val up = stringResource(R.string.fill_mark_move_up)
    val down = stringResource(R.string.fill_mark_move_down)
    val resizeHandle = selection.handles[2]
    val resizeAnchor = selection.corners[2]
    val showHandle = enabled && 2 in selection.visibleHandles

    Box(modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = visualProgress }) {
            val outline = Path().apply {
                selection.corners.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
                close()
            }
            drawPath(outline, accent.copy(alpha = .85f), style = Stroke(1.25.dp.toPx()))
            if (showHandle) {
                val anchor = Offset(resizeAnchor.x, resizeAnchor.y)
                val handle = Offset(resizeHandle.x, resizeHandle.y)
                if ((handle - anchor).getDistance() in 1f..28.dp.toPx()) {
                    drawLine(accent.copy(alpha = .75f), anchor, handle, strokeWidth = 1.dp.toPx())
                }
            }
        }
        if (showHandle) {
            fun resize(factor: Float) {
                val resized = if (draft.kind in textKinds) {
                    draft.resizedFillMarkBoxFromCorner(
                        2, draft.width * 595f * (factor - 1f),
                        draft.height * 842f * (factor - 1f), 595f, 842f,
                    )
                } else draft.scaledFillMarkAtCorner(2, factor)
                onUpdate(resized)
            }
            val actions = listOf(
                CustomAccessibilityAction(grow) { resize(1.1f); true },
                CustomAccessibilityAction(shrink) { resize(.9f); true },
                CustomAccessibilityAction(left) { onUpdate(draft.movedTo(draft.x - .005f, draft.y)); true },
                CustomAccessibilityAction(right) { onUpdate(draft.movedTo(draft.x + .005f, draft.y)); true },
                CustomAccessibilityAction(up) { onUpdate(draft.movedTo(draft.x, draft.y - .005f)); true },
                CustomAccessibilityAction(down) { onUpdate(draft.movedTo(draft.x, draft.y + .005f)); true },
            )
            Box(
                Modifier.absoluteOffset {
                    IntOffset(
                        (resizeHandle.x - selection.targetSize / 2f).roundToInt(),
                        (resizeHandle.y - selection.targetSize / 2f).roundToInt(),
                    )
                }.size(48.dp).semantics {
                    contentDescription = resizeLabel
                    customActions = actions
                },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    Modifier.size(22.dp).graphicsLayer {
                        alpha = visualProgress
                        scaleX = .96f + .04f * visualProgress
                        scaleY = .96f + .04f * visualProgress
                    },
                    shape = CircleShape,
                    color = surface,
                    contentColor = accent,
                    border = BorderStroke(1.5.dp, accent),
                    shadowElevation = 1.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_gesture_resize),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
