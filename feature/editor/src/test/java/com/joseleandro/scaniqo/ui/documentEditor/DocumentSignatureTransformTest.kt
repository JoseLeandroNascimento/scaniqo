package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.transformSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureAroundCenter
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureFromHandle
import com.joseleandro.scaniqo.ui.documentEditor.util.resizeSignatureFromCorner
import com.joseleandro.scaniqo.ui.documentEditor.util.rotateSignatureFromHandle
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureWidthRange
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSignatureTransformTest {
    private val placement = PdfSignaturePlacement("signature", "file:///signature.png", .2f, .5f, .4f, .1f)

    @Test fun scalingPreservesImageAspectAndIdentity() {
        val resized = transformSignaturePlacement(placement, width = .6f)
        assertEquals(.6f, resized.width, .0001f)
        assertEquals(.15f, resized.height, .0001f)
        assertEquals(placement.id, resized.id)
        assertEquals(placement.imageUri, resized.imageUri)
    }

    @Test fun everyRotatedCornerRemainsInsidePageAfterDraggingToAnEdge() {
        for (rotation in listOf(-180f, -135f, -90f, -45f, 0f, 45f, 90f, 135f, 180f)) {
            for (x in listOf(-2f, 2f)) for (y in listOf(-2f, 2f)) {
                val result = transformSignaturePlacement(placement, x, y, width = 2f, rotationDegrees = rotation)
                val pageAspect = 595f / 842f
                val angle = Math.toRadians(result.rotationDegrees.toDouble())
                val halfWidth = result.width * pageAspect / 2f
                val halfHeight = result.height / 2f
                for (dx in listOf(-halfWidth, halfWidth)) for (dy in listOf(-halfHeight, halfHeight)) {
                    val cornerX = (result.x + result.width / 2f) * pageAspect + dx * cos(angle) - dy * sin(angle)
                    val cornerY = result.y + result.height / 2f + dx * sin(angle) + dy * cos(angle)
                    assertTrue("Horizontal edge at $rotation degrees", cornerX >= -.00001 && cornerX <= pageAspect + .00001)
                    assertTrue("Vertical edge at $rotation degrees", cornerY >= -.00001 && cornerY <= 1.00001)
                }
            }
        }
    }

    @Test fun invalidGestureValuesCannotProduceInvalidGeometry() {
        val result = transformSignaturePlacement(placement, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Float.NEGATIVE_INFINITY)
        assertTrue(listOf(result.x, result.y, result.width, result.height, result.rotationDegrees).all(Float::isFinite))
        assertEquals(placement.width, result.width, .0001f)
        assertEquals(placement.height, result.height, .0001f)
    }

    @Test fun sizeControlKeepsCenterEvenWhenRequestedWidthExceedsTheLimit() {
        val centered = placement.copy(x = .3f)
        val result = resizeSignatureAroundCenter(centered, width = 10f)
        assertEquals(signatureWidthRange(centered).endInclusive, result.width, .000001f)
        assertEquals(centered.x + centered.width / 2, result.x + result.width / 2, .000001f)
        assertEquals(centered.y + centered.height / 2, result.y + result.height / 2, .000001f)
    }

    @Test fun handleDragOutwardGrowsAlongTheRotatedDiagonal() {
        val rotated = placement.copy(rotationDegrees = 90f)
        val halfWidth = rotated.width * 595f / 2
        val halfHeight = rotated.height * 842f / 2
        val result = resizeSignatureFromHandle(rotated, -halfHeight / 2, halfWidth / 2, 595f, 842f)
        assertEquals(.6f, result.width, .000001f)
        assertEquals(rotated.x + rotated.width / 2, result.x + result.width / 2, .000001f)
        assertEquals(rotated.y + rotated.height / 2, result.y + result.height / 2, .000001f)
        assertEquals(90f, result.rotationDegrees, .000001f)
    }

    @Test fun sidewaysHandleMovementDoesNotAccidentallyEnlargeSignature() {
        val dx = placement.width * 595f / 2
        val dy = placement.height * 842f / 2
        val result = resizeSignatureFromHandle(placement, -dy, dx, 595f, 842f)
        assertEquals(placement.width, result.width, .000001f)
    }

    @Test fun draggingHandlePastCenterStaysSmallInsteadOfGrowingAgain() {
        val dx = placement.width * 595f / 2
        val dy = placement.height * 842f / 2
        val result = resizeSignatureFromHandle(placement, -dx * 3, -dy * 3, 595f, 842f)
        assertEquals(signatureWidthRange(placement).start, result.width, .000001f)
    }

    @Test fun unavailablePageDimensionsDoNotChangeHandleGeometry() {
        val result = resizeSignatureFromHandle(placement, 10f, 10f, 0f, 0f)
        assertEquals(placement, result)
    }

    @Test fun rotationWrapsWithoutChangingAspect() {
        val result = transformSignaturePlacement(placement, rotationDegrees = 765f)
        assertEquals(45f, result.rotationDegrees, .0001f)
        assertTrue(abs(result.height / result.width - placement.height / placement.width) < .0001f)
    }

    @Test fun explicitHeightChangesOnlyHeightAndOptInControlsStretching() {
        val result = transformSignaturePlacement(placement, height = .18f, stretchToBounds = true)
        assertEquals(placement.width, result.width, .000001f)
        assertEquals(.18f, result.height, .000001f)
        assertTrue(result.stretchToBounds)
        assertEquals(false, transformSignaturePlacement(placement, height = .18f).stretchToBounds)
    }

    @Test fun everyCornerChangesWidthAndHeightIndependentlyAroundItsOppositeAnchor() {
        for (rotation in listOf(-70f, 0f, 45f, 90f, 135f)) for (index in 0..3) {
            val start = placement.copy(x = .3f, y = .4f, rotationDegrees = rotation)
            val signX = if (index == 0 || index == 3) -1f else 1f
            val signY = if (index < 2) -1f else 1f
            val angle = Math.toRadians(rotation.toDouble())
            for ((widthChange, heightChange) in listOf(.08f to 0f, 0f to .04f, .08f to .04f)) {
                val localX = signX * widthChange * 595f
                val localY = signY * heightChange * 842f
                val dx = (localX * cos(angle) - localY * sin(angle)).toFloat()
                val dy = (localX * sin(angle) + localY * cos(angle)).toFloat()
                val result = resizeSignatureFromCorner(start, index, dx, dy, 595f, 842f)
                assertEquals(start.width + widthChange, result.width, .000001f)
                assertEquals(start.height + heightChange, result.height, .000001f)
                assertPointEquals(corner(start, (index + 2) % 4), corner(result, (index + 2) % 4))
                assertTrue(result.stretchToBounds)
                assertInsidePage(result)
            }
        }
    }

    @Test fun cornerGestureUsesRenderedPageScaleWithoutChangingItsResult() {
        val large = resizeSignatureFromCorner(placement, 2, 59.5f, 42.1f, 595f, 842f)
        val small = resizeSignatureFromCorner(placement, 2, 29.75f, 21.05f, 297.5f, 421f)
        assertEquals(large.width, small.width, .000001f)
        assertEquals(large.height, small.height, .000001f)
        assertEquals(large.x, small.x, .000001f)
        assertEquals(large.y, small.y, .000001f)
    }

    @Test fun rotatedResizingStopsAtPageEdgesWithoutMovingTheOppositeCorner() {
        for (rotation in listOf(-135f, -90f, -45f, 0f, 45f, 90f, 135f)) {
            for (index in 0..3) for (x in listOf(-1f, 2f)) for (y in listOf(-1f, 2f)) {
                val start = transformSignaturePlacement(placement, x = x, y = y, rotationDegrees = rotation)
                for ((dx, dy) in listOf(-2000f to -2000f, 2000f to -2000f, 2000f to 2000f, -2000f to 2000f)) {
                    val result = resizeSignatureFromCorner(start, index, dx, dy, 595f, 842f)
                    assertInsidePage(result)
                    assertPointEquals(corner(start, (index + 2) % 4), corner(result, (index + 2) % 4))
                }
            }
        }
    }

    @Test fun draggingAcrossOppositeCornerNeverFlipsTheRectangle() {
        for (index in 0..3) {
            val signX = if (index == 0 || index == 3) -1f else 1f
            val signY = if (index < 2) -1f else 1f
            val result = resizeSignatureFromCorner(placement, index, -signX * 2000f, -signY * 2000f, 595f, 842f)
            assertTrue(result.width > 0f && result.width < placement.width)
            assertTrue(result.height > 0f && result.height < placement.height)
            assertPointEquals(corner(placement, (index + 2) % 4), corner(result, (index + 2) % 4))
            assertInsidePage(result)
        }
    }

    @Test fun proportionalPinchPreservesTheNewStretchedAspectAndCenter() {
        val stretched = placement.copy(width = .25f, height = .2f, stretchToBounds = true)
        val result = resizeSignatureAroundCenter(stretched, width = .35f)
        assertEquals(.28f, result.height, .000001f)
        assertEquals(stretched.x + stretched.width / 2f, result.x + result.width / 2f, .000001f)
        assertEquals(stretched.y + stretched.height / 2f, result.y + result.height / 2f, .000001f)
        assertTrue(result.stretchToBounds)
    }

    @Test fun rotationHandleTracksAllFourDirectionsAndPreservesCenter() {
        val centerX = (placement.x + placement.width / 2f) * 595f
        val centerY = (placement.y + placement.height / 2f) * 842f
        val pointers = listOf(
            Triple(0f, -100f, 0f), Triple(100f, 0f, 90f),
            Triple(0f, 100f, -180f), Triple(-100f, 0f, -90f),
        )
        pointers.forEach { (dx, dy, expected) ->
            val result = rotateSignatureFromHandle(placement, centerX + dx, centerY + dy, 595f, 842f)
            assertEquals(expected, result.rotationDegrees, .0001f)
            assertEquals(centerX, (result.x + result.width / 2f) * 595f, .0001f)
            assertEquals(centerY, (result.y + result.height / 2f) * 842f, .0001f)
            assertEquals(placement.height / placement.width, result.height / result.width, .000001f)
            assertInsidePage(result)
        }
    }

    @Test fun tinySignaturesAndInvalidInputsCannotProduceNonFiniteOrInvertedBounds() {
        val tiny = placement.copy(width = .0001f, height = .00001f)
        assertInsidePage(resizeSignatureFromCorner(tiny, 2, -1000f, -1000f, 595f, 842f))
        assertEquals(placement, resizeSignatureFromCorner(placement, -1, 10f, 10f, 595f, 842f))
        assertEquals(placement, resizeSignatureFromCorner(placement, 2, Float.NaN, 10f, 595f, 842f))
        assertEquals(placement, resizeSignatureFromCorner(placement, 2, 10f, 10f, 0f, 842f))
        assertEquals(placement, resizeSignatureFromCorner(placement, 2, 0f, 0f, 595f, 842f))
        assertEquals(placement, rotateSignatureFromHandle(placement, Float.POSITIVE_INFINITY, 10f, 595f, 842f))
        assertEquals(placement, rotateSignatureFromHandle(
            placement, (placement.x + placement.width / 2f) * 595f,
            (placement.y + placement.height / 2f) * 842f, 595f, 842f,
        ))
        assertInsidePage(transformSignaturePlacement(placement, width = Float.NaN, height = Float.NaN))
    }

    private fun corner(value: PdfSignaturePlacement, index: Int): Pair<Double, Double> {
        val signX = if (index == 0 || index == 3) -1 else 1
        val signY = if (index < 2) -1 else 1
        val x = signX * value.width * 595.0 / 2
        val y = signY * value.height * 842.0 / 2
        val angle = Math.toRadians(value.rotationDegrees.toDouble())
        return ((value.x + value.width / 2f) * 595.0 + x * cos(angle) - y * sin(angle)) to
            ((value.y + value.height / 2f) * 842.0 + x * sin(angle) + y * cos(angle))
    }

    private fun assertPointEquals(expected: Pair<Double, Double>, actual: Pair<Double, Double>) {
        assertEquals(expected.first, actual.first, .003)
        assertEquals(expected.second, actual.second, .003)
    }

    private fun assertInsidePage(value: PdfSignaturePlacement) {
        assertTrue(listOf(value.x, value.y, value.width, value.height, value.rotationDegrees).all(Float::isFinite))
        assertTrue(value.width > 0f && value.height > 0f)
        assertTrue(value.x >= -.00001f && value.y >= -.00001f)
        assertTrue(value.x + value.width <= 1.00001f && value.y + value.height <= 1.00001f)
        for (index in 0..3) {
            val corner = corner(value, index)
            assertTrue("x=${corner.first}, placement=$value", corner.first in -.003..595.003)
            assertTrue("y=${corner.second}, placement=$value", corner.second in -.003..842.003)
        }
    }
}
