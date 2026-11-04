package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFCloneUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle

/** Materializa atributos herdados da árvore original, sem converter a folha para A4. */
internal class PdfExtractPageCopier(private val output: PDDocument) {
    private val cloner = PDFCloneUtility(output)

    fun copy(source: PDPage): PDPage {
        val page = output.importPage(source)
        page.resources = source.resources?.let {
            PDResources(cloner.cloneForNewDocument(it.cosObject) as COSDictionary)
        } ?: PDResources()
        page.mediaBox = copyBox(source.mediaBox)
        page.cropBox = copyBox(source.cropBox)
        page.bleedBox = copyBox(source.bleedBox)
        page.trimBox = copyBox(source.trimBox)
        page.artBox = copyBox(source.artBox)
        page.rotation = source.rotation
        // Referências de navegação/parentesco podem alcançar a árvore inteira da fonte.
        // Preservamos a aparência das anotações sem incorporar páginas não selecionadas.
        page.annotations = source.annotations.map { annotation ->
            val dictionary = COSDictionary(annotation.cosObject)
            listOf("P", "Parent", "Popup", "IRT", "Dest", "A", "AA").forEach {
                dictionary.removeItem(COSName.getPDFName(it))
            }
            com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation.createAnnotation(
                cloner.cloneForNewDocument(dictionary)
            ).also { it.page = page }
        }
        return page
    }

    private fun copyBox(box: PDRectangle) =
        PDRectangle(box.lowerLeftX, box.lowerLeftY, box.width, box.height)
}
