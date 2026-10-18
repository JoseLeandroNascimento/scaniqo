package com.joseleandro.scaniqo.ui.documentSignature

import com.joseleandro.scaniqo.domain.model.PdfPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSignatureViewportTest {
    @Test
    fun fitPageIsCenteredWithRoomForTheHandles() {
        val result = bounds(SignatureDocumentViewport())
        assertEquals(200f, result.page.center.x, .001f)
        assertEquals(300f, result.page.center.y, .001f)
        assertTrue(result.page.left >= 24f && result.page.top >= 24f)
        assertTrue(result.page.right <= 376f && result.page.bottom <= 576f)
        assertEquals(result.basePage.left, result.page.left, .001f)
        assertEquals(result.basePage.top, result.page.top, .001f)
        assertEquals(result.basePage.width, result.page.width, .001f)
        assertEquals(result.basePage.height, result.page.height, .001f)
    }

    @Test
    fun documentZoomIsLimitedToOneThroughThree() {
        assertEquals(1f, bounds(SignatureDocumentViewport(.5f)).viewport.zoom, 0f)
        val result = bounds(SignatureDocumentViewport(8f))
        assertEquals(3f, result.viewport.zoom, 0f)
        assertEquals(result.basePage.width * 3f, result.page.width, .001f)
        assertEquals(result.basePage.height * 3f, result.page.height, .001f)
    }

    @Test
    fun fittingPageCannotBeDraggedAway() {
        val result = panSignatureDocumentViewport(SignatureDocumentViewport(), 400f, -400f, 400f, 600f, 24f)
        assertEquals(SignatureDocumentViewport(), result)
    }

    @Test
    fun panningCanReachEveryEdgeButNotLeaveEmptySpaceBeyondTheMargin() {
        val positive = bounds(panSignatureDocumentViewport(SignatureDocumentViewport(2f), 10000f, 10000f, 400f, 600f, 24f))
        val negative = bounds(panSignatureDocumentViewport(SignatureDocumentViewport(2f), -10000f, -10000f, 400f, 600f, 24f))
        assertEquals(24f, positive.page.left, .001f)
        assertEquals(24f, positive.page.top, .001f)
        assertEquals(376f, negative.page.right, .001f)
        assertEquals(576f, negative.page.bottom, .001f)
    }

    @Test
    fun zoomKeepsTheVisibleSignatureAtTheSameScreenPosition() {
        val focus = PdfPoint(.65f, .6f)
        val before = bounds(SignatureDocumentViewport())
        val after = bounds(zoomSignatureDocumentViewport(before.viewport, 2f, 400f, 600f, 24f, focus))
        assertEquals(before.page.left + focus.x * before.page.width, after.page.left + focus.x * after.page.width, .001f)
        assertEquals(before.page.top + focus.y * before.page.height, after.page.top + focus.y * after.page.height, .001f)
    }

    @Test
    fun anOffscreenSignatureDoesNotPullTheDocumentAwayFromTheRegionBeingViewed() {
        val before = bounds(SignatureDocumentViewport(2f, .3f, .2f))
        val centerPoint = PdfPoint((200f - before.page.left) / before.page.width, (300f - before.page.top) / before.page.height)
        val after = bounds(zoomSignatureDocumentViewport(before.viewport, 2.5f, 400f, 600f, 24f, PdfPoint(99f, 99f)))
        assertEquals(200f, after.page.left + centerPoint.x * after.page.width, .001f)
        assertEquals(300f, after.page.top + centerPoint.y * after.page.height, .001f)
    }

    @Test
    fun panUsesPageCoordinatesSoDensityChangesPreserveTheVisibleRegion() {
        val value = SignatureDocumentViewport(2f, .2f, -.25f)
        val normal = bounds(value)
        val scaled = signatureDocumentViewportBounds(value, 800f, 1200f, 48f)
        assertEquals(normal.page.left * 2, scaled.page.left, .001f)
        assertEquals(normal.page.top * 2, scaled.page.top, .001f)
        assertEquals(normal.page.width * 2, scaled.page.width, .001f)
        assertEquals(normal.viewport, scaled.viewport)
    }

    @Test
    fun returningToOneHundredPercentAlsoClearsPan() {
        val value = zoomSignatureDocumentViewport(SignatureDocumentViewport(3f, .7f, -.7f), 1f, 400f, 600f, 24f)
        assertEquals(SignatureDocumentViewport(), value)
    }

    @Test
    fun consecutivePanEventsAccumulateWithoutWaitingForRecomposition() {
        val state = DocumentSignatureViewportState(SignatureDocumentViewport(2f))
        state.pan(20f, 30f, 400f, 600f, 24f)
        state.pan(20f, 30f, 400f, 600f, 24f)
        val before = bounds(SignatureDocumentViewport(2f))
        val after = bounds(state.value)
        assertEquals(40f, after.page.left - before.page.left, .001f)
        assertEquals(60f, after.page.top - before.page.top, .001f)
    }

    @Test
    fun invalidSavedValuesCannotEscapeTheZoomLimits() {
        val result = bounds(SignatureDocumentViewport(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN))
        assertEquals(SignatureDocumentViewport(), result.viewport)
        assertTrue(result.page.width.isFinite() && result.page.height.isFinite())
    }

    @Test
    fun panKeepsTheGestureKeyEvenWhenTranslatedRectWidthChangesByRounding() {
        val before = signatureDocumentViewportBounds(SignatureDocumentViewport(2f), 320f, 272f, 24f)
        val panned = panSignatureDocumentViewport(before.viewport, 5f, 0f, 320f, 272f, 24f)
        val after = signatureDocumentViewportBounds(panned, 320f, 272f, 24f)
        // Esses valores reproduzem a diferença de um ULP que cancelava pointerInput durante o arraste.
        assertNotEquals(before.page.width, after.page.width)
        assertEquals(5f, after.page.left - before.page.left, .001f)
        assertEquals(before.gestureKey, after.gestureKey)
    }

    @Test
    fun gestureKeyChangesWhenZoomOrCanvasGeometryChanges() {
        val value = SignatureDocumentViewport(2f)
        val before = signatureDocumentViewportBounds(value, 320f, 272f, 24f).gestureKey
        assertNotEquals(before, signatureDocumentViewportBounds(value.copy(zoom = 2.25f), 320f, 272f, 24f).gestureKey)
        assertNotEquals(before, signatureDocumentViewportBounds(value, 400f, 272f, 24f).gestureKey)
        assertNotEquals(before, signatureDocumentViewportBounds(value, 320f, 320f, 24f).gestureKey)
        assertNotEquals(before, signatureDocumentViewportBounds(value, 320f, 272f, 48f).gestureKey)
    }

    private fun bounds(value: SignatureDocumentViewport) =
        signatureDocumentViewportBounds(value, 400f, 600f, 24f)
}
