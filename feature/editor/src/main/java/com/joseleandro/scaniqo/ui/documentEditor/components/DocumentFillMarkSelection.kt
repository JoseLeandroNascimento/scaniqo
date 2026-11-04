package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.joseleandro.scaniqo.domain.model.PdfOverlay

@Composable
internal fun DocumentFillMarkSelection(
    selection: FillMarkSelectionGeometry,
    draft: PdfOverlay,
    enabled: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val left = stringResource(R.string.fill_mark_move_left)
    val right = stringResource(R.string.fill_mark_move_right)
    val up = stringResource(R.string.fill_mark_move_up)
    val down = stringResource(R.string.fill_mark_move_down)
    val grow = stringResource(R.string.fill_selection_grow)
    val shrink = stringResource(R.string.fill_selection_shrink)
    val rotateLeft = stringResource(R.string.signature_rotate_left)
    val rotateRight = stringResource(R.string.signature_rotate_right)
    val labels = listOf(
        stringResource(R.string.fill_selection_top_left),
        stringResource(R.string.fill_selection_top_right),
        stringResource(R.string.fill_selection_bottom_right),
        stringResource(R.string.fill_selection_bottom_left),
        stringResource(CoreResourcesR.string.signature_adjust_rotate),
    )
    fun resize(index: Int, deltaX: Float, deltaY: Float) {
        onUpdate(if (draft.kind in textKinds) draft.resizedFillMarkBoxFromCorner(index, deltaX, deltaY, 595f, 842f)
            else draft.scaledFillMarkFromCorner(index, deltaX, deltaY, 595f, 842f))
    }
    DocumentTransformSelection(
        selection = selection.geometry,
        enabled = enabled,
        visibleHandles = selection.visibleHandles,
        handleLabels = labels,
        modifier = modifier,
        showHandles = enabled,
        testTagPrefix = "fill_selection",
        handleActions = { index ->
            if (index == 4) listOf(
                CustomAccessibilityAction(rotateLeft) { onUpdate(draft.rotatedFillMark(draft.rotationDegrees - 15f)); true },
                CustomAccessibilityAction(rotateRight) { onUpdate(draft.rotatedFillMark(draft.rotationDegrees + 15f)); true },
            ) else listOf(
                CustomAccessibilityAction(left) { resize(index, -595f * .02f, 0f); true },
                CustomAccessibilityAction(right) { resize(index, 595f * .02f, 0f); true },
                CustomAccessibilityAction(up) { resize(index, 0f, -842f * .02f); true },
                CustomAccessibilityAction(down) { resize(index, 0f, 842f * .02f); true },
                CustomAccessibilityAction(grow) { onUpdate(draft.scaledFillMarkAtCorner(index, 1.1f)); true },
                CustomAccessibilityAction(shrink) { onUpdate(draft.scaledFillMarkAtCorner(index, .9f)); true },
            )
        },
    )
}
