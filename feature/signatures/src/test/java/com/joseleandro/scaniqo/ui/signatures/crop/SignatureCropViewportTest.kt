package com.joseleandro.scaniqo.ui.signatures.crop

import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureCropViewportTest {
    private val original = PdfSignatureCrop(.1f, .2f, .9f, .8f)

    @Test fun zoomChangesSelectedSourceRegionWhileKeepingTheScreenFrameFixed() {
        val before = SignatureCropViewport()
        val result = before.transform(original, 2f, 0f, 0f, .5f, .5f)
        assertEquals(2f, result.viewport.scale, .00001f)
        assertEquals(PdfSignatureCrop(.3f, .35f, .7f, .65f), result.crop)
        assertFrameUnchanged(before, original, result)
        val oldAspect = (original.right - original.left) / (original.bottom - original.top)
        val newAspect = (result.crop.right - result.crop.left) / (result.crop.bottom - result.crop.top)
        assertEquals(oldAspect, newAspect, .00001f)
    }

    @Test fun draggingTheImageMovesSourceSelectionInTheOppositeDirection() {
        val before = SignatureCropViewport()
        val result = before.transform(original, 1f, .05f, -.1f, .5f, .5f)
        assertEquals(.05f, result.crop.left, .00001f)
        assertEquals(.3f, result.crop.top, .00001f)
        assertFrameUnchanged(before, original, result)
    }

    @Test fun panClampsAtSourceEdgesInsteadOfExposingAnEmptyCropRegion() {
        val before = SignatureCropViewport()
        val result = before.transform(original, 1f, 100f, -100f, .5f, .5f)
        assertEquals(0f, result.crop.left, .00001f)
        assertEquals(1f, result.crop.bottom, .00001f)
        assertTrue(result.crop.isValid)
        assertFrameUnchanged(before, original, result)
    }

    @Test fun zoomOutRestoresOriginalPixelsAndCannotShrinkImageBelowTheFrame() {
        val before = SignatureCropViewport()
        val enlarged = before.transform(original, 3f, 0f, 0f, .5f, .5f)
        val restored = enlarged.viewport.transform(enlarged.crop, .01f, 0f, 0f, .5f, .5f)
        assertEquals(1f, restored.viewport.scale, .00001f)
        assertEquals(original.left, restored.crop.left, .00001f)
        assertEquals(original.top, restored.crop.top, .00001f)
        assertEquals(original.right, restored.crop.right, .00001f)
        assertEquals(original.bottom, restored.crop.bottom, .00001f)
        assertFrameUnchanged(before, original, restored)
    }

    @Test fun zoomIsBoundedAndNonFiniteGesturesAreIgnored() {
        val before = SignatureCropViewport()
        val bounded = before.transform(original, 100f, 0f, 0f, .5f, .5f)
        assertEquals(8f, bounded.viewport.scale, .00001f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f).forEach { zoom ->
            assertEquals(SignatureCropImageTransform(before, original),
                before.transform(original, zoom, 0f, 0f, .5f, .5f))
        }
        assertEquals(SignatureCropImageTransform(before, original),
            before.transform(original, 2f, Float.NaN, 0f, .5f, .5f))
    }

    private fun assertFrameUnchanged(
        before: SignatureCropViewport, crop: PdfSignatureCrop, result: SignatureCropImageTransform,
    ) {
        assertEquals(before.offsetX + crop.left * before.scale,
            result.viewport.offsetX + result.crop.left * result.viewport.scale, .00001f)
        assertEquals(before.offsetY + crop.top * before.scale,
            result.viewport.offsetY + result.crop.top * result.viewport.scale, .00001f)
        assertEquals(before.offsetX + crop.right * before.scale,
            result.viewport.offsetX + result.crop.right * result.viewport.scale, .00001f)
        assertEquals(before.offsetY + crop.bottom * before.scale,
            result.viewport.offsetY + result.crop.bottom * result.viewport.scale, .00001f)
    }
}
