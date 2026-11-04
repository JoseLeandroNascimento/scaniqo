package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLayoutControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayoutDraftHistoryTest {
    @Test fun presetsResetScaleAndPreservePositionAndRotation() {
        val start = PdfPageLayout(
            scaleMode = PageScaleMode.FILL,
            contentScale = 1.37f,
            contentPosition = PdfContentPosition(0f, 1f),
            rotationQuarterTurns = 3,
        )
        LayoutSizePreset.entries.forEach { preset ->
            val result = preset.apply(start)
            assertEquals(1f, result.contentScale)
            assertEquals(preset.scaleMode, result.scaleMode)
            assertEquals(preset.edgeToEdge, result.edgeToEdge)
            assertEquals(start.contentPosition, result.contentPosition)
            assertEquals(start.rotationQuarterTurns, result.rotationQuarterTurns)
            assertEquals(preset, LayoutSizePreset.selected(result))
        }
        assertNull(LayoutSizePreset.selected(start))
        assertNull(LayoutSizePreset.selected(start.copy(contentScale = 1f, edgeToEdge = false)))
    }

    @Test fun oneCompletedGestureCreatesOneUndoEntryAndNewChangeClearsRedo() {
        val start = PdfPageLayout()
        val dragged = start.copy(contentPosition = PdfContentPosition(.2f, .8f))
        val history = LayoutDraftHistory().record(start, dragged)
        assertEquals(listOf(start), history.undo)
        val (undone, afterUndo) = requireNotNull(history.undo(dragged))
        assertEquals(start, undone)
        assertEquals(listOf(dragged), afterUndo.redo)
        val next = start.copy(contentScale = .75f)
        val afterNewChange = afterUndo.record(start, next)
        assertEquals(emptyList<PdfPageLayout>(), afterNewChange.redo)
        assertNull(afterNewChange.redo(next))
        assertEquals(afterNewChange, afterNewChange.record(next, next))
    }

    @Test fun restoringEachCategoryKeepsOtherChanges() {
        val baseline = PdfPageLayout(
            alignment = PageAlignment.LEFT,
            scaleMode = PageScaleMode.FILL,
            contentPosition = PdfContentPosition(0f, .5f),
            contentScale = .8f,
            rotationQuarterTurns = 1,
            edgeToEdge = true,
        )
        val draft = PdfPageLayout(
            alignment = PageAlignment.RIGHT,
            scaleMode = PageScaleMode.FIT,
            contentPosition = PdfContentPosition(1f, 0f),
            contentScale = 1.6f,
            rotationQuarterTurns = 3,
            edgeToEdge = false,
        )
        val position = restoreLayoutCategory(draft, baseline, DocumentLayoutControl.POSITION)
        assertEquals(baseline.contentPosition, position.contentPosition)
        assertEquals(baseline.alignment, position.alignment)
        assertEquals(draft.contentScale, position.contentScale)
        assertEquals(draft.rotationQuarterTurns, position.rotationQuarterTurns)

        val size = restoreLayoutCategory(draft, baseline, DocumentLayoutControl.SIZE)
        assertEquals(baseline.scaleMode, size.scaleMode)
        assertEquals(baseline.edgeToEdge, size.edgeToEdge)
        assertEquals(baseline.contentScale, size.contentScale)
        assertEquals(draft.contentPosition, size.contentPosition)
        assertEquals(draft.rotationQuarterTurns, size.rotationQuarterTurns)

        val rotation = restoreLayoutCategory(draft, baseline, DocumentLayoutControl.ROTATION)
        assertEquals(baseline.rotationQuarterTurns, rotation.rotationQuarterTurns)
        assertEquals(draft.contentScale, rotation.contentScale)
        assertEquals(draft.contentPosition, rotation.contentPosition)
    }
}
