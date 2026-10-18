package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.ui.documentEditor.util.corners
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeManualCrop
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
    fun invalidInputCannotCreateAnInvalidRectangle() {
        assertEquals(crop, resizeManualCrop(crop, 4, .1f, .1f))
        assertEquals(crop, resizeManualCrop(crop, 0, Float.NaN, .1f))
        assertEquals(crop, resizeManualCrop(crop, 0, .1f, Float.POSITIVE_INFINITY))
    }
}
