package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfImageBackgroundRemovalTest {
    @Test fun removesSurroundingsWithoutResizingOrChangingInteriorPixels() = runTest {
        val source = IntArray(100 * 200) { 0xFF000000.toInt() or (it * 317 and 0xFFFFFF) }
        val original = source.copyOf()
        val output = PdfImageBackgroundRemoval.apply(source, 100, 200, rectangle(.2f, .1f, .8f, .9f))
        assertEquals(source.size, output.size)
        for (y in 0 until 200) for (x in 0 until 100) {
            assertEquals(if (x in 20..79 && y in 20..179) source[y * 100 + x] else -1, output[y * 100 + x])
        }
        assertArrayEquals(original, source)
    }

    @Test fun preservesTextAndColorsInsideSlantedDocumentInsteadOfRectifyingIt() = runTest {
        val source = IntArray(100 * 100) { 0xFF6B293C.toInt() }
        val mask = PdfPerspective(PdfPoint(.2f,.1f), PdfPoint(.8f,.2f), PdfPoint(.9f,.9f), PdfPoint(.1f,.8f))
        source[50 * 100 + 50] = 0xFF102030.toInt()
        val output = PdfImageBackgroundRemoval.apply(source, 100, 100, mask)
        assertEquals(source[50 * 100 + 50], output[50 * 100 + 50])
        assertEquals(source[40 * 100 + 40], output[40 * 100 + 40])
        assertEquals(-1, output[0])
        assertEquals(-1, output[99 * 100 + 99])
        assertTrue(output.any { it != -1 && it != 0xFF6B293C.toInt() && it != 0xFF102030.toInt() })
    }

    @Test fun maskCoordinatesRemainOnOriginalCaptureAfterManualCrop() = runTest {
        val source = IntArray(60 * 80) { 0xFF102030.toInt() }
        val output = PdfImageBackgroundRemoval.apply(
            source, 60, 80, rectangle(.3f,.2f,.7f,.8f),
            PdfCropBounds(.2f,.1f,.8f,.9f),
        )
        assertEquals(-1, output[40 * 60 + 2])
        assertEquals(source[40 * 60 + 30], output[40 * 60 + 30])
        assertEquals(-1, output[2 * 60 + 30])
        assertEquals(source[20 * 60 + 30], output[20 * 60 + 30])
    }

    @Test fun smallCaptureInsideALargePageKeepsItsContentAndRemovesTheSurroundings() = runTest {
        val crop = PdfCropBounds(.39f, .39f, .43f, .43f)
        val mask = rectangle(.4f, .4f, .42f, .42f)
        com.joseleandro.scaniqo.domain.model.PdfPageLayout(crop = crop, backgroundMask = mask)
            .validateImageProcessing()
        val source = IntArray(200 * 200) { 0xFF123456.toInt() }
        val original = source.copyOf()
        val output = PdfImageBackgroundRemoval.apply(source, 200, 200, mask, crop)
        assertEquals(-1, output[10 * 200 + 10])
        assertEquals(0xFF123456.toInt(), output[100 * 200 + 100])
        assertEquals(0xFF123456.toInt(), output[80 * 200 + 80])
        assertEquals(-1, output[190 * 200 + 190])
        assertArrayEquals(original, source)
    }

    @Test fun removesOnlyCaptureBackgroundAndKeepsPdfContentOutsideIt() = runTest {
        val source = IntArray(100 * 100) { 0xFF000000.toInt() or (it * 317 and 0xFFFFFF) }
        val original = source.copyOf()
        val output = PdfImageBackgroundRemoval.apply(
            source, 100, 100, rectangle(.3f, .3f, .7f, .7f),
            captureBounds = PdfCropBounds(.2f, .2f, .8f, .8f),
        )
        for (y in 0 until 100) for (x in 0 until 100) {
            val inCapture = x in 20..79 && y in 20..79
            val inDocument = x in 30..69 && y in 30..69
            assertEquals(
                if (inCapture && !inDocument) -1 else original[y * 100 + x],
                output[y * 100 + x],
            )
        }
        assertArrayEquals(original, source)
    }

    @Test fun captureAndMaskKeepSourceCoordinatesWhenViewportIsCropped() = runTest {
        val source = IntArray(80 * 80) { 0xFF234567.toInt() }
        val output = PdfImageBackgroundRemoval.apply(
            source, 80, 80, rectangle(.3f, .3f, .7f, .7f),
            crop = PdfCropBounds(.1f, .1f, .9f, .9f),
            captureBounds = PdfCropBounds(.2f, .2f, .8f, .8f),
        )
        for (y in 0 until 80) for (x in 0 until 80) {
            val inCapture = x in 10..69 && y in 10..69
            val inDocument = x in 20..59 && y in 20..59
            assertEquals(
                if (inCapture && !inDocument) -1 else source[y * 80 + x],
                output[y * 80 + x],
            )
        }
    }

    @Test fun cropOutsideCaptureOrInvalidCaptureNeverErasesOtherContent() = runTest {
        val source = IntArray(100) { 0xFF123456.toInt() }
        val mask = rectangle(.1f, .1f, .3f, .3f)
        assertSame(source, PdfImageBackgroundRemoval.apply(
            source, 10, 10, mask, crop = PdfCropBounds(.7f, .7f, .9f, .9f),
            captureBounds = PdfCropBounds(.05f, .05f, .35f, .35f),
        ))
        assertSame(source, PdfImageBackgroundRemoval.apply(
            source, 10, 10, mask, captureBounds = PdfCropBounds(.8f, .1f, .2f, .9f),
        ))
    }

    @Test fun slantedMaskAntialiasingNeverChangesPixelsOutsideCapture() = runTest {
        val source = IntArray(100 * 100) { 0xFF234567.toInt() }
        val mask = PdfPerspective(
            PdfPoint(.22f, .21f), PdfPoint(.79f, .31f),
            PdfPoint(.74f, .79f), PdfPoint(.31f, .7f),
        )
        val output = PdfImageBackgroundRemoval.apply(
            source, 100, 100, mask, captureBounds = PdfCropBounds(.2f, .2f, .8f, .8f),
        )
        for (y in 0 until 100) for (x in 0 until 100) {
            if (x !in 20..79 || y !in 20..79) assertEquals(source[y * 100 + x], output[y * 100 + x])
        }
        assertEquals(source[50 * 100 + 50], output[50 * 100 + 50])
        assertEquals(-1, output[21 * 100 + 78])
        assertTrue(output.any { it != -1 && it != 0xFF234567.toInt() })
    }

    @Test fun absentOrInvalidMaskNeverErasesContent() = runTest {
        val source = IntArray(100) { 0xFF123456.toInt() }
        assertSame(source, PdfImageBackgroundRemoval.apply(source, 10, 10, null))
        assertSame(source, PdfImageBackgroundRemoval.apply(source, 10, 10,
            rectangle(.8f,.1f,.2f,.9f)))
        assertArrayEquals(source, PdfImageBackgroundRemoval.apply(source, 10, 10, PdfPerspective()))
    }

    @Test fun cropOutsideDocumentProducesPaperAndKeepsSourceUnchanged() = runTest {
        val source = IntArray(100) { 0xFF123456.toInt() }
        val original = source.copyOf()
        val output = PdfImageBackgroundRemoval.apply(source, 10, 10, rectangle(.05f,.05f,.35f,.35f),
            PdfCropBounds(.7f,.7f,.9f,.9f))
        assertTrue(output.all { it == -1 })
        assertArrayEquals(original, source)
    }

    @Test fun cancellationStopsProcessingWithoutMutatingCapture() = runTest {
        val source = IntArray(1000 * 1000) { 0xFF123456.toInt() }
        var published = false
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            PdfImageBackgroundRemoval.apply(source, 1000, 1000, rectangle(.1f,.1f,.9f,.9f))
            published = true
        }
        task.cancelAndJoin()
        assertTrue(!published)
        assertTrue(source.all { it == 0xFF123456.toInt() })
    }

    private fun rectangle(left:Float,top:Float,right:Float,bottom:Float) =
        PdfPerspective(PdfPoint(left,top),PdfPoint(right,top),PdfPoint(right,bottom),PdfPoint(left,bottom))
}
