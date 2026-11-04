package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Percorre uma única vez a árvore da fonte e conserva as mesmas páginas durante a operação.
 * A estrutura da fonte deve permanecer estável; não usar para um documento recebendo novas páginas.
 */
internal class PdfPageIndex(
    document: PDDocument,
    context: CoroutineContext = EmptyCoroutineContext,
) {
    // PDPageTree.get(index) percorre os irmãos novamente a cada acesso, tornando uma
    // reordenação quadrática. O iterador visita cada folha apenas uma vez.
    private val pages = buildList {
        for (page in document.pages) {
            context.ensureActive()
            add(page)
        }
    }

    val size: Int get() = pages.size

    operator fun get(index: Int): PDPage = pages[index]
}
