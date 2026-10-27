package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.ui.documentEditor.state.CorrectionDraftSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionDraftSessionTest {
    @Test
    fun continuousAdjustmentIsOneUndoStepAndPreservesBaseline() {
        val baseline = PdfPageLayout(straightenDegrees = 2f)
        val session = CorrectionDraftSession(baseline)

        session.beginGesture()
        session.changeGesture(baseline.copy(straightenDegrees = 2.1f))
        session.changeGesture(baseline.copy(straightenDegrees = 2.2f))
        session.finishGesture()

        assertTrue(session.canUndo)
        assertEquals(2.2f, session.draft.straightenDegrees)
        assertTrue(session.undo())
        assertEquals(baseline, session.draft)
        assertFalse(session.canUndo)
        assertTrue(session.redo())
        assertEquals(2.2f, session.draft.straightenDegrees)
        assertEquals(baseline, session.baseline)
    }

    @Test
    fun cancelledEditDoesNotCreateHistoryOrApplyCrop() {
        val baseline = PdfPageLayout(crop = PdfCropBounds(.1f, .1f, .9f, .9f))
        val session = CorrectionDraftSession(baseline)
        session.beginGesture()
        session.changeGesture(baseline.copy(crop = PdfCropBounds(.2f, .2f, .8f, .8f)))
        session.cancelGesture()

        assertEquals(baseline, session.draft)
        assertFalse(session.canUndo)
    }

    @Test
    fun acceptedDetectionCanBeUndoneWithoutDiscardingOtherCorrections() {
        val baseline = PdfPageLayout(straightenDegrees = 3f)
        val session = CorrectionDraftSession(baseline)
        val accepted = baseline.copy(curvature = .15f)

        session.edit(accepted)

        assertTrue(session.undo())
        assertEquals(3f, session.draft.straightenDegrees)
        assertEquals(0f, session.draft.curvature)
    }

    @Test
    fun newEditClearsRedo() {
        val session = CorrectionDraftSession(PdfPageLayout())
        session.edit(session.draft.copy(straightenDegrees = 1f))
        session.undo()
        assertTrue(session.canRedo)

        session.edit(session.draft.copy(curvature = .1f))

        assertFalse(session.canRedo)
    }
}
