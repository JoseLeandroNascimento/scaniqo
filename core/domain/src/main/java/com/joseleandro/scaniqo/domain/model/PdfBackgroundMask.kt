package com.joseleandro.scaniqo.domain.model

/**
 * Valida a máscara nas coordenadas da página, sem exigir que a captura ocupe uma
 * porcentagem mínima da folha. Os limites de interação pertencem à imagem ampliada.
 */
fun PdfPerspective.isValidBackgroundMask(): Boolean {
    val points = listOf(topLeft, topRight, bottomRight, bottomLeft)
    if (points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) {
        return false
    }
    return points.indices.all { index ->
        val a = points[index]
        val b = points[(index + 1) % 4]
        val c = points[(index + 2) % 4]
        val cross = (b.x.toDouble() - a.x) * (c.y.toDouble() - b.y) -
            (b.y.toDouble() - a.y) * (c.x.toDouble() - b.x)
        cross > 1e-12
    }
}
