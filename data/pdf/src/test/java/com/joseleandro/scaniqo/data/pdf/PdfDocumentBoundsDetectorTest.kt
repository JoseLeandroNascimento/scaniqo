package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfDocumentBoundsDetectorTest {
    @Test fun findsOuterPaperInsteadOfPrintedTextAndPreservesSource() = runBlocking {
        val expected = rectangle(.15f, .1f, .85f, .9f)
        val pixels = capture(expected, text = true)
        val original = pixels.copyOf()
        assertCorners(expected, PdfDocumentBoundsDetector.detect(pixels, WIDTH, HEIGHT))
        assertArrayEquals(original, pixels)
    }

    @Test fun findsFourDifferentCornersOfPerspectiveCapture() = runBlocking {
        val expected = PdfPerspective(
            PdfPoint(.19f, .12f), PdfPoint(.83f, .2f),
            PdfPoint(.91f, .88f), PdfPoint(.1f, .94f),
        )
        assertCorners(expected, PdfDocumentBoundsDetector.detect(capture(expected, text = true), WIDTH, HEIGHT))
    }

    @Test fun keepsWholePaperUnderUnevenLightingAndBackgroundNoise() = runBlocking {
        val expected = rectangle(.12f, .09f, .88f, .91f)
        val pixels = capture(expected, text = true, shadows = true)
        assertCorners(expected, PdfDocumentBoundsDetector.detect(pixels, WIDTH, HEIGHT))
    }

    @Test fun downsamplingStillReturnsCoordinatesOfOriginalSource() = runBlocking {
        val expected = rectangle(.15f, .1f, .85f, .9f)
        val pixels = capture(expected, width = 900, height = 1200, text = true)
        assertCorners(expected, PdfDocumentBoundsDetector.detect(pixels, 900, 1200))
    }

    @Test fun uniformAndLowContrastImagesHaveNoSafeSuggestion() = runBlocking {
        assertNull(PdfDocumentBoundsDetector.detect(IntArray(WIDTH * HEIGHT) { gray(220) }, WIDTH, HEIGHT))
        val paper = rectangle(.15f, .1f, .85f, .9f)
        val lowContrast = capture(paper).map { if ((it and 255) > 100) gray(220) else gray(208) }.toIntArray()
        assertNull(PdfDocumentBoundsDetector.detect(lowContrast, WIDTH, HEIGHT))
    }

    @Test fun pageTouchingFrameAndTextOnFullWhitePageAreLeftForManualCrop() = runBlocking {
        assertNull(PdfDocumentBoundsDetector.detect(capture(PdfPerspective(), text = true), WIDTH, HEIGHT))
        assertNull(PdfDocumentBoundsDetector.detect(
            capture(rectangle(0f, .1f, .85f, .9f), text = true), WIDTH, HEIGHT,
        ))
    }

    @Test fun refusesToChooseBetweenTwoSimilarDocuments() = runBlocking {
        val left = capture(rectangle(.04f, .1f, .43f, .9f))
        val right = capture(rectangle(.57f, .1f, .96f, .9f))
        val both = IntArray(left.size) { if ((left[it] and 255) > 100) left[it] else right[it] }
        assertNull(PdfDocumentBoundsDetector.detect(both, WIDTH, HEIGHT))
    }

    @Test fun checkerboardAndSmallBrightObjectsDoNotLookLikePaper() = runBlocking {
        val noise = IntArray(360 * 360) { index ->
            gray(if ((index % 360 + index / 360) % 2 == 0) 240 else 20)
        }
        assertNull(PdfDocumentBoundsDetector.detect(noise, 360, 360))
        assertNull(PdfDocumentBoundsDetector.detect(
            capture(rectangle(.4f, .4f, .6f, .6f)), WIDTH, HEIGHT,
        ))
    }

    @Test fun invalidInputDoesNotGenerateCoordinates() = runBlocking {
        assertNull(PdfDocumentBoundsDetector.detect(intArrayOf(), 0, 0))
        assertNull(PdfDocumentBoundsDetector.detect(intArrayOf(0), 100, 100))
        assertNull(PdfDocumentBoundsDetector.detect(intArrayOf(0), Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun cancelledDetectionDoesNotChangeOriginalPixels() = runBlocking {
        val pixels = capture(rectangle(.15f, .1f, .85f, .9f))
        val original = pixels.copyOf()
        var cancelled = false
        val task = launch {
            currentCoroutineContext().cancel()
            try {
                PdfDocumentBoundsDetector.detect(pixels, WIDTH, HEIGHT)
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        task.join()
        assertTrue(cancelled)
        assertArrayEquals(original, pixels)
    }

    @Test fun explicitManualCropExcludesPixelsOutsideSelection() = runBlocking {
        val width = 101
        val height = 101
        val source = IntArray(width * height) { index ->
            if (index % width in 20..80 && index / width in 20..80) gray(230) else gray(10)
        }
        val original = source.copyOf()
        val result = PdfImageCorrections.apply(
            source, width, height, PdfPageLayout(perspective = rectangle(.2f, .2f, .8f, .8f)),
        )
        assertTrue(result.all { (it and 255) == 230 })
        assertArrayEquals(original, source)
    }

    private fun assertCorners(expected: PdfPerspective, detected: PdfPerspective?) {
        assertNotNull("A folha inteira deve ser encontrada", detected)
        assertTrue(PdfImageCorrections.isValidPerspective(detected!!))
        expected.points().zip(detected.points()).forEachIndexed { index, (wanted, found) ->
            assertEquals("x do canto $index", wanted.x, found.x, .025f)
            assertEquals("y do canto $index", wanted.y, found.y, .025f)
        }
    }

    private fun capture(
        paper: PdfPerspective,
        width: Int = WIDTH,
        height: Int = HEIGHT,
        text: Boolean = false,
        shadows: Boolean = false,
    ): IntArray {
        val corners = paper.points()
        return IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val px = x / (width - 1f)
            val py = y / (height - 1f)
            val inside = corners.indices.all { i ->
                val a = corners[i]
                val b = corners[(i + 1) % 4]
                (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x) >= 0f
            }
            val noise = if (shadows) ((x * 17 + y * 23) % 13) - 6 else 0
            val ink = text && px in .28f.. .72f && py in .3f.. .7f && y % 19 in 0..2
            val value = when {
                !inside -> 35 + noise
                ink -> 55
                shadows -> 162 + (70f * px).toInt() + noise
                else -> 235
            }
            gray(value)
        }
    }

    private fun PdfPerspective.points() = listOf(topLeft, topRight, bottomRight, bottomLeft)
    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float) =
        PdfPerspective(PdfPoint(left, top), PdfPoint(right, top), PdfPoint(right, bottom), PdfPoint(left, bottom))
    private fun gray(value: Int) = -0x1000000 or (value shl 16) or (value shl 8) or value

    private companion object {
        const val WIDTH = 240
        const val HEIGHT = 320
    }
}
