package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.ui.documentEditor.util.DocumentLayoutRotationGesture
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutAlignmentAt
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutDragged
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutHandleRotation
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutHandleScale
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutRotation
import com.joseleandro.scaniqo.ui.documentEditor.util.documentLayoutScale
import com.joseleandro.scaniqo.ui.documentEditor.util.withLayoutAdjustmentsFrom
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentLayoutGesturesTest {
    @Test fun diagonalHandlesPreserveProportionsInBothCorners() {
        val size = IntSize(200, 400)
        assertEquals(1.5f, documentLayoutHandleScale(1f, Offset(50f, 100f), size, false), .001f)
        assertEquals(1.5f, documentLayoutHandleScale(1f, Offset(-50f, -100f), size, true), .001f)
        assertEquals(1f, documentLayoutHandleScale(1f, Offset(-100f, 50f), size, false), .001f)
        assertEquals(.25f, documentLayoutHandleScale(1f, Offset(-400f, -800f), size, false), .001f)
        assertEquals(2f, documentLayoutHandleScale(1f, Offset(400f, 800f), size, false), .001f)
    }

    @Test fun invalidHandleMeasurementsKeepSafeScale() {
        assertEquals(.8f, documentLayoutHandleScale(.8f, Offset(50f, 100f), IntSize.Zero, false), .001f)
        assertEquals(.8f, documentLayoutHandleScale(.8f, Offset(Float.NaN, 100f), IntSize(200, 400), true), .001f)
        assertEquals(1f, documentLayoutHandleScale(Float.NaN, Offset.Zero, IntSize(200, 400), false), .001f)
    }

    @Test fun oneFingerRotationCrossesAngleBoundaryWithoutFullTurnJump() {
        assertEquals(90f, documentLayoutHandleRotation(Offset(100f, 0f), Offset(0f, 100f), Offset.Zero), .001f)
        assertEquals(-90f, documentLayoutHandleRotation(Offset(0f, 100f), Offset(100f, 0f), Offset.Zero), .001f)
        assertEquals(1.146f, documentLayoutHandleRotation(Offset(-100f, 1f), Offset(-100f, -1f), Offset.Zero), .01f)
        assertEquals(0f, documentLayoutHandleRotation(Offset.Zero, Offset(100f, 100f), Offset.Zero), .001f)
        assertEquals(0f, documentLayoutHandleRotation(Offset(Float.NaN, 0f), Offset(100f, 100f), Offset.Zero), .001f)
    }

    @Test fun draggingChoosesClosestEdgeAndKeepsSmallMovementsCentered() {
        assertEquals(PageAlignment.CENTER, documentLayoutAlignmentAt(.6f, .4f))
        assertEquals(PageAlignment.LEFT, documentLayoutAlignmentAt(.05f, .5f))
        assertEquals(PageAlignment.RIGHT, documentLayoutAlignmentAt(.95f, .4f))
        assertEquals(PageAlignment.TOP, documentLayoutAlignmentAt(.5f, .05f))
        assertEquals(PageAlignment.BOTTOM, documentLayoutAlignmentAt(.5f, .95f))
        assertEquals(PageAlignment.TOP, documentLayoutAlignmentAt(.2f, .05f))
        assertEquals(PageAlignment.CENTER, documentLayoutAlignmentAt(Float.NaN, .5f))
    }

    @Test fun dragCanReachTopLeftAndTracksTheActualPaperDistance() {
        val initial = PdfPageLayout(contentScale = .75f, edgeToEdge = true)
        val paper = IntSize(595, 842)
        val moved = documentLayoutDragged(initial, Offset(-100f, -120f), paper, 595f, 842f)
        assertEquals(0f, moved.contentPosition!!.horizontal, .001f)
        assertEquals(0f, moved.contentPosition.vertical, .001f)

        val small = documentLayoutDragged(initial, Offset(30f, 20f), paper, 595f, 842f)
        val split = documentLayoutDragged(
            documentLayoutDragged(initial, Offset(12f, 8f), paper, 595f, 842f),
            Offset(18f, 12f), paper, 595f, 842f,
        )
        assertEquals(small.contentPosition!!.horizontal, split.contentPosition!!.horizontal, .001f)
        assertEquals(small.contentPosition.vertical, split.contentPosition.vertical, .001f)
    }

    @Test fun dragWithoutAvailableSpaceDoesNotCreateInvisibleChange() {
        val initial = PdfPageLayout(edgeToEdge = true)
        assertEquals(
            initial,
            documentLayoutDragged(initial, Offset(50f, 50f), IntSize(595, 842), 595f, 842f),
        )
    }

    @Test fun pinchPreservesProportionsAndClampsToSupportedRange() {
        assertEquals(.75f, documentLayoutScale(.5f, 1.5f), .001f)
        assertEquals(.25f, documentLayoutScale(.5f, .1f), .001f)
        assertEquals(2f, documentLayoutScale(1.5f, 4f), .001f)
        assertEquals(.8f, documentLayoutScale(.8f, Float.NaN), .001f)
        assertEquals(.8f, documentLayoutScale(.8f, -1f), .001f)
    }

    @Test fun rotationAccumulatesSmallDeltasAndSupportsBothDirections() {
        val gesture = DocumentLayoutRotationGesture()
        assertEquals(0, gesture.turn(20f))
        assertEquals(0, gesture.turn(20f))
        assertEquals(1, gesture.turn(20f))
        assertEquals(0, gesture.turn(30f))
        assertEquals(-1, gesture.turn(-60f))
        assertEquals(0, gesture.turn(-30f))
        assertEquals(0, gesture.turn(Float.NaN))
        assertEquals(0, documentLayoutRotation(3, 1))
        assertEquals(3, documentLayoutRotation(0, -1))
    }

    @Test fun separateRotationGesturesDoNotCarryResidualAngles() {
        val gesture = DocumentLayoutRotationGesture()
        assertEquals(0, gesture.turn(30f))
        gesture.reset()
        assertEquals(0, gesture.turn(30f))
        assertEquals(1, gesture.turn(20f))
    }

    @Test fun previewGeometryRetainsEachPagesFiltersAndCorrections() {
        val page = PdfPageLayout(
            imageFilter = PdfImageFilter.WHITEN,
            filterIntensity = .7f,
            straightenDegrees = 3f,
            perspective = PdfPerspective(),
            curvature = .4f,
            rasterizeText = true,
        )
        val draft = PdfPageLayout(
            alignment = PageAlignment.BOTTOM,
            scaleMode = PageScaleMode.FILL,
            rotationQuarterTurns = 3,
            contentScale = .6f,
            contentPosition = com.joseleandro.scaniqo.domain.model.PdfContentPosition(0f, 0f),
            edgeToEdge = true,
        )
        assertEquals(
            page.copy(
                alignment = PageAlignment.BOTTOM,
                scaleMode = PageScaleMode.FILL,
                rotationQuarterTurns = 3,
                contentScale = .6f,
                contentPosition = draft.contentPosition,
                edgeToEdge = true,
            ),
            page.withLayoutAdjustmentsFrom(draft),
        )
        assertEquals(page, page.withLayoutAdjustmentsFrom(draft).withLayoutAdjustmentsFrom(PdfPageLayout()))
    }
}
