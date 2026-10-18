package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures sintéticas dos operadores Skia; save/load e importPageAsForm usam o PDFBox real. */
class PdfCaptureSkiaTest {
    @Test fun skiaSourceWithWhitePolygonClipAndFlippedAxisRemainsProcessableAfterReopen() = runBlocking {
        scan().use { source ->
            reopen(source).use { document ->
                val before = document.getPage(0).contents.use { it.readBytes() }
                val native = PdfNativeContent.inspect(document, 0)
                assertEquals(PdfCropBounds(), native.captureBounds)
                assertFalse(native.shouldPreserve)
                assertEquals(1200, native.largestImageSide)
                assertEquals(1200f, native.sourceWidth, 0f)
                assertEquals(600f, native.sourceHeight, 0f)
                assertTrue(before.contentEquals(document.getPage(0).contents.use { it.readBytes() }))
            }
        }
    }

    @Test fun sourceImportedAsFormIntoA4RetainsCaptureAndWhiteMarginsAfterReopen() = runBlocking {
        scan().use { source ->
            PDDocument().use { output ->
                val page = PDPage(PDRectangle(600f, 800f))
                output.addPage(page)
                val form = LayerUtility(output).importPageAsForm(source, 0)
                PDPageContentStream(output, page).use {
                    it.setNonStrokingColor(1f)
                    it.addRect(0f, 0f, 600f, 800f)
                    it.fill()
                    it.saveGraphicsState()
                    it.addRect(40f, 40f, 520f, 720f)
                    it.clip()
                    it.transform(Matrix(1f / 3f, 0f, 0f, 1f / 3f, 100f, 300f))
                    it.drawForm(form)
                    it.restoreGraphicsState()
                }
                reopen(output).use { document ->
                    val native = PdfNativeContent.inspect(document, 0)
                    assertBounds(PdfCropBounds(1f / 6f, .375f, 5f / 6f, .625f), native.captureBounds)
                    assertFalse(native.shouldPreserve)
                    assertEquals(600f, document.getPage(0).cropBox.width, 0f)
                    assertEquals(800f, document.getPage(0).cropBox.height, 0f)
                }
            }
        }
    }

    @Test fun unusedVectorFormDoesNotBlockProcessingButDrawnDecorationIsPreserved() = runBlocking {
        scan().use { document ->
            val page = document.getPage(0)
            val unused = PDFormXObject(document).apply { bBox = PDRectangle(1200f, 600f) }
            unused.cosObject.createOutputStream().use { it.write("0 0 m 30 40 l S".toByteArray()) }
            page.resources.put(COSName.getPDFName("Unused"), unused)
            assertFalse(PdfNativeContent.inspect(document, 0).shouldPreserve)
            val decoration = PDStream(document, ByteArrayInputStream("q /Unused Do Q".toByteArray()))
            page.setContents(listOf(PDStream(document, page.contents), decoration))
            val native = PdfNativeContent.inspect(document, 0)
            assertTrue(native.hasVectors)
            assertNotNull(native.captureBounds)
        }
    }

    @Test fun invisibleOcrAndTextArraysKeepNativeProtectionWithoutHidingCapture() = runBlocking {
        for (text in listOf("BT /F0 12 Tf 3 Tr (OCR) Tj ET", "BT /F0 12 Tf [(O) 30 (CR)] TJ ET",
            "BT /F0 12 Tf (OCR) ' ET", "BT /F0 12 Tf 1 2 (OCR) \" ET")) {
            scan(extra = text).use { source ->
                reopen(source).use { document ->
                    val native = PdfNativeContent.inspect(document, 0)
                    assertTrue(native.hasText)
                    assertTrue(native.shouldPreserve)
                    assertNotNull(native.captureBounds)
                }
            }
        }
    }

    @Test fun emptyTextOperatorsDoNotRequireRasterizingAScan() = runBlocking {
        scan(extra = "BT /F0 12 Tf () Tj [() 20 ()] TJ ET").use {
            assertFalse(PdfNativeContent.inspect(it, 0).hasText)
        }
    }

