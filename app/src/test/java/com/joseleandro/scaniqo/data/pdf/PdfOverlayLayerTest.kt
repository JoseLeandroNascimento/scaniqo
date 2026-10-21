package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, manifest = Config.NONE)
class PdfOverlayLayerTest {
    @Test fun insertedTextAndCustomNumberingRemainSearchableInFinalPdf() {
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                PdfOverlayLayer.draw(document, stream, listOf(
                    PdfOverlay("name", PdfOverlayKind.TEXT, text = "Nome: José Leandro"),
                    PdfOverlay("page", PdfOverlayKind.PAGE_NUMBER, text = "Página {pagina} de {total}", y = .9f),
                ), 2, 5)
            }
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Nome: José Leandro"))
            assertTrue(text.contains("Página 2 de 5"))
        }
    }

    @Test fun highlightDoesNotRemoveSourceTextAndIsDistinctFromRedaction() {
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                PdfOverlayLayer.draw(document, stream, listOf(
                    PdfOverlay("text", PdfOverlayKind.TEXT, text = "Texto preservado"),
                    PdfOverlay("highlight", PdfOverlayKind.HIGHLIGHT, opacity = .3f),
                ), 1, 1)
            }
            assertTrue(PDFTextStripper().getText(document).contains("Texto preservado"))
        }
    }
}
