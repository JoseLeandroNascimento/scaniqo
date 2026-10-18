package com.joseleandro.scaniqo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PdfSignatureCropTest {
    @Test fun fullSelectionRetainsEveryPixel() {
        assertEquals(PdfSignatureCropPixels(0, 0, 320, 200), PdfSignatureCrop(0f, 0f, 1f, 1f).pixels(320, 200))
    }

    @Test fun fractionalSelectionIncludesThePixelsTouchedAtBothEdges() {
        assertEquals(PdfSignatureCropPixels(1, 2, 7, 4), PdfSignatureCrop(.11f, .21f, .71f, .51f).pixels(10, 10))
    }

    @Test fun tinySelectionAtTheBottomRightStillHasOnePixel() {
        assertEquals(PdfSignatureCropPixels(99, 49, 1, 1), PdfSignatureCrop(.999f, .999f, 1f, 1f).pixels(100, 50))
    }

    @Test fun reversedEmptyNonfiniteAndOutsideSelectionsAreRejected() {
        listOf(
            PdfSignatureCrop(.8f, 0f, .2f, 1f),
            PdfSignatureCrop(0f, .5f, 1f, .5f),
            PdfSignatureCrop(Float.NaN, 0f, 1f, 1f),
            PdfSignatureCrop(-.1f, 0f, 1f, 1f),
        ).forEach { assertFalse(it.isValid) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEmptyBitmapCannotBeCropped() {
        PdfSignatureCrop(0f, 0f, 1f, 1f).pixels(0, 100)
    }
}
