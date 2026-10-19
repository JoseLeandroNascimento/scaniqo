package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.components.containsFillMarkPoint
import com.joseleandro.scaniqo.ui.documentEditor.components.isFillMarkResizePoint
import com.joseleandro.scaniqo.ui.documentEditor.components.resizedFillMark
import com.joseleandro.scaniqo.ui.documentEditor.components.placedFillMarkAt
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkGestureIntent
import org.junit.Assert.*
import org.junit.Test

class FillMarkGeometryTest {
    private val element = PdfOverlay("text", PdfOverlayKind.TEXT, x = .2f, y = .3f, width = .4f, height = .1f)

    @Test fun newTextKeepsTheTapAnchorNearTheRightEdge() {
        val placed = element.placedFillMarkAt(PdfPoint(.9f, .8f))
        assertEquals(.9f, placed.x, .00001f)
        assertEquals(.8f, placed.y, .00001f)
        assertEquals(.1f, placed.width, .00001f)
        assertTrue(placed.isValid())
    }

    @Test fun twoFingerGestureCannotInsertOrCommitADragWhenOnlyOneFingerRemains() {
        val gesture = FillMarkGestureIntent()
        gesture.update(1, 25f, 10f)
        assertTrue(gesture.canCommitDrag)
        gesture.update(2, 30f, 10f)
        gesture.update(1, 35f, 10f)
        gesture.update(0, 35f, 10f)
        assertFalse(gesture.canTap)
        assertFalse(gesture.canCommitDrag)
    }

    @Test fun aPinchWithoutMovementStillDoesNotBecomeATap() {
        val gesture = FillMarkGestureIntent()
        gesture.update(2, 0f, 10f)
        gesture.update(0, 0f, 10f)
        assertFalse(gesture.canTap)
    }

    @Test fun shortSingleTouchRemainsAnInsertionTap() {
        val gesture = FillMarkGestureIntent()
        gesture.update(1, 3f, 10f)
        gesture.update(0, 3f, 10f)
        assertTrue(gesture.canTap)
        assertFalse(gesture.canCommitDrag)
    }

    @Test fun hitTestingSupportsSmallTextTouchTargetsWithoutMovingCoordinates() {
        assertTrue(element.containsFillMarkPoint(PdfPoint(.19f, .29f), .02f, .02f))
        assertFalse(element.containsFillMarkPoint(PdfPoint(.1f, .3f), .02f, .02f))
        assertEquals(.2f, element.x)
    }

    @Test fun resizingUsesBottomRightHandleOnly() {
        assertTrue(element.isFillMarkResizePoint(PdfPoint(.61f, .39f), .02f, .02f))
        assertFalse(element.isFillMarkResizePoint(PdfPoint(.2f, .3f), .02f, .02f))
    }

    @Test fun smallCheckmarkCenterStillMovesInsteadOfResizing() {
        val check = element.copy(kind = PdfOverlayKind.CHECKMARK, width = .04f, height = .03f)
        assertFalse(check.isFillMarkResizePoint(PdfPoint(.22f, .315f), .1f, .1f))
        assertTrue(check.isFillMarkResizePoint(PdfPoint(.245f, .335f), .1f, .1f))
    }

    @Test fun resizeClampsAtPageEdgeAndPreventsInversion() {
        val large = element.resizedFillMark(2f, 2f)
        assertEquals(.8f, large.width, .00001f)
        assertEquals(.7f, large.height, .00001f)
        assertTrue(large.isValid())
        val small = element.resizedFillMark(-1f, -1f)
        assertEquals(.025f, small.width, .00001f)
        assertEquals(.018f, small.height, .00001f)
        assertTrue(small.isValid())
    }

    @Test fun resizeKeepsDrawingInsideItsNewBox() {
        val drawing = element.copy(kind = PdfOverlayKind.DRAWING, points = listOf(PdfPoint(.2f, .3f), PdfPoint(.6f, .4f)))
        val resized = drawing.resizedFillMark(.4f, .5f)
        assertEquals(.4f, resized.points.last().x, .00001f)
        assertEquals(.5f, resized.points.last().y, .00001f)
        assertTrue(resized.isValid())
    }

    @Test fun resizeNearPageCornerRemainsValid() {
        val edge = element.copy(x = .99f, y = .99f, width = .01f, height = .01f)
        assertTrue(edge.resizedFillMark(-10f, -10f).isValid())
    }
}
