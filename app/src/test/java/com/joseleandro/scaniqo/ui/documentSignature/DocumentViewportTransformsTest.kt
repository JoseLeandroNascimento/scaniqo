package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.ui.geometry.Offset
import com.joseleandro.scaniqo.domain.model.PdfPoint
import org.junit.Assert.*
import org.junit.Test

class DocumentViewportTransformsTest {
    @Test fun pinchKeepsTheDocumentPointUnderTheFingers() {
        val before = signatureDocumentViewportBounds(SignatureDocumentViewport(2f), 400f, 600f, 12f, 6f)
        val centroid = Offset(170f, 270f)
        val point = PdfPoint((centroid.x - before.page.left) / before.page.width,
            (centroid.y - before.page.top) / before.page.height)
        val next = transformSignatureDocumentViewport(before.viewport, centroid, Offset.Zero, 1.3f, 400f, 600f, 12f, 6f)
        val after = signatureDocumentViewportBounds(next, 400f, 600f, 12f, 6f)
        assertEquals(centroid.x, after.page.left + point.x * after.page.width, .001f)
        assertEquals(centroid.y, after.page.top + point.y * after.page.height, .001f)
    }

    @Test fun pinchAndPanApplyTogether() {
        val before = signatureDocumentViewportBounds(SignatureDocumentViewport(2f), 400f, 600f, 12f)
        val next = transformSignatureDocumentViewport(before.viewport, Offset(200f, 300f),
            Offset(20f, -30f), 1.2f, 400f, 600f, 12f)
        val after = signatureDocumentViewportBounds(next, 400f, 600f, 12f)
        assertEquals(220f, after.page.center.x, .001f)
        assertEquals(270f, after.page.center.y, .001f)
    }

    @Test fun typingFocusCentersAVisibleInteriorPointAfterKeyboardResize() {
        val point = PdfPoint(.4f, .75f)
        val next = focusSignatureDocumentViewport(SignatureDocumentViewport(), point, 5f, 400f, 230f, 12f, 6f)
        val bounds = signatureDocumentViewportBounds(next, 400f, 230f, 12f, 6f)
        assertEquals(200f, bounds.page.left + point.x * bounds.page.width, .001f)
        assertEquals(115f, bounds.page.top + point.y * bounds.page.height, .001f)
    }

    @Test fun optionalSixTimesZoomDoesNotChangeSignatureDefault() {
        val extended = transformSignatureDocumentViewport(SignatureDocumentViewport(), Offset(200f, 300f),
            Offset.Zero, 20f, 400f, 600f, 12f, 6f)
        val legacy = transformSignatureDocumentViewport(SignatureDocumentViewport(), Offset(200f, 300f),
            Offset.Zero, 20f, 400f, 600f, 12f)
        assertEquals(6f, extended.zoom, 0f)
        assertEquals(3f, legacy.zoom, 0f)
    }

    @Test fun invalidGestureValuesCannotPoisonViewport() {
        val result = transformSignatureDocumentViewport(SignatureDocumentViewport(2f), Offset(Float.NaN, Float.NaN),
            Offset(Float.NaN, Float.POSITIVE_INFINITY), Float.NaN, 400f, 600f, 12f)
        assertTrue(result.zoom.isFinite() && result.panX.isFinite() && result.panY.isFinite())
        assertEquals(2f, result.zoom, 0f)
    }

    @Test fun focusNearPageEdgeRemainsBounded() {
        val result = focusSignatureDocumentViewport(SignatureDocumentViewport(), PdfPoint(1f, 1f),
            6f, 400f, 230f, 12f, 6f)
        val bounds = signatureDocumentViewportBounds(result, 400f, 230f, 12f, 6f)
        assertTrue(bounds.page.right >= 388f - .01f)
        assertTrue(bounds.page.bottom >= 218f - .01f)
    }
}
