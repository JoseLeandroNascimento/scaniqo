package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlin.math.abs

/** Cria no ponto tocado: perto da borda, reduz a caixa em vez de deslocar a âncora. */
internal fun PdfOverlay.placedFillMarkAt(point: PdfPoint): PdfOverlay {
    val left = (point.x.takeIf(Float::isFinite) ?: .1f).coerceIn(0f, .98f)
    val top = (point.y.takeIf(Float::isFinite) ?: .1f).coerceIn(0f, .98f)
    return copy(x = left, y = top, width = minOf(width, 1f - left), height = minOf(height, 1f - top))
}

/** O gesto com dois dedos nunca termina como toque ou como edição de um elemento. */
internal class FillMarkGestureIntent {
    var transformed = false
        private set
    var dragged = false
        private set

    fun update(pointerCount: Int, distance: Float, touchSlop: Float) {
        if (pointerCount > 1) transformed = true
        if (distance > touchSlop) dragged = true
    }

    val canTap: Boolean get() = !transformed && !dragged
    val canCommitDrag: Boolean get() = !transformed && dragged
}

/**
 * Escala uniforme pelo canto oposto. A fonte acompanha a caixa: arrastar uma alça
 * aumenta o texto, sem esticá-lo ou alterar seu ponto de ancoragem.
 */
internal fun PdfOverlay.scaledFillMarkAtCorner(corner: Int, scale: Float): PdfOverlay {
    if (corner !in 0..3 || !scale.isFinite() || !isValid()) return this
    val fromLeft = corner == 0 || corner == 3
    val fromTop = corner < 2
    val anchorX = if (fromLeft) x + width else x
    val anchorY = if (fromTop) y + height else y
    val roomX = if (fromLeft) anchorX else 1f - anchorX
    val roomY = if (fromTop) anchorY else 1f - anchorY
    val minimum = 6f / fontSize
    val maximum = minOf(72f / fontSize, roomX / width, roomY / height).coerceAtLeast(1f)
    val factor = scale.coerceIn(minimum, maximum)
    if (factor == 1f) return this
    val newWidth = width * factor
    val newHeight = height * factor
    val left = (if (fromLeft) anchorX - newWidth else anchorX).coerceAtLeast(0f)
    val top = (if (fromTop) anchorY - newHeight else anchorY).coerceAtLeast(0f)
    return copy(
        x = left, y = top, width = newWidth, height = newHeight,
        fontSize = (fontSize * factor).coerceIn(6f, 72f),
        points = points.map {
            PdfPoint(
                (left + (it.x - x) * factor).coerceIn(0f, 1f),
                (top + (it.y - y) * factor).coerceIn(0f, 1f),
            )
        },
    )
}

/** A projeção usa pixels reais da folha; zoom e alças afastadas não causam saltos. */
internal fun PdfOverlay.scaledFillMarkFromCorner(
    corner: Int, deltaX: Float, deltaY: Float, pageWidth: Float, pageHeight: Float,
): PdfOverlay {
    if (corner !in 0..3 || !listOf(deltaX, deltaY, pageWidth, pageHeight).all(Float::isFinite) ||
        pageWidth <= 0f || pageHeight <= 0f) return this
    val diagonalX = width * pageWidth * if (corner == 0 || corner == 3) -1f else 1f
    val diagonalY = height * pageHeight * if (corner < 2) -1f else 1f
    val lengthSquared = diagonalX * diagonalX + diagonalY * diagonalY
    if (lengthSquared <= 0f) return this
    return scaledFillMarkAtCorner(corner, 1f + (deltaX * diagonalX + deltaY * diagonalY) / lengthSquared)
}

/** Redimensiona a caixa de texto, sem alterar a fonte, mantendo o canto oposto fixo. */
internal fun PdfOverlay.resizedFillMarkBoxFromCorner(
    corner: Int, deltaX: Float, deltaY: Float, pageWidth: Float, pageHeight: Float,
): PdfOverlay {
    if (corner !in 0..3 || !isValid() ||
        !listOf(deltaX, deltaY, pageWidth, pageHeight).all(Float::isFinite) ||
        pageWidth <= 0f || pageHeight <= 0f) return this
    val fromLeft = corner == 0 || corner == 3
    val fromTop = corner < 2
    val right = x + width
    val bottom = y + height
    val minWidth = minOf(.025f, width)
    val minHeight = minOf(.018f, height)
    val left = if (fromLeft) (x + deltaX / pageWidth).coerceIn(0f, right - minWidth) else x
    val top = if (fromTop) (y + deltaY / pageHeight).coerceIn(0f, bottom - minHeight) else y
    val newRight = if (fromLeft) right else (right + deltaX / pageWidth).coerceIn(x + minWidth, 1f)
    val newBottom = if (fromTop) bottom else (bottom + deltaY / pageHeight).coerceIn(y + minHeight, 1f)
    return copy(
        x = left, y = top, width = newRight - left, height = newBottom - top,
        autoSizeText = false, wrapText = true,
    )
}

/** Os gestos usam coordenadas da folha, independentemente do zoom da visualização. */
internal fun PdfOverlay.containsFillMarkPoint(point: PdfPoint, toleranceX: Float = 0f, toleranceY: Float = 0f): Boolean =
    point.x in (x - toleranceX)..(x + width + toleranceX) &&
        point.y in (y - toleranceY)..(y + height + toleranceY)

internal fun PdfOverlay.isFillMarkResizePoint(point: PdfPoint, toleranceX: Float, toleranceY: Float): Boolean =
    point.x >= x + width * .8f && point.y >= y + height * .65f &&
        abs(point.x - (x + width)) <= toleranceX && abs(point.y - (y + height)) <= toleranceY

internal fun PdfOverlay.resizedFillMark(right: Float, bottom: Float): PdfOverlay {
    val newWidth = (right - x).coerceIn(minOf(.025f, 1f - x), 1f - x)
    val newHeight = (bottom - y).coerceIn(minOf(.018f, 1f - y), 1f - y)
    return copy(
        width = newWidth,
        height = newHeight,
        points = points.map { point ->
            PdfPoint(x + (point.x - x) * newWidth / width, y + (point.y - y) * newHeight / height)
        },
    )
}
