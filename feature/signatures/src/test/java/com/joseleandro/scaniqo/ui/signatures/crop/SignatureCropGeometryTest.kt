package com.joseleandro.scaniqo.ui.signatures.crop

import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureCropGeometryTest {
    private val crop = PdfSignatureCrop(.2f, .3f, .8f, .9f)

    @Test
    fun cornerDragKeepsOppositeCornerAndStopsAtTheImageEdges() {
        val moved = SignatureCropGeometry.drag(crop, SignatureCropHandle.TOP_LEFT, -.5f, -.8f)
        assertCrop(PdfSignatureCrop(0f, 0f, .8f, .9f), moved)
        val lower = SignatureCropGeometry.drag(crop, SignatureCropHandle.BOTTOM_RIGHT, 4f, 6f)
        assertCrop(PdfSignatureCrop(.2f, .3f, 1f, 1f), lower)
    }

    @Test
    fun edgeHandlesOnlyChangeTheirOwnAxis() {
        assertCrop(
            crop.copy(left = .3f),
            SignatureCropGeometry.drag(crop, SignatureCropHandle.LEFT, .1f, .5f),
        )
        assertCrop(
            crop.copy(top = .4f),
            SignatureCropGeometry.drag(crop, SignatureCropHandle.TOP, .5f, .1f),
        )
        assertCrop(
            crop.copy(right = .7f),
            SignatureCropGeometry.drag(crop, SignatureCropHandle.RIGHT, -.1f, -.5f),
        )
        assertCrop(
            crop.copy(bottom = .8f),
            SignatureCropGeometry.drag(crop, SignatureCropHandle.BOTTOM, -.5f, -.1f),
        )
    }

    @Test
    fun movingSelectionPreservesItsSizeAtEveryImageBoundary() {
        val moved = SignatureCropGeometry.drag(crop, SignatureCropHandle.MOVE, 5f, -5f)
        assertCrop(PdfSignatureCrop(.4f, 0f, 1f, .6f), moved)
        val opposite = SignatureCropGeometry.drag(moved, SignatureCropHandle.MOVE, -5f, 5f)
        assertCrop(PdfSignatureCrop(0f, .4f, .6f, 1f), opposite)
    }

    @Test
    fun draggingThroughOppositeCornerCannotInvertOrCollapseSelection() {
        listOf(
            SignatureCropHandle.TOP_LEFT to (5f to 5f),
            SignatureCropHandle.TOP_RIGHT to (-5f to 5f),
            SignatureCropHandle.BOTTOM_LEFT to (5f to -5f),
            SignatureCropHandle.BOTTOM_RIGHT to (-5f to -5f),
        ).forEach { (handle, delta) ->
            val result = SignatureCropGeometry.drag(crop, handle, delta.first, delta.second)
            assertTrue(result.isValid)
            assertEquals(SignatureCropGeometry.minimumSize, result.right - result.left, .00001f)
            assertEquals(SignatureCropGeometry.minimumSize, result.bottom - result.top, .00001f)
        }
    }

    @Test
    fun allEightHandlesUsePixelDistanceForNonSquareImages() {
        val selection = PdfSignatureCrop(.25f, .25f, .75f, .75f)
        val targets = listOf(
            Triple(SignatureCropHandle.TOP_LEFT, 250f, 100f),
            Triple(SignatureCropHandle.TOP, 500f, 100f),
            Triple(SignatureCropHandle.TOP_RIGHT, 750f, 100f),
            Triple(SignatureCropHandle.RIGHT, 750f, 200f),
            Triple(SignatureCropHandle.BOTTOM_RIGHT, 750f, 300f),
            Triple(SignatureCropHandle.BOTTOM, 500f, 300f),
            Triple(SignatureCropHandle.BOTTOM_LEFT, 250f, 300f),
            Triple(SignatureCropHandle.LEFT, 250f, 200f),
        )
        targets.forEach { (handle, x, y) ->
            assertEquals(handle, SignatureCropGeometry.handleAt(selection, x, y, 1000f, 400f, 24f))
        }
        assertEquals(SignatureCropHandle.MOVE, SignatureCropGeometry.handleAt(selection, 500f, 200f, 1000f, 400f, 24f))
        assertNull(SignatureCropGeometry.handleAt(selection, 500f, 10f, 1000f, 400f, 24f))
    }

    @Test
    fun cornersRemainReachableOutsideTheImageAndWinOverNearbyEdges() {
        val full = SignatureCropGeometry.fullImage
        assertEquals(SignatureCropHandle.TOP_LEFT, SignatureCropGeometry.handleAt(full, -12f, -12f, 300f, 100f, 24f))
        assertEquals(SignatureCropHandle.BOTTOM_RIGHT, SignatureCropGeometry.handleAt(full, 285f, 95f, 300f, 100f, 24f))
        assertNull(SignatureCropGeometry.handleAt(full, -25f, -25f, 300f, 100f, 24f))
    }

    @Test
    fun invalidCoordinatesNeverProduceInvalidCrops() {
        val unchanged = SignatureCropGeometry.drag(crop, SignatureCropHandle.TOP_LEFT, Float.NaN, 0f)
        assertEquals(crop, unchanged)
        assertEquals(crop, SignatureCropGeometry.drag(crop, SignatureCropHandle.MOVE, 0f, Float.POSITIVE_INFINITY))
        val invalid = crop.copy(left = Float.NaN)
        assertFalse(invalid.isValid)
        assertEquals(SignatureCropGeometry.fullImage, SignatureCropGeometry.drag(invalid, SignatureCropHandle.MOVE, 0f, 0f))
        assertNull(SignatureCropGeometry.handleAt(crop, Float.NaN, 0f, 100f, 100f, 24f))
        assertNull(SignatureCropGeometry.handleAt(crop, 0f, 0f, 0f, 100f, 24f))
    }

    @Test
    fun croppingProducesOriginalPixelBoundsWithoutChangingAspectCoordinates() {
        val moved = SignatureCropGeometry.drag(
            SignatureCropGeometry.fullImage, SignatureCropHandle.TOP_LEFT,
            deltaX = 100f / 1000f, deltaY = 40f / 400f,
        )
        val pixels = moved.pixels(4000, 1600)
        assertEquals(400, pixels.x)
        assertEquals(160, pixels.y)
        assertEquals(3600, pixels.width)
        assertEquals(1440, pixels.height)
    }

    private fun assertCrop(expected: PdfSignatureCrop, actual: PdfSignatureCrop) {
        assertEquals(expected.left, actual.left, .00001f)
        assertEquals(expected.top, actual.top, .00001f)
        assertEquals(expected.right, actual.right, .00001f)
        assertEquals(expected.bottom, actual.bottom, .00001f)
        assertTrue(actual.isValid)
    }
}
