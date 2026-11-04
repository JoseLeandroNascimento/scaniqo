package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.ui.geometry.Offset
import com.joseleandro.scaniqo.domain.model.PdfPoint

internal fun safeViewportMaxZoom(value: Float): Float =
    (value.takeIf { it.isFinite() && it >= 1f } ?: 3f).coerceAtMost(12f)

/** Centraliza o ponto de edição, inclusive após o teclado alterar a janela visível. */
internal fun focusSignatureDocumentViewport(
    viewport: SignatureDocumentViewport,
    point: PdfPoint,
    zoom: Float,
    width: Float,
    height: Float,
    margin: Float,
    maxZoom: Float = 3f,
): SignatureDocumentViewport {
    val current = signatureDocumentViewportBounds(viewport, width, height, margin, maxZoom)
    val target = (zoom.takeIf(Float::isFinite) ?: current.viewport.zoom).coerceIn(1f, safeViewportMaxZoom(maxZoom))
    val x = (point.x.takeIf(Float::isFinite) ?: .5f).coerceIn(0f, 1f)
    val y = (point.y.takeIf(Float::isFinite) ?: .5f).coerceIn(0f, 1f)
    return signatureDocumentViewportBounds(
        SignatureDocumentViewport(target, (.5f - x) * target, (.5f - y) * target),
        width, height, margin, maxZoom,
    ).viewport
}

/** Mantém o PDF sob o centro dos dedos; escala e translação nunca alteram o documento. */
internal fun transformSignatureDocumentViewport(
    viewport: SignatureDocumentViewport,
    centroid: Offset,
    pan: Offset,
    zoomChange: Float,
    width: Float,
    height: Float,
    margin: Float,
    maxZoom: Float = 3f,
): SignatureDocumentViewport {
    val current = signatureDocumentViewportBounds(viewport, width, height, margin, maxZoom)
    val factor = zoomChange.takeIf { it.isFinite() && it > 0f } ?: 1f
    val zoom = (current.viewport.zoom * factor).coerceIn(1f, safeViewportMaxZoom(maxZoom))
    val anchorX = ((centroid.x.takeIf(Float::isFinite) ?: width / 2f) - current.page.left) / current.page.width
    val anchorY = ((centroid.y.takeIf(Float::isFinite) ?: height / 2f) - current.page.top) / current.page.height
    return signatureDocumentViewportBounds(
        current.viewport.copy(
            zoom = zoom,
            panX = current.viewport.panX + (anchorX - .5f) * (current.viewport.zoom - zoom) +
                (pan.x.takeIf(Float::isFinite) ?: 0f) / current.basePage.width,
            panY = current.viewport.panY + (anchorY - .5f) * (current.viewport.zoom - zoom) +
                (pan.y.takeIf(Float::isFinite) ?: 0f) / current.basePage.height,
        ),
        width, height, margin, maxZoom,
    ).viewport
}
