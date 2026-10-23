package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.ui.documentEditor.util.corners
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeManualCrop
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeManualCropEdge
import com.joseleandro.scaniqo.ui.documentEditor.util.transformManualCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentManualCropGeometryTest {
    private val crop = PdfCropBounds(.1f, .2f, .9f, .8f)

    @Test
    fun draggingTopLeftMovesBothAdjacentEdgesAndKeepsOppositeCorner() {
        val result = resizeManualCrop(crop, 0, .15f, .1f)
        assertEquals(.25f, result.left, .00001f)
        assertEquals(.3f, result.top, .00001f)
        assertEquals(crop.right, result.right, 0f)
        assertEquals(crop.bottom, result.bottom, 0f)
        val corners = result.corners()
        assertEquals(corners[0].x, corners[3].x, 0f)
        assertEquals(corners[0].y, corners[1].y, 0f)
    }

    @Test
    fun allFourCornersRemainRectangularWhenDragged() {
        for (corner in 0..3) {
            val result = resizeManualCrop(crop, corner, .05f, -.05f)
            val points = result.corners()
            assertEquals(points[0].y, points[1].y, 0f)
            assertEquals(points[1].x, points[2].x, 0f)
            assertEquals(points[2].y, points[3].y, 0f)
            assertEquals(points[3].x, points[0].x, 0f)
            assertTrue(result.isValid())
        }
    }

    @Test
    fun draggingPastTheOppositeCornerKeepsMinimumTouchArea() {
        val result = resizeManualCrop(crop, 0, 2f, 2f, .2f, .15f)
        assertEquals(.2f, result.right - result.left, .00001f)
        assertEquals(.15f, result.bottom - result.top, .00001f)
        assertEquals(crop.right, result.right, 0f)
        assertEquals(crop.bottom, result.bottom, 0f)
        assertTrue(result.isValid())
    }

    @Test
    fun draggingOutwardStopsAtTheSourceBounds() {
        assertEquals(
            PdfCropBounds(0f, 0f, crop.right, crop.bottom),
            resizeManualCrop(crop, 0, -5f, -5f),
        )
        assertEquals(
            PdfCropBounds(crop.left, crop.top, 1f, 1f),
            resizeManualCrop(crop, 2, 5f, 5f),
        )
    }

    @Test
    fun topRightAndBottomLeftOnlyMoveTheirAdjacentEdges() {
        val topRight = resizeManualCrop(crop, 1, -.1f, -.1f)
        assertEquals(crop.left, topRight.left, 0f)
        assertEquals(.1f, topRight.top, .00001f)
        assertEquals(.8f, topRight.right, .00001f)
        assertEquals(crop.bottom, topRight.bottom, 0f)
        val bottomLeft = resizeManualCrop(crop, 3, .1f, -.1f)
        assertEquals(.2f, bottomLeft.left, .00001f)
        assertEquals(.7f, bottomLeft.bottom, .00001f)
        assertEquals(crop.top, bottomLeft.top, 0f)
        assertEquals(crop.right, bottomLeft.right, 0f)
    }

    @Test
    fun movingSelectionKeepsSizeAndStopsAtImageEdges() {
        val moved = transformManualCrop(crop, 2f, -2f, 1f)
        assertEquals(.2f, moved.left, .00001f)
        assertEquals(1f, moved.right, .00001f)
        assertEquals(0f, moved.top, .00001f)
        assertEquals(.6f, moved.bottom, .00001f)
    }

    @Test
    fun pinchKeepsAspectRatioAndConstrainsSelectionToImage() {
        for (zoom in listOf(.001f, .5f, 1.5f, 100f)) {
            val result = transformManualCrop(crop, 0f, 0f, zoom, .15f, .1f)
            assertTrue(result.isValid())
            assertTrue(result.left >= 0f && result.top >= 0f && result.right <= 1f && result.bottom <= 1f)
            assertTrue(result.right - result.left >= .15f - .00001f)
            assertEquals(4f / 3f, (result.right - result.left) / (result.bottom - result.top), .0001f)
        }
    }

    @Test
    fun draggingAnEdgeKeepsPerpendicularEdgesAndOppositeEdge() {
        val top = resizeManualCropEdge(crop, 0, .3f, .1f)
        assertEquals(PdfCropBounds(.1f, .3f, .9f, .8f), top)
        val right = resizeManualCropEdge(crop, 1, -.1f, .3f)
        assertEquals(crop.top, right.top)
        assertEquals(crop.bottom, right.bottom)
        assertEquals(crop.left, right.left)
        assertEquals(.8f, right.right, .00001f)
        assertEquals(crop, resizeManualCropEdge(crop, 9, .2f, .3f))
    }

    @Test
    fun edgeDragDoesNotExpandTheOtherAxisOfAnExistingNarrowCrop() {
        val narrow = PdfCropBounds(.2f, .2f, .24f, .24f)
        for (edge in 0..3) {
            val result = resizeManualCropEdge(narrow, edge, .01f, .01f, .1f, .1f)
            if (edge % 2 == 0) {
                assertEquals(narrow.left, result.left, 0f)
                assertEquals(narrow.right, result.right, 0f)
            } else {
                assertEquals(narrow.top, result.top, 0f)
                assertEquals(narrow.bottom, result.bottom, 0f)
            }
            assertTrue(result.isValid())
        }
    }

    @Test
    fun invalidPinchCannotCorruptTheSelection() {
        assertEquals(crop, transformManualCrop(crop, Float.NaN, 0f, 1f))
        assertEquals(crop, transformManualCrop(crop, 0f, 0f, Float.POSITIVE_INFINITY))
        assertEquals(crop, transformManualCrop(crop, 0f, 0f, 0f))
    }

    @Test
    fun invalidInputCannotCreateAnInvalidRectangle() {
        assertEquals(crop, resizeManualCrop(crop, 4, .1f, .1f))
        assertEquals(crop, resizeManualCrop(crop, 0, Float.NaN, .1f))
        assertEquals(crop, resizeManualCrop(crop, 0, .1f, Float.POSITIVE_INFINITY))
    }
}
