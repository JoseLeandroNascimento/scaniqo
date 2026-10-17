package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfInteractiveRasterSizeTest {
    @Test fun gestureSizePreservesPortraitAndLandscapeWithBoundedWork() {
        val portrait = PdfInteractiveRasterSize.fit(778, 1100, 480)
        val landscape = PdfInteractiveRasterSize.fit(1100, 778, 480)
        assertEquals(PdfInteractiveRasterSize(339, 480), portrait)
        assertEquals(PdfInteractiveRasterSize(480, 339), landscape)
        assertTrue(portrait.width * portrait.height < 480 * 480)
        assertTrue(landscape.width * landscape.height < 480 * 480)
    }

    @Test fun refinementRetainsOriginalSizeAndNeverUpscalesSmallSources() {
        assertEquals(PdfInteractiveRasterSize(778, 1100), PdfInteractiveRasterSize.fit(778, 1100, 1100))
        assertEquals(PdfInteractiveRasterSize(90, 120), PdfInteractiveRasterSize.fit(90, 120, 480))
    }

    @Test fun extremelyThinPagesKeepAtLeastOnePixelWithoutOverflow() {
        assertEquals(PdfInteractiveRasterSize(1, 480), PdfInteractiveRasterSize.fit(1, Int.MAX_VALUE, 480))
        assertEquals(PdfInteractiveRasterSize(480, 1), PdfInteractiveRasterSize.fit(Int.MAX_VALUE, 1, 480))
    }

    @Test fun invalidPreviewLimitsAndSourceDimensionsAreRejected() {
        listOf(0, 127, 1101, Int.MAX_VALUE).forEach { limit ->
            assertTrue(runCatching { PdfInteractiveRasterSize.fit(778, 1100, limit) }.isFailure)
        }
        assertTrue(runCatching { PdfInteractiveRasterSize.fit(0, 1100, 480) }.isFailure)
        assertTrue(runCatching { PdfInteractiveRasterSize.fit(778, -1, 480) }.isFailure)
    }

    @Test fun pixelFrameKeepsSourceGeometryIndependentFromReducedResolution() {
        val pixels = intArrayOf(-1, 0xff101010.toInt(), 0xff202020.toInt(), -1)
        val frame = PdfInteractivePixels(2, 2, pixels, 612f, 792f)
        assertSame(pixels, frame.pixels)
        assertEquals(612f, frame.sourceWidth, 0f)
        assertEquals(792f, frame.sourceHeight, 0f)
        assertTrue(runCatching { PdfInteractivePixels(2, 3, pixels, 612f, 792f) }.isFailure)
        assertTrue(runCatching { PdfInteractivePixels(2, 2, pixels, Float.NaN, 792f) }.isFailure)
    }
}
