package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.multipdf.PDFCloneUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlin.math.abs

/** Copia a folha pronta sem acumular camadas Form a cada ajuste na prévia. */
internal class PdfReadyPageCopier(private val output: PDDocument) {
    private val resources = PDFCloneUtility(output)

    fun copy(source: PDPage, removePageNumber: Boolean = false): Boolean {
        val content = if (removePageNumber) PdfPageNumberLayer.withoutNumber(source) ?: return false else source
        val box = content.cropBox
        if (source.rotation % 360 != 0 || source.annotations.isNotEmpty() ||
            abs(box.lowerLeftX) > .01f || abs(box.lowerLeftY) > .01f ||
            abs(box.width - PdfPageGeometry.WIDTH) > .01f ||
            abs(box.height - PdfPageGeometry.HEIGHT) > .01f
        ) return false
        val imported = output.importPage(content)
        imported.resources = source.resources?.let {
            PDResources(resources.cloneForNewDocument(it.cosObject) as COSDictionary)
        } ?: PDResources()
        imported.mediaBox = PDRectangle(PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT)
        imported.cropBox = PDRectangle(PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT)
        imported.rotation = 0
        return true
    }
}
