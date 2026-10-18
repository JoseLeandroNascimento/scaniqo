package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfWorkspace

/** Impede quadriláteros cruzados ou degenerados antes de enviar a receita ao processamento. */
internal fun PdfPerspective.isValidSelection(): Boolean {
    val points = listOf(topLeft, topRight, bottomRight, bottomLeft)
    if (points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return false
    val crosses = points.indices.map { index ->
        val a = points[index]
        val b = points[(index + 1) % 4]
        val c = points[(index + 2) % 4]
        (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
    }
    val doubleArea = points.indices.sumOf { index ->
        val a = points[index]
        val b = points[(index + 1) % 4]
        (a.x * b.y - b.x * a.y).toDouble()
    }
    return crosses.all { it > .001f } && doubleArea >= .04
}

/** A comparação preserva ordem, assinaturas e enquadramento da folha; só retira as melhorias. */
internal fun PdfWorkspace.withoutImageEnhancements(pageId: String? = null): PdfWorkspace = copy(
    pages = pages.map { page ->
        if (pageId != null && page.id != pageId) page else page.copy(layout = page.layout.copy(
            imageFilter = PdfImageFilter.ORIGINAL,
            filterIntensity = 1f,
            straightenDegrees = 0f,
            perspective = null,
            curvature = 0f,
        ))
    },
)
