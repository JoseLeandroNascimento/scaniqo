package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import com.joseleandro.scaniqo.core.graphics.ShapedTextLayout
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfOverlayTextAlignmentTest {
    @Test fun wrappedAutoSizedAlignedLatinRemainsSearchableAfterReopening() {
        val overlay = PdfOverlay(
            "name", PdfOverlayKind.TEXT, text = "Maria da Silva dos Santos", width = .18f, height = .3f,
            fontSize = 24f, autoSizeText = true, wrapText = true,
            textHorizontalAlignment = PdfTextHorizontalAlignment.CENTER,
            textVerticalAlignment = PdfTextVerticalAlignment.BOTTOM,
        )
        PDDocument.load(export(overlay)).use { document ->
            val text = PDFTextStripper().getText(document).replace(Regex("\\s+"), " ").trim()
            assertEquals(overlay.text, text)
            assertTrue(document.getPage(0).resources.xObjectNames.none())
        }
    }

    @Test fun exportedBaselinesAndHorizontalOffsetsMatchThePreviewLayoutForEveryAlignment() {
        for (horizontal in PdfTextHorizontalAlignment.entries) {
            for (vertical in PdfTextVerticalAlignment.entries) {
                val overlay = PdfOverlay(
                    "name", PdfOverlayKind.TEXT, text = "Maria\nSilva", x = .2f, y = .25f,
                    width = .5f, height = .3f, fontSize = 24f, wrapText = true,
                    textHorizontalAlignment = horizontal, textVerticalAlignment = vertical,
                )
                val width = overlay.width * 595f
                val height = overlay.height * 842f
                val shaped = ShapedTextLayout.create(
                    overlay.text, overlay.fontSize, overlay.color, width, height,
                    wrap = true, horizontalAlignment = horizontal,
                )
                PDDocument.load(export(overlay)).use { document ->
                    val positions = mutableListOf<TextPosition>()
                    val stripper = object : PDFTextStripper() {
                        override fun processTextPosition(text: TextPosition) {
                            positions += text
                            super.processTextPosition(text)
                        }
                    }
                    stripper.getText(document)
                    shaped.lines.zip(listOf("M", "S")).forEach { (line, firstLetter) ->
                        val position = positions.first { it.unicode == firstLetter }
                        assertEquals(
                            overlay.x * 595f + shaped.horizontalOffset(width) + line.left,
                            position.xDirAdj, .02f,
                        )
                        assertEquals(
                            overlay.y * 842f + shaped.verticalOffset(height, vertical) + line.baseline,
                            position.yDirAdj, .02f,
                        )
                    }
                }
            }
        }
    }

    private fun export(overlay: PdfOverlay): ByteArray {
        val bytes = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(595f, 842f))
            document.addPage(page)
            PDPageContentStream(document, page).use {
                PdfOverlayLayer.draw(document, it, listOf(overlay), 1, 1)
            }
            document.save(bytes)
        }
        return bytes.toByteArray()
    }
}
