package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureSelectionGeometry
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSignatureSelectionGeometryTest {
    @Test
    fun selectionFollowsTheActualRectangleAndPageOrigin() {
        val value = placement(width = .5f, height = .2f).copy(x = .25f, y = .3f)
        val result = signatureSelectionGeometry(value, 20f, 30f, 200f, 300f, 280f, 420f, 48f, 4f, 48f)
        assertPoint(PdfPoint(70f, 120f), result.corners[0])
        assertPoint(PdfPoint(170f, 180f), result.corners[2])
        assertPoint(PdfPoint(120f, 120f), result.topMiddle)
    }

    @Test
    fun thinSignatureKeepsItsActualHeightWhileTargetsSeparate() {
        val value = placement(width = .7f, height = .01f)
        val result = signatureSelectionGeometry(value, 24f, 24f, 280f, 400f, 328f, 448f, 48f, 4f, 48f)
        assertEquals(4f, result.corners[3].y - result.corners[0].y, .001f)
        assertTrue(result.cornerHandles[3].y - result.cornerHandles[0].y >= 52f)
        assertTargets(result.cornerHandles + result.rotationHandle, 328f, 448f)
    }

    @Test
    fun rotatedSmallAndThinSignaturesKeepAllFiveTargetsApart() {
        for (rotation in listOf(-179f, -135f, -90f, -45f, 0f, 15f, 45f, 90f, 135f, 179f)) {
            for ((width, height) in listOf(.04f to .01f, .2f to .02f, .7f to .08f)) {
                val value = placement(width, height).copy(rotationDegrees = rotation)
                val result = signatureSelectionGeometry(value, 24f, 24f, 240f, 340f, 288f, 388f, 48f, 4f, 48f)
                assertTargets(result.cornerHandles + result.rotationHandle, 288f, 388f)
            }
        }
    }

    @Test
    fun compactCanvasStillHasFiveDistinctTouchTargets() {
        for (rotation in listOf(0f, 45f, 90f, 135f)) {
            val result = signatureSelectionGeometry(
                placement(.05f, .01f).copy(rotationDegrees = rotation),
                0f, 0f, 160f, 160f, 160f, 160f, 48f, 4f, 48f,
            )
            assertTargets(result.cornerHandles + result.rotationHandle, 160f, 160f)
        }
    }

    @Test
    fun pageEdgeNeverClipsTheTargets() {
        for (x in listOf(0f, .45f, .9f)) {
            for (y in listOf(0f, .45f, .9f)) {
                val result = signatureSelectionGeometry(
                    placement(.1f, .05f).copy(x = x, y = y, rotationDegrees = 25f),
                    24f, 24f, 200f, 280f, 248f, 328f, 48f, 4f, 48f,
                )
                assertTargets(result.cornerHandles + result.rotationHandle, 248f, 328f)
            }
        }
    }

    @Test
    fun quarterTurnPlacesTheTopStemOnTheRightOfTheCenter() {
        val value = placement(.4f, .1f).copy(x = .3f, y = .4f, rotationDegrees = 90f)
        val result = signatureSelectionGeometry(value, 0f, 0f, 300f, 420f, 300f, 420f, 48f, 4f, 48f)
        assertPoint(PdfPoint(171f, 189f), result.topMiddle)
        assertTrue(result.rotationHandle.x > result.topMiddle.x)
    }

    private fun placement(width: Float, height: Float) = PdfSignaturePlacement(
        id = "signature", imageUri = "content://signature", x = .4f, y = .5f, width = width, height = height,
    )

    private fun assertPoint(expected: PdfPoint, actual: PdfPoint) {
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
    }

    private fun assertTargets(points: List<PdfPoint>, width: Float, height: Float) {
        assertEquals(5, points.size)
        points.forEach {
            assertTrue("Target left/right clipped: $it", it.x >= 24f && it.x <= width - 24f)
            assertTrue("Target top/bottom clipped: $it", it.y >= 24f && it.y <= height - 24f)
        }
        for (first in points.indices) {
            for (second in first + 1 until points.size) {
                assertTrue(
                    "Overlapping targets: ${points[first]} and ${points[second]}",
                    abs(points[first].x - points[second].x) >= 51.99f ||
                        abs(points[first].y - points[second].y) >= 51.99f,
                )
            }
        }
    }
}
