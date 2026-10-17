package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

/** O rodapé fica separado do conteúdo para renumerar folhas sem repetir filtros nem OCR. */
internal object PdfPageNumberLayer {
    private val marker = COSName.getPDFName("ScaniqoPageNumber")

    fun hasLayer(page: PDPage): Boolean =
        page.contentStreams.asSequence().count { it.cosObject.getBoolean(marker, false) } == 1

    fun withoutNumber(page: PDPage): PDPage? {
        val streams = page.contentStreams.asSequence().toList()
        if (streams.count { it.cosObject.getBoolean(marker, false) } != 1) return null
        val content = streams.filterNot { it.cosObject.getBoolean(marker, false) }
        if (content.isEmpty()) return null
        // A cópia do dicionário mantém a fonte intacta para o viewer e para uma eventual falha na gravação.
        return PDPage(COSDictionary(page.cosObject)).apply { setContents(content) }
    }

    fun append(document: PDDocument, page: PDPage, pageNumber: Int, pageCount: Int) {
        PDPageContentStream(
            document, page, PDPageContentStream.AppendMode.APPEND, true, false
        ).use { stream ->
            stream.saveGraphicsState()
            draw(stream, pageNumber, pageCount)
            stream.restoreGraphicsState()
        }
        markLastStream(page)
    }

    internal fun markLastStream(page: PDPage) {
        page.contentStreams.asSequence().last().cosObject.setBoolean(marker, true)
    }

    fun draw(stream: PDPageContentStream, page: Int, count: Int) {
        val text = "$page / $count"
        val font = PDType1Font.HELVETICA
        val size = 10f
        val width = font.getStringWidth(text) / 1000 * size
        stream.setNonStrokingColor(1f)
        stream.addRect((PdfPageGeometry.WIDTH - width) / 2 - 4, 12f, width + 8, size + 6)
        stream.fill()
        stream.beginText()
        stream.setNonStrokingColor(0f)
        stream.setFont(font, size)
        stream.newLineAtOffset((PdfPageGeometry.WIDTH - width) / 2, 15f)
        stream.showText(text)
        stream.endText()
    }
}
