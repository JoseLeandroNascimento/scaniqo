package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import android.graphics.Color
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfOverlayUnicodeTest {
    @Test fun unicodeIsEmbeddedAsTransparentImagesAndLatinStaysSearchableAfterReopening() {
        val bytes = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            val overlays = listOf(
                PdfOverlay("latin", PdfOverlayKind.TEXT, text = "Nome: José Leandro"),
                PdfOverlay("arabic", PdfOverlayKind.TEXT, text = "الاسم: ليلى 123", y = .2f),
                PdfOverlay("chinese", PdfOverlayKind.TEXT, text = "姓名：王小明", y = .3f),
                PdfOverlay("russian", PdfOverlayKind.TEXT, text = "Имя: Мария", y = .4f),
            )
            PDPageContentStream(document, page).use {
                PdfOverlayLayer.draw(document, it, overlays, 1, 1)
            }
            document.save(bytes)
        }
        PDDocument.load(bytes.toByteArray()).use { reopened ->
            val text = PDFTextStripper().getText(reopened)
            assertTrue(text.contains("Nome: José Leandro"))
            assertFalse(text.contains("?"))
            val resources = reopened.getPage(0).resources
            val images = resources.xObjectNames.map(resources::getXObject)
                .filterIsInstance<PDImageXObject>()
            assertEquals(3, images.size)
            for (image in images) {
                val bitmap = image.image
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertTrue(pixels.any { Color.alpha(it) > 0 })
                    assertTrue(pixels.any { Color.alpha(it) == 0 })
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun fullPageUnicodeOverlayHasABoundedRaster() {
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use {
                PdfOverlayLayer.draw(document, it, listOf(
                    PdfOverlay(
                        "large", PdfOverlayKind.WATERMARK, text = "مستند سري",
                        x = 0f, y = 0f, width = 1f, height = 1f, fontSize = 72f,
                    ),
                ), 1, 1)
            }
            val resources = page.resources
            val image = resources.xObjectNames.map(resources::getXObject)
                .filterIsInstance<PDImageXObject>().single()
            assertTrue(image.width in 1..2048)
            assertTrue(image.height in 1..2048)
            assertTrue(image.width.toLong() * image.height <= 2_000_000L)
        }
    }
}
