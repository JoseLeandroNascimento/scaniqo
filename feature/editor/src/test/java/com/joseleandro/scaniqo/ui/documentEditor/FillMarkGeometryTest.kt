package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.ui.documentEditor.components.fillMarkPositionMapping
import com.joseleandro.scaniqo.ui.documentEditor.components.fillMarkScaleRange
import com.joseleandro.scaniqo.ui.documentEditor.components.rotatedFillMark
import com.joseleandro.scaniqo.ui.documentEditor.components.scaledFillMarkAroundCenter
import com.joseleandro.scaniqo.ui.documentEditor.components.scaledFillMarkAtCorner
import com.joseleandro.scaniqo.ui.documentEditor.util.documentSelectionGeometry
import kotlin.math.cos
import kotlin.math.sin
import com.joseleandro.scaniqo.ui.documentEditor.components.containsFillMarkPoint
import com.joseleandro.scaniqo.ui.documentEditor.components.isFillMarkResizePoint
import com.joseleandro.scaniqo.ui.documentEditor.components.resizedFillMark
import com.joseleandro.scaniqo.ui.documentEditor.components.resizedFillMarkBoxFromCorner
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

    @Test fun textBoxResizeKeepsFontSizeAndChangesOnlyTheDraggedAxes() {
        val resized = element.copy(autoSizeText = true).resizedFillMarkBoxFromCorner(2, 100f, 0f, 1000f, 1000f)
        assertEquals(.5f, resized.width, .00001f)
        assertEquals(element.height, resized.height, .00001f)
        assertEquals(element.fontSize, resized.fontSize, .00001f)
        assertEquals(element.x, resized.x, .00001f)
        assertEquals(element.y, resized.y, .00001f)
        assertFalse(resized.autoSizeText)
        assertTrue(resized.wrapText)
    }

    @Test fun textBoxResizeAnchorsOppositeCornerForEveryHandle() {
        for (corner in 0..3) {
            val fromLeft = corner == 0 || corner == 3
            val fromTop = corner < 2
            val resized = element.resizedFillMarkBoxFromCorner(corner, 50f, 30f, 1000f, 1000f)
            assertEquals(if (fromLeft) element.x + element.width else element.x,
                if (fromLeft) resized.x + resized.width else resized.x, .00001f)
            assertEquals(if (fromTop) element.y + element.height else element.y,
                if (fromTop) resized.y + resized.height else resized.y, .00001f)
            assertTrue(resized.isValid())
        }
    }

    @Test fun textBoxResizeCannotInvertOrLeaveThePage() {
        for (corner in 0..3) for (delta in listOf(-10000f, 10000f)) {
            val resized = element.resizedFillMarkBoxFromCorner(corner, delta, delta, 1000f, 1000f)
            assertTrue(resized.isValid())
            assertEquals(element.fontSize, resized.fontSize, .00001f)
        }
    }

    @Test fun textBoxResizeIsIndependentOfPreviewZoom() {
        assertEquals(element.resizedFillMarkBoxFromCorner(0, 20f, -15f, 595f, 842f),
            element.resizedFillMarkBoxFromCorner(0, 60f, -45f, 1785f, 2526f))
    }

    @Test fun textBoxResizeIgnoresInvalidGeometry() {
        assertEquals(element, element.resizedFillMarkBoxFromCorner(4, 1f, 1f, 595f, 842f))
        assertEquals(element, element.resizedFillMarkBoxFromCorner(2, Float.NaN, 1f, 595f, 842f))
        assertEquals(element, element.resizedFillMarkBoxFromCorner(2, 1f, 1f, 0f, 842f))
    }

    @Test fun resizeNearPageCornerRemainsValid() {
        val edge = element.copy(x = .99f, y = .99f, width = .01f, height = .01f)
        assertTrue(edge.resizedFillMark(-10f, -10f).isValid())
    }

    @Test fun hitTestingFollowsRotatedContentInsteadOfTheOriginalBox() {
        val rotated = element.copy(rotationDegrees = 90f)
        assertTrue(rotated.containsFillMarkPoint(PdfPoint(.4f, .47f)))
        assertFalse(rotated.containsFillMarkPoint(PdfPoint(.58f, .35f)))
    }

    @Test fun rotatedTextResizeKeepsTheOppositeCornerAndFontAtEveryHandle() {
        for (rotation in listOf(-45f, 32f, 90f, 135f)) for (corner in 0..3) {
            val original = element.copy(rotationDegrees = rotation)
            val radians = Math.toRadians(rotation.toDouble())
            val resized = original.resizedFillMarkBoxFromCorner(
                corner, 24f * cos(radians).toFloat(), 24f * sin(radians).toFloat(), 595f, 842f,
            )
            val opposite = (corner + 2) % 4
            val before = pageCorners(original)[opposite]
            val after = pageCorners(resized)[opposite]
            assertEquals(before.x, after.x, .00001f)
            assertEquals(before.y, after.y, .00001f)
            assertEquals(original.height, resized.height, .00001f)
            assertEquals(original.fontSize, resized.fontSize, .00001f)
            assertEquals(rotation, resized.rotationDegrees, 0f)
            assertTrue(resized.isValid())
        }
    }

    @Test fun rotationAndScalingFitContentWithinPageWithoutChangingItsAspectRatio() {
        val original = element.copy(x = .05f, y = .05f, width = .9f, height = .9f)
        for (rotation in listOf(-135f, -45f, 45f, 90f)) {
            val rotated = original.rotatedFillMark(rotation)
            assertTrue(rotated.isValid())
            assertEquals(original.width / original.height, rotated.width / rotated.height, .00001f)
            assertEquals(original.fontSize, rotated.fontSize, 0f)
            assertCornersOnPage(rotated)
            assertCornersOnPage(rotated.scaledFillMarkAroundCenter(10f))
        }
    }

    @Test fun rotationAwarePositionPadReachesBothEdgesAndReturnsItsPosition() {
        val original = element.copy(rotationDegrees = 45f)
        for (horizontal in listOf(0f, .5f, 1f)) for (vertical in listOf(0f, .5f, 1f)) {
            val position = PdfContentPosition(horizontal, vertical)
            val placed = fillMarkPositionMapping(original).place(position)
            val restored = fillMarkPositionMapping(placed).position()
            assertEquals(horizontal, restored.horizontal, .00001f)
            assertEquals(vertical, restored.vertical, .00001f)
            assertCornersOnPage(placed)
            assertEquals(original.width, placed.width, 0f)
            assertEquals(original.rotationDegrees, placed.rotationDegrees, 0f)
        }
    }

    @Test fun proportionalScalePreservesCenterAndDrawingPoints() {
        val drawing = element.copy(kind = PdfOverlayKind.DRAWING, rotationDegrees = 25f,
            points = listOf(PdfPoint(.2f, .3f), PdfPoint(.6f, .4f)))
        val scaled = drawing.scaledFillMarkAroundCenter(.75f)
        assertEquals(drawing.x + drawing.width / 2f, scaled.x + scaled.width / 2f, .00001f)
        assertEquals(drawing.y + drawing.height / 2f, scaled.y + scaled.height / 2f, .00001f)
        assertEquals(.75f, scaled.width / drawing.width, .00001f)
        assertEquals(.75f, scaled.height / drawing.height, .00001f)
        assertEquals(scaled.x, scaled.points.first().x, .00001f)
        assertEquals(scaled.x + scaled.width, scaled.points.last().x, .00001f)
        assertTrue(scaled.isValid())
    }

    @Test fun markScaleRangeDependsOnPageGeometryInsteadOfFontSize() {
        for (fontSize in listOf(6f, 16f, 72f)) {
            val mark = element.copy(kind = PdfOverlayKind.CHECKMARK, width = .04f, height = .03f,
                fontSize = fontSize)
            val range = mark.fillMarkScaleRange()
            assertEquals(.01f / mark.height, range.start, .00001f)
            assertEquals(1f / mark.width, range.endInclusive, .00001f)
        }
    }

    @Test fun marksScalePastTheFormerFontLimitWithoutChangingTheirFont() {
        val kinds = listOf(PdfOverlayKind.CHECKMARK, PdfOverlayKind.CROSSMARK,
            PdfOverlayKind.HIGHLIGHT, PdfOverlayKind.UNDERLINE, PdfOverlayKind.ARROW, PdfOverlayKind.DRAWING)
        for (kind in kinds) for (fontSize in listOf(16f, 72f)) {
            val mark = element.copy(kind = kind, x = .3f, y = .3f, width = .04f, height = .03f,
                fontSize = fontSize)
            val scaledMarks = listOf(mark.scaledFillMarkAroundCenter(10f), mark.scaledFillMarkAtCorner(2, 10f))
            scaledMarks.forEach { scaled ->
                assertEquals(.4f, scaled.width, .00001f)
                assertEquals(.3f, scaled.height, .00001f)
                assertTrue(scaled.width > .18f)
                assertEquals(mark.fontSize, scaled.fontSize, 0f)
                assertTrue(scaled.isValid())
                assertCornersOnPage(scaled)
            }
        }
    }

    @Test fun marksKeepBothDimensionsAtLeastOnePercentWhenShrinking() {
        for ((width, height) in listOf(.12f to .04f, .04f to .12f)) {
            val mark = element.copy(kind = PdfOverlayKind.CROSSMARK, width = width, height = height)
            val scaledMarks = listOf(mark.scaledFillMarkAroundCenter(0f)) +
                (0..3).map { corner -> mark.scaledFillMarkAtCorner(corner, 0f) }
            scaledMarks.forEach { scaled ->
                assertEquals(.01f, minOf(scaled.width, scaled.height), .00001f)
                assertEquals(mark.width / mark.height, scaled.width / scaled.height, .00001f)
                assertEquals(mark.fontSize, scaled.fontSize, 0f)
                assertTrue(scaled.isValid())
            }
        }
    }

    @Test fun rotatedMarksScaleToPageBoundsAndKeepTheirOppositeCorner() {
        for (rotation in listOf(-135f, -45f, 32f, 90f)) {
            val mark = element.copy(kind = PdfOverlayKind.CHECKMARK, x = .45f, y = .45f,
                width = .04f, height = .03f, rotationDegrees = rotation, fontSize = 72f)
            val centered = mark.scaledFillMarkAroundCenter(100f)
            assertTrue(centered.width > .18f)
            assertEquals(mark.fontSize, centered.fontSize, 0f)
            assertTrue(centered.isValid())
            assertCornersOnPage(centered)
            for (corner in 0..3) {
                val scaled = mark.scaledFillMarkAtCorner(corner, 100f)
                val opposite = (corner + 2) % 4
                val before = pageCorners(mark)[opposite]
                val after = pageCorners(scaled)[opposite]
                assertEquals(before.x, after.x, .00001f)
                assertEquals(before.y, after.y, .00001f)
                assertTrue(scaled.width > .18f)
                assertEquals(mark.width / mark.height, scaled.width / scaled.height, .00001f)
                assertEquals(mark.fontSize, scaled.fontSize, 0f)
                assertEquals(rotation, scaled.rotationDegrees, 0f)
                assertTrue(scaled.isValid())
                assertCornersOnPage(scaled)
            }
        }
    }

    @Test fun textKindsStillScaleWithTheirFontWithinTheExistingLimits() {
        val kinds = listOf(PdfOverlayKind.TEXT, PdfOverlayKind.STAMP, PdfOverlayKind.WATERMARK,
            PdfOverlayKind.HEADER, PdfOverlayKind.FOOTER, PdfOverlayKind.PAGE_NUMBER)
        for (kind in kinds) {
            val text = element.copy(kind = kind, x = .45f, y = .45f, width = .04f, height = .03f,
                fontSize = 16f)
            assertEquals(6f / 16f, text.fillMarkScaleRange().start, .00001f)
            assertEquals(72f / 16f, text.fillMarkScaleRange().endInclusive, .00001f)
            for ((scale, expectedFont) in listOf(0f to 6f, 100f to 72f)) {
                val scaledTexts = listOf(text.scaledFillMarkAroundCenter(scale)) +
                    (0..3).map { corner -> text.scaledFillMarkAtCorner(corner, scale) }
                scaledTexts.forEach { scaled ->
                    assertEquals(expectedFont, scaled.fontSize, .00001f)
                    assertEquals(text.width * expectedFont / text.fontSize, scaled.width, .00001f)
                    assertEquals(text.height * expectedFont / text.fontSize, scaled.height, .00001f)
                    assertTrue(scaled.isValid())
                }
            }
        }
    }

    private fun pageCorners(overlay: PdfOverlay): List<PdfPoint> = documentSelectionGeometry(
        overlay.x, overlay.y, overlay.width, overlay.height, overlay.rotationDegrees,
        0f, 0f, 595f, 842f, 595f, 842f, 48f, 4f, 48f,
    ).corners.map { PdfPoint(it.x / 595f, it.y / 842f) }

    private fun assertCornersOnPage(overlay: PdfOverlay) {
        pageCorners(overlay).forEach {
            assertTrue("Corner outside page: $it", it.x in -.00001f..1.00001f && it.y in -.00001f..1.00001f)
        }
    }
}
