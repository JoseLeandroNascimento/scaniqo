package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureSelectionGeometry
import com.joseleandro.scaniqo.ui.documentEditor.util.SignatureSelectionGeometry
import com.joseleandro.scaniqo.ui.documentEditor.util.signatureContextToolbarCenter
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

    @Test
    fun contextToolbarPrefersBelowTheSignatureAndAvoidsAllManipulationHandles() {
        val geometry = signatureSelectionGeometry(
            placement(.35f, .1f), 24f, 24f, 280f, 400f, 328f, 448f, 48f, 4f, 48f,
        )
        val menu = signatureContextToolbarCenter(geometry, 328f, 448f, 48f, 48f, 48f, 4f)
        assertTrue(menu.y - 24f >= geometry.corners.maxOf { it.y } + 3.99f)
        assertContextTarget(menu, geometry.cornerHandles + geometry.rotationHandle, 328f, 448f)
    }

    @Test
    fun contextToolbarRemainsAccessibleAtPageEdgesAndFineRotationAngles() {
        for (rotation in listOf(-135f, -90f, -37f, 0f, 28f, 90f, 135f)) {
            for (x in listOf(0f, .45f, .9f)) {
                for (y in listOf(0f, .45f, .9f)) {
                    val geometry = signatureSelectionGeometry(
                        placement(.1f, .05f).copy(x = x, y = y, rotationDegrees = rotation),
                        24f, 24f, 200f, 280f, 248f, 328f, 48f, 4f, 48f,
                    )
                    val menu = signatureContextToolbarCenter(geometry, 248f, 328f, 48f, 48f, 48f, 4f)
                    assertContextTarget(menu, geometry.cornerHandles + geometry.rotationHandle, 248f, 328f)
                    val outside = menu.x + 24f <= geometry.corners.minOf { it.x } ||
                        menu.x - 24f >= geometry.corners.maxOf { it.x } ||
                        menu.y + 24f <= geometry.corners.minOf { it.y } ||
                        menu.y - 24f >= geometry.corners.maxOf { it.y }
                    assertTrue("Menu overlays signature at rotation=$rotation, x=$x, y=$y", outside)
                }
            }
        }
    }

    @Test
    fun contextToolbarUsesUpperSpaceWhenSignatureTouchesTheBottom() {
        val geometry = signatureSelectionGeometry(
            placement(.55f, .08f).copy(x = .25f, y = .92f),
            0f, 0f, 320f, 450f, 320f, 450f, 48f, 4f, 48f,
        )
        val menu = signatureContextToolbarCenter(geometry, 320f, 450f, 48f, 48f, 48f, 4f)
        assertTrue(menu.y + 24f <= geometry.corners.minOf { it.y } - 3.99f)
        assertContextTarget(menu, geometry.cornerHandles + geometry.rotationHandle, 320f, 450f)
    }

    @Test
    fun contextToolbarDoesNotStealHandleTargetsInANarrowViewport() {
        for (rotation in listOf(0f, 45f, 90f, 135f)) {
            val geometry = signatureSelectionGeometry(
                placement(.05f, .01f).copy(rotationDegrees = rotation),
                0f, 0f, 160f, 220f, 160f, 220f, 48f, 4f, 48f,
            )
            val menu = signatureContextToolbarCenter(geometry, 160f, 220f, 48f, 48f, 48f, 4f)
            assertContextTarget(menu, geometry.cornerHandles + geometry.rotationHandle, 160f, 220f)
        }
    }

    @Test
    fun fullWidthToolbarUsesItsRealBoundsToAvoidHandlesAtEveryPageEdge() {
        for (rotation in listOf(-135f, -90f, -25f, 0f, 45f, 90f)) {
            for (x in listOf(0f, .45f, .9f)) for (y in listOf(0f, .45f, .9f)) {
                val geometry = signatureSelectionGeometry(
                    placement(.1f, .05f).copy(x = x, y = y, rotationDegrees = rotation),
                    24f, 24f, 280f, 400f, 328f, 448f, 48f, 8f, 32f,
                )
                val toolbar = signatureContextToolbarCenter(geometry, 328f, 448f, 304f, 56f, 48f, 8f)
                assertToolbarBounds(toolbar, 304f, 56f, geometry, 328f, 448f)
            }
        }
    }

    @Test
    fun toolbarFitsNarrowWindowsWithoutCoveringTheSignatureOrItsHandles() {
        for (rotation in listOf(-45f, 0f, 45f, 90f)) {
            val geometry = signatureSelectionGeometry(
                placement(.12f, .04f).copy(rotationDegrees = rotation),
                0f, 0f, 240f, 360f, 240f, 360f, 48f, 8f, 32f,
            )
            val toolbar = signatureContextToolbarCenter(geometry, 240f, 360f, 224f, 56f, 48f, 8f)
            assertToolbarBounds(toolbar, 224f, 56f, geometry, 240f, 360f)
        }
    }

    @Test
    fun toolbarPlacementUsesMeasuredHeightForLargeContent() {
        val geometry = signatureSelectionGeometry(
            placement(.35f, .1f).copy(x = .25f, y = .7f),
            0f, 0f, 360f, 520f, 360f, 520f, 48f, 8f, 32f,
        )
        for (width in listOf(256f, 304f)) for (height in listOf(56f, 80f)) {
            val toolbar = signatureContextToolbarCenter(geometry, 360f, 520f, width, height, 48f, 8f)
            assertToolbarBounds(toolbar, width, height, geometry, 360f, 520f)
        }
    }

    @Test
    fun toolbarRemainsInTheViewportWhenZoomMovesTheSelectionOutsideIt() {
        val geometry = signatureSelectionGeometry(
            placement(.3f, .1f).copy(x = .05f, y = .05f, rotationDegrees = -15f),
            -200f, -250f, 720f, 1020f, 360f, 520f, 48f, 8f, 32f,
        )
        val toolbar = signatureContextToolbarCenter(geometry, 360f, 520f, 304f, 56f, 48f, 8f)
        assertToolbarBounds(toolbar, 304f, 56f, geometry, 360f, 520f)
    }

    @Test
    fun toolbarChoosesNearbySpaceAboveInsteadOfADistantSpotBelow() {
        val geometry = SignatureSelectionGeometry(
            corners = listOf(PdfPoint(100f, 200f), PdfPoint(200f, 200f), PdfPoint(200f, 240f), PdfPoint(100f, 240f)),
            cornerHandles = listOf(PdfPoint(80f, 270f), PdfPoint(220f, 270f), PdfPoint(80f, 330f), PdfPoint(220f, 330f)),
            topMiddle = PdfPoint(150f, 200f),
            rotationHandle = PdfPoint(150f, 390f),
        )
        val toolbar = signatureContextToolbarCenter(geometry, 360f, 520f, 304f, 56f, 48f, 8f)
        assertEquals(164f, toolbar.y, .001f)
        assertToolbarBounds(toolbar, 304f, 56f, geometry, 360f, 520f)
    }

    private fun assertToolbarBounds(
        toolbar: PdfPoint,
        toolbarWidth: Float,
        toolbarHeight: Float,
        geometry: SignatureSelectionGeometry,
        viewportWidth: Float,
        viewportHeight: Float,
    ) {
        val halfWidth = toolbarWidth / 2f
        val halfHeight = toolbarHeight / 2f
        assertTrue("Toolbar clipped horizontally: $toolbar", toolbar.x >= halfWidth && toolbar.x <= viewportWidth - halfWidth)
        assertTrue("Toolbar clipped vertically: $toolbar", toolbar.y >= halfHeight && toolbar.y <= viewportHeight - halfHeight)
        (geometry.cornerHandles + geometry.rotationHandle).forEach { handle ->
            assertTrue(
                "Toolbar $toolbar overlaps handle $handle",
                abs(toolbar.x - handle.x) >= halfWidth + 24f + 7.99f ||
                    abs(toolbar.y - handle.y) >= halfHeight + 24f + 7.99f,
            )
        }
        assertTrue(
            "Toolbar overlays the signature",
            toolbar.x + halfWidth <= geometry.corners.minOf { it.x } ||
                toolbar.x - halfWidth >= geometry.corners.maxOf { it.x } ||
                toolbar.y + halfHeight <= geometry.corners.minOf { it.y } ||
                toolbar.y - halfHeight >= geometry.corners.maxOf { it.y },
        )
    }

    private fun assertContextTarget(menu: PdfPoint, handles: List<PdfPoint>, width: Float, height: Float) {
        assertTrue("Context target clipped horizontally: $menu", menu.x >= 24f && menu.x <= width - 24f)
        assertTrue("Context target clipped vertically: $menu", menu.y >= 24f && menu.y <= height - 24f)
        handles.forEach { handle ->
            assertTrue(
                "Context menu $menu overlaps handle $handle",
                abs(menu.x - handle.x) >= 51.99f || abs(menu.y - handle.y) >= 51.99f,
            )
        }
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
