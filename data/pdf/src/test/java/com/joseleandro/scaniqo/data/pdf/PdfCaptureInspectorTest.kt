package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayInputStream
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

class PdfCaptureInspectorTest {
    @Test fun findsHorizontalCaptureInsideA4WithoutChangingSourceOrMargins() = runBlocking {
        fixture("1 g 0 0 600 800 re f q 400 0 0 200 100 300 cm /Im0 Do Q") { _, page ->
            val before = page.contents.use { it.readBytes() }
            val bounds = PdfCaptureInspector.inspect(page)
            assertBounds(PdfCropBounds(1f / 6f, .375f, 5f / 6f, .625f), bounds)
            assertEquals(600f, page.cropBox.width, 0f)
            assertEquals(800f, page.cropBox.height, 0f)
            assertArrayEquals(before, page.contents.use { it.readBytes() })
        }
    }

    @Test fun rawScanAlreadyOccupyingItsWholeSourceHasIdentityViewport() = runBlocking {
        fixture("1 g 0 0 600 800 re f q 600 0 0 -800 0 800 cm /Im0 Do Q") { _, page ->
            assertEquals(PdfCropBounds(), PdfCaptureInspector.inspect(page))
        }
    }

    @Test fun rotationMapsImageIntoSameTopLeftCoordinatesUsedByPdfRenderer() = runBlocking {
        fixture("q 400 0 0 200 100 300 cm /Im0 Do Q") { _, page ->
            page.rotation = 90
            assertBounds(PdfCropBounds(.375f, 1f / 6f, .625f, 5f / 6f), PdfCaptureInspector.inspect(page))
        }
    }

    @Test fun rectangularClipIsAppliedToImageViewport() = runBlocking {
        fixture("150 330 200 100 re W n q 400 0 0 200 100 300 cm /Im0 Do Q") { _, page ->
            assertBounds(PdfCropBounds(.25f, .4625f, 350f / 600f, .5875f), PdfCaptureInspector.inspect(page))
        }
    }

    @Test fun aSingleImageInsideNestedFormIsStillOneCapture() = runBlocking {
        fixture("q 2 0 0 2 100 300 cm /Fm0 Do Q") { document, page ->
            val form = PDFormXObject(document).apply {
                bBox = PDRectangle(200f, 100f)
                resources = page.resources
            }
            form.cosObject.createOutputStream().use {
                it.write("q 200 0 0 100 0 0 cm /Im0 Do Q".toByteArray())
            }
            page.resources.put(COSName.getPDFName("Fm0"), form)
            assertBounds(PdfCropBounds(1f / 6f, .375f, 5f / 6f, .625f), PdfCaptureInspector.inspect(page))
        }
    }

    @Test fun unusedImageResourcesDoNotChangeTheOnlyDrawnCapture() = runBlocking {
        fixture("q 400 0 0 200 100 300 cm /Im0 Do Q") { document, page ->
            page.resources.put(COSName.getPDFName("Unused"), image(document))
            assertNotNull(PdfCaptureInspector.inspect(page))
        }
    }

    @Test fun multipleDrawnImagesRemainAmbiguousButTextDoesNotHideTheOnlyCapture() = runBlocking {
        fixture("q 200 0 0 100 20 20 cm /Im0 Do Q q 200 0 0 100 300 300 cm /Im0 Do Q") { _, page ->
            assertNull(PdfCaptureInspector.inspect(page))
        }
        fixture("q 400 0 0 200 100 300 cm /Im0 Do Q BT (Title) Tj ET") { _, page ->
            assertBounds(PdfCropBounds(1f / 6f, .375f, 5f / 6f, .625f), PdfCaptureInspector.inspect(page))
        }
    }

    @Test fun skewNonRectangularClippingAndCompleteOverpaintDoNotEnableCaptureTools() = runBlocking {
        for (content in listOf(
            "q 400 20 0 200 100 300 cm /Im0 Do Q",
            "0 0 m 600 0 l 300 800 l h W n q 400 0 0 200 100 300 cm /Im0 Do Q",
            "q 400 0 0 200 100 300 cm /Im0 Do Q 1 g 0 0 600 800 re f",
        )) fixture(content) { _, page -> assertNull(PdfCaptureInspector.inspect(page)) }
    }

    @Test fun detectorAndMaskUseCapturePixelsThenMapBackToSourceCoordinates() = runBlocking {
        fixture("q 400 0 0 200 100 300 cm /Im0 Do Q") { _, page ->
            val bounds = requireNotNull(PdfCaptureInspector.inspect(page))
            val captureWidth = 400
            val captureHeight = 200
            val capture = IntArray(captureWidth * captureHeight) { index ->
                val x = index % captureWidth
                val y = index / captureWidth
                if (x in 60..339 && y in 30..169) gray(230) else gray(35)
            }
            val detected = requireNotNull(PdfDocumentBoundsDetector.detect(capture, captureWidth, captureHeight))
            val mask = PdfCaptureGeometry.toSource(detected, bounds)
            assertEquals((100f + 60f) / 600f, mask.topLeft.x, .015f)
            assertEquals((300f + 30f) / 800f, mask.topLeft.y, .015f)
            val source = IntArray(600 * 800) { -1 }
            for (y in 0 until captureHeight) {
                capture.copyInto(source, (y + 300) * 600 + 100, y * captureWidth, (y + 1) * captureWidth)
            }
            val marker = -0xff5589
            source[400 * 600 + 300] = marker
            val original = source.copyOf()
            val result = PdfImageBackgroundRemoval.apply(source, 600, 800, mask)
            assertEquals(-1, result[310 * 600 + 110]) // Entorno dentro da foto.
            assertEquals(-1, result[10 * 600 + 10]) // Margem original da A4.
            assertEquals(marker, result[400 * 600 + 300]) // Conteúdo sem deslocar ou deformar.
            assertArrayEquals(original, source)
        }
    }

    @Test fun inspectionPropagatesCancellationWithoutChangingPage() = runBlocking {
        fixture("q 400 0 0 200 100 300 cm /Im0 Do Q") { _, page ->
            var cancelled = false
            val task = launch {
                currentCoroutineContext().cancel()
                try { PdfCaptureInspector.inspect(page) } catch (_: CancellationException) { cancelled = true }
            }
            task.join()
            assertTrue(cancelled)
            assertEquals(600f, page.cropBox.width, 0f)
        }
    }

    private suspend fun fixture(content: String, block: suspend (PDDocument, PDPage) -> Unit) {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(600f, 800f))
            page.resources = PDResources()
            page.resources.put(COSName.getPDFName("Im0"), image(document))
            page.setContents(PDStream(document, ByteArrayInputStream(content.toByteArray())))
            document.addPage(page)
            block(document, page)
        }
    }

    private fun image(document: PDDocument) = PDImageXObject(document).apply {
        width = 1200
        height = 600
        bitsPerComponent = 8
    }

    private fun assertBounds(expected: PdfCropBounds, actual: PdfCropBounds?) {
        assertNotNull(actual)
        assertEquals(expected.left, actual!!.left, .0001f)
        assertEquals(expected.top, actual.top, .0001f)
        assertEquals(expected.right, actual.right, .0001f)
        assertEquals(expected.bottom, actual.bottom, .0001f)
    }

    private fun gray(value: Int) = -0x1000000 or (value shl 16) or (value shl 8) or value
}
