package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlin.math.max
import kotlin.math.min

internal data class CaptureMagnifierPlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal data class CaptureMagnifierTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translateX: Float,
    val translateY: Float,
)

/** Todas as medidas são pixels do palco; nunca posiciona a lupa sobre o dedo ou fora do palco. */
internal fun placeCaptureMagnifier(
    viewportWidth: Float,
    viewportHeight: Float,
    fingerX: Float,
    fingerY: Float,
    desiredWidth: Float,
    desiredHeight: Float,
    margin: Float,
    clearance: Float,
    minimumWidth: Float,
    minimumHeight: Float,
): CaptureMagnifierPlacement? {
    val values = listOf(
        viewportWidth, viewportHeight, fingerX, fingerY, desiredWidth, desiredHeight,
        margin, clearance, minimumWidth, minimumHeight,
    )
    if (values.any { !it.isFinite() } || viewportWidth <= 0f || viewportHeight <= 0f ||
        desiredWidth <= 0f || desiredHeight <= 0f || margin < 0f || clearance < 0f
    ) return null
    val availableWidth = viewportWidth - 2f * margin
    val availableHeight = viewportHeight - 2f * margin
    for (factor in listOf(1f, .85f, .7f)) {
        val width = min(desiredWidth, availableWidth) * factor
        val height = min(desiredHeight, availableHeight) * factor
        if (width < minimumWidth || height < minimumHeight || width <= 0f || height <= 0f) continue
        val maxLeft = viewportWidth - margin - width
        val maxTop = viewportHeight - margin - height
        val centeredLeft = (fingerX - width / 2f).coerceIn(margin, maxLeft)
        val centeredTop = (fingerY - height / 2f).coerceIn(margin, maxTop)
        val candidates = listOf(
            CaptureMagnifierPlacement(centeredLeft, fingerY - clearance - height, width, height),
            CaptureMagnifierPlacement(centeredLeft, fingerY + clearance, width, height),
            CaptureMagnifierPlacement(fingerX - clearance - width, centeredTop, width, height),
            CaptureMagnifierPlacement(fingerX + clearance, centeredTop, width, height),
            CaptureMagnifierPlacement(margin, margin, width, height),
            CaptureMagnifierPlacement(maxLeft, margin, width, height),
            CaptureMagnifierPlacement(margin, maxTop, width, height),
            CaptureMagnifierPlacement(maxLeft, maxTop, width, height),
        )
        candidates.firstOrNull { lens ->
            val inside = lens.left >= margin && lens.top >= margin &&
                lens.left + lens.width <= viewportWidth - margin + .01f &&
                lens.top + lens.height <= viewportHeight - margin + .01f
            val dx = max(max(lens.left - fingerX, 0f), fingerX - lens.left - lens.width)
            val dy = max(max(lens.top - fingerY, 0f), fingerY - lens.top - lens.height)
            inside && dx * dx + dy * dy >= clearance * clearance - .01f
        }?.let { return it }
    }
    // Em um palco sem espaço seguro, mantém o gesto e oculta somente a lupa.
    return null
}

/** Centraliza o canto real, inclusive nas extremidades, sem deslocar a mira para dentro da imagem. */
internal fun captureMagnifierTransform(
    focus: PdfPoint,
    imageWidth: Float,
    imageHeight: Float,
    bitmapWidth: Int,
    bitmapHeight: Int,
    lensWidth: Float,
    lensHeight: Float,
    zoom: Float = 3f,
): CaptureMagnifierTransform? {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 ||
        listOf(focus.x, focus.y, imageWidth, imageHeight, lensWidth, lensHeight, zoom).any { !it.isFinite() } ||
        imageWidth <= 0f || imageHeight <= 0f || lensWidth <= 0f || lensHeight <= 0f || zoom <= 0f
    ) return null
    val focusX = focus.x.coerceIn(0f, 1f)
    val focusY = focus.y.coerceIn(0f, 1f)
    return CaptureMagnifierTransform(
        scaleX = imageWidth * zoom / bitmapWidth,
        scaleY = imageHeight * zoom / bitmapHeight,
        translateX = lensWidth / 2f - focusX * imageWidth * zoom,
        translateY = lensHeight / 2f - focusY * imageHeight * zoom,
    )
}
