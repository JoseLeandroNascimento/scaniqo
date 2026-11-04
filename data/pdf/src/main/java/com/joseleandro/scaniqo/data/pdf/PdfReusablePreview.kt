package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File

/** Folhas prontas só são reaproveitadas quando conteúdo, assinaturas e rodapé continuam iguais. */
internal data class PdfReusablePreview(val file: File, val key: PdfPreviewKey) {
    fun matchingPageIndices(workspace: PdfWorkspace): List<Int?> {
        if (key.showPageNumbers != workspace.showPageNumbers) {
            return List(workspace.pages.size) { null }
        }
        if (workspace.showPageNumbers) {
            if (key.pages.size != workspace.pages.size) return List(workspace.pages.size) { null }
            return workspace.pages.mapIndexed { index, page ->
                index.takeIf { key.pages[index] == page }
            }
        }
        val previous = key.pages.withIndex().associate { it.value.id to it }
        return workspace.pages.mapIndexed { index, page: PdfPageSpec ->
            previous[page.id]?.takeIf { cached ->
                cached.value == page &&
                    (cached.index == index || page.overlays.none { it.text.contains("{pagina}") }) &&
                    (key.pages.size == workspace.pages.size || page.overlays.none { it.text.contains("{total}") })
            }?.index
        }
    }
}