    @Test fun textInsideADrawnFormIsPreservedButUnusedTextFormIsIgnored() = runBlocking {
        scan().use { document ->
            val page = document.getPage(0)
            val form = PDFormXObject(document).apply {
                bBox = PDRectangle(1200f, 600f)
                resources = page.resources
            }
            form.cosObject.createOutputStream().use { it.write("BT /F0 12 Tf [(OCR)] TJ ET".toByteArray()) }
            page.resources.put(COSName.getPDFName("Text"), form)
            assertFalse(PdfNativeContent.inspect(document, 0).hasText)
            page.setContents(listOf(PDStream(document, page.contents),
                PDStream(document, ByteArrayInputStream("q /Text Do Q".toByteArray()))))
            val native = PdfNativeContent.inspect(document, 0)
            assertTrue(native.hasText)
            assertNotNull(native.captureBounds)
        }
    }

    @Test fun aSingleInlineImageIsRecognizedWithoutDecodingItsBitmap() = runBlocking {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(600f, 800f))
            document.addPage(page)
            page.setContents(PDStream(document, ByteArrayInputStream(
                "q 400 0 0 200 100 300 cm BI /W 1 /H 1 /BPC 8 /CS /G ID \u007f EI Q".toByteArray())))
            assertBounds(PdfCropBounds(1f / 6f, .375f, 5f / 6f, .625f), PdfCaptureInspector.inspect(page))
        }
    }

    @Test fun anAdditionalDrawnImageStillRequiresAnUnambiguousSource() = runBlocking {
        scan(extra = "q 20 0 0 20 10 10 cm /Im0 Do Q").use { document ->
            assertNull(PdfNativeContent.inspect(document, 0).captureBounds)
        }
    }

    private fun scan(extra: String = ""): PDDocument = PDDocument().also { document ->
        val page = PDPage(PDRectangle(1200f, 600f))
        page.resources = PDResources()
        val image = PDImageXObject(document).apply {
            width = 1200
            height = 600
            bitsPerComponent = 8
            colorSpace = PDDeviceGray.INSTANCE
        }
        image.cosObject.createOutputStream(COSName.FLATE_DECODE).use {
            it.write(ByteArray(1200 * 600) { index -> (index % 251).toByte() })
        }
        page.resources.put(COSName.getPDFName("Im0"), image)
        page.resources.put(COSName.getPDFName("G0"), PDExtendedGraphicsState())
        // Fonte padrão declarada sem inicializar recursos Android: inspecionar não extrai glyphs.
        val font = COSDictionary().apply {
            setName(COSName.TYPE, "Font")
            setName(COSName.SUBTYPE, "Type1")
            setName(COSName.BASE_FONT, "Helvetica")
            setName(COSName.ENCODING, "WinAnsiEncoding")
        }
        page.resources.cosObject.setItem(COSName.FONT, COSDictionary().apply {
            setItem(COSName.getPDFName("F0"), font)
        })
        val commands = """
            1 0 0 -1 0 600 cm
            q /DeviceRGB cs 1 1 1 scn /G0 gs
            0 0 m 1200 0 l 1200 600 l 0 600 l h f Q
            q 0 0 m 1200 0 l 1200 600 l 0 600 l h W* n
            1200 0 0 -600 0 600 cm /Im0 Do Q
            $extra
        """.trimIndent()
        page.setContents(PDStream(document, ByteArrayInputStream(commands.toByteArray())))
        document.addPage(page)
    }

    private fun reopen(document: PDDocument): PDDocument {
        val bytes = ByteArrayOutputStream().use { document.save(it); it.toByteArray() }
        return PDDocument.load(bytes)
    }

    private fun assertBounds(expected: PdfCropBounds, actual: PdfCropBounds?) {
        assertNotNull(actual)
        assertEquals(expected.left, actual!!.left, .0001f)
        assertEquals(expected.top, actual.top, .0001f)
        assertEquals(expected.right, actual.right, .0001f)
        assertEquals(expected.bottom, actual.bottom, .0001f)
    }
}
