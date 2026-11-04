package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.geometry.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val FillPageAspect = PdfPageGeometry.WIDTH / PdfPageGeometry.HEIGHT

/** Cria no ponto tocado: perto da borda, reduz a caixa em vez de deslocar a âncora. */
internal fun PdfOverlay.placedFillMarkAt(point: PdfPoint): PdfOverlay {
    val left = (point.x.takeIf(Float::isFinite) ?: .1f).coerceIn(0f, .98f)
    val top = (point.y.takeIf(Float::isFinite) ?: .1f).coerceIn(0f, .98f)
    return copy(x = left, y = top, width = minOf(width, 1f - left), height = minOf(height, 1f - top))
        .movedTo(left, top)
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

internal data class FillMarkPositionMapping(private val overlay: PdfOverlay) {
    private val origin = overlay.movedTo(0f, 0f)
    private val left = origin.x
    private val top = origin.y
    val horizontalTravel = (1f - overlay.width - left * 2f).coerceAtLeast(0f)
    val verticalTravel = (1f - overlay.height - top * 2f).coerceAtLeast(0f)

    fun position(): PdfContentPosition = PdfContentPosition(
        if (horizontalTravel > .0001f) ((overlay.x - left) / horizontalTravel).coerceIn(0f, 1f) else .5f,
        if (verticalTravel > .0001f) ((overlay.y - top) / verticalTravel).coerceIn(0f, 1f) else .5f,
    )

    fun place(position: PdfContentPosition): PdfOverlay = overlay.movedTo(
        left + position.horizontal.coerceIn(0f, 1f) * horizontalTravel,
        top + position.vertical.coerceIn(0f, 1f) * verticalTravel,
    )
}

internal fun fillMarkPositionMapping(overlay: PdfOverlay) = FillMarkPositionMapping(overlay)

internal fun PdfOverlay.fillMarkScaleRange(): ClosedFloatingPointRange<Float> {
    val bounds = rotatedFillMarkSize()
    val pageMaximum = minOf(1f / width, 1f / height, 1f / bounds.x, 1f / bounds.y)
    val isText = kind in textKinds
    val maximum = if (isText) minOf(72f / fontSize, pageMaximum) else pageMaximum
    val minimum = if (isText) 6f / fontSize else maxOf(.01f / width, .01f / height)
    return minOf(minimum, maximum)..maximum
}

internal fun PdfOverlay.scaledFillMarkAroundCenter(scale: Float): PdfOverlay {
    if (!scale.isFinite() || !isValid()) return this
    val factor = scale.coerceIn(fillMarkScaleRange())
    val newWidth = width * factor
    val newHeight = height * factor
    return withFillMarkBounds(
        x + (width - newWidth) / 2f, y + (height - newHeight) / 2f, newWidth, newHeight,
    ).copy(fontSize = if (kind in textKinds) (fontSize * factor).coerceIn(6f, 72f) else fontSize)
        .let { it.movedTo(it.x, it.y) }
}

internal fun PdfOverlay.rotatedFillMark(degrees: Float): PdfOverlay {
    if (!degrees.isFinite() || !isValid()) return this
    val rotation = ((degrees + 180f) % 360f + 360f) % 360f - 180f
    val rotated = copy(rotationDegrees = rotation)
    val bounds = rotated.rotatedFillMarkSize()
    val factor = minOf(1f, 1f / bounds.x, 1f / bounds.y)
    val newWidth = width * factor
    val newHeight = height * factor
    return rotated.withFillMarkBounds(
        x + (width - newWidth) / 2f, y + (height - newHeight) / 2f, newWidth, newHeight,
    ).let { it.movedTo(it.x, it.y) }
}

/**
 * Escala uniforme pelo canto oposto. A fonte acompanha a caixa: arrastar uma alça
 * aumenta o texto, sem esticá-lo ou alterar seu ponto de ancoragem.
 */
internal fun PdfOverlay.scaledFillMarkAtCorner(corner: Int, scale: Float): PdfOverlay {
    if (corner !in 0..3 || !scale.isFinite() || !isValid()) return this
    val isText = kind in textKinds
    val factor = if (isText) scale.coerceIn(6f / fontSize, 72f / fontSize)
        else scale.coerceIn(fillMarkScaleRange())
    if (factor == 1f) return this
    val fitted = fitFillMarkCorner(corner, width * factor, height * factor)
    val appliedFactor = fitted.width / width
    return fitted.copy(fontSize = if (isText) (fontSize * appliedFactor).coerceIn(6f, 72f) else fontSize)
}

/** A projeção usa pixels reais da folha; zoom e alças afastadas não causam saltos. */
internal fun PdfOverlay.scaledFillMarkFromCorner(
    corner: Int, deltaX: Float, deltaY: Float, pageWidth: Float, pageHeight: Float,
): PdfOverlay {
    if (corner !in 0..3 || !listOf(deltaX, deltaY, pageWidth, pageHeight).all(Float::isFinite) ||
        pageWidth <= 0f || pageHeight <= 0f) return this
    val angle = Math.toRadians(rotationDegrees.toDouble())
    val localX = width * pageWidth * if (corner == 0 || corner == 3) -1f else 1f
    val localY = height * pageHeight * if (corner < 2) -1f else 1f
    val diagonalX = (localX * cos(angle) - localY * sin(angle)).toFloat()
    val diagonalY = (localX * sin(angle) + localY * cos(angle)).toFloat()
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
    val angle = Math.toRadians(rotationDegrees.toDouble())
    val dx = deltaX / pageWidth * FillPageAspect
    val dy = deltaY / pageHeight
    val localX = (dx * cos(angle) + dy * sin(angle)).toFloat() / FillPageAspect
    val localY = (-dx * sin(angle) + dy * cos(angle)).toFloat()
    val signX = if (corner == 0 || corner == 3) -1f else 1f
    val signY = if (corner < 2) -1f else 1f
    val desiredWidth = (width + signX * localX).coerceIn(minOf(.025f, width), 1f)
    val desiredHeight = (height + signY * localY).coerceIn(minOf(.018f, height), 1f)
    return fitFillMarkCorner(corner, desiredWidth, desiredHeight).copy(autoSizeText = false, wrapText = true)
}

private fun PdfOverlay.fitFillMarkCorner(corner: Int, desiredWidth: Float, desiredHeight: Float): PdfOverlay {
    val angle = Math.toRadians(rotationDegrees.toDouble())
    val cosine = cos(angle).toFloat()
    val sine = sin(angle).toFloat()
    val signX = if (corner == 0 || corner == 3) -1f else 1f
    val signY = if (corner < 2) -1f else 1f
    val anchorX = (x + width / 2f) * FillPageAspect -
        (signX * width * FillPageAspect * cosine - signY * height * sine) / 2f
    val anchorY = y + height / 2f -
        (signX * width * FillPageAspect * sine + signY * height * cosine) / 2f
    fun candidate(fraction: Float): PdfOverlay {
        val newWidth = width + (desiredWidth - width) * fraction
        val newHeight = height + (desiredHeight - height) * fraction
        val centerX = anchorX + (signX * newWidth * FillPageAspect * cosine - signY * newHeight * sine) / 2f
        val centerY = anchorY + (signX * newWidth * FillPageAspect * sine + signY * newHeight * cosine) / 2f
        return withFillMarkBounds(centerX / FillPageAspect - newWidth / 2f, centerY - newHeight / 2f, newWidth, newHeight)
    }
    val requested = candidate(1f)
    val fitted = if (requested.fillMarkFitsPage()) requested else {
        var low = 0f
        var high = 1f
        repeat(28) {
            val middle = (low + high) / 2f
            if (candidate(middle).fillMarkFitsPage()) low = middle else high = middle
        }
        candidate(low)
    }
    return fitted.movedTo(fitted.x, fitted.y)
}

private fun PdfOverlay.rotatedFillMarkSize(): PdfPoint {
    val angle = Math.toRadians(rotationDegrees.toDouble())
    val cosine = abs(cos(angle)).toFloat()
    val sine = abs(sin(angle)).toFloat()
    return PdfPoint(width * cosine + height * sine / FillPageAspect, width * sine * FillPageAspect + height * cosine)
}

private fun PdfOverlay.fillMarkFitsPage(): Boolean {
    val rotated = rotatedFillMarkSize()
    val halfWidth = maxOf(width, rotated.x) / 2f
    val halfHeight = maxOf(height, rotated.y) / 2f
    val centerX = x + width / 2f
    val centerY = y + height / 2f
    return width > 0f && height > 0f && centerX - halfWidth >= 0f && centerX + halfWidth <= 1f &&
        centerY - halfHeight >= 0f && centerY + halfHeight <= 1f
}

private fun PdfOverlay.withFillMarkBounds(left: Float, top: Float, newWidth: Float, newHeight: Float): PdfOverlay =
    copy(x = left, y = top, width = newWidth, height = newHeight, points = points.map {
        PdfPoint(
            (left + (it.x - x) * newWidth / width).coerceIn(0f, 1f),
            (top + (it.y - y) * newHeight / height).coerceIn(0f, 1f),
        )
    })

/** Os gestos usam coordenadas da folha, independentemente do zoom da visualização. */
internal fun PdfOverlay.containsFillMarkPoint(point: PdfPoint, toleranceX: Float = 0f, toleranceY: Float = 0f): Boolean {
    val local = unrotatedFillMarkPoint(point)
    return local.x in (x - toleranceX)..(x + width + toleranceX) &&
        local.y in (y - toleranceY)..(y + height + toleranceY)
}

internal fun PdfOverlay.unrotatedFillMarkPoint(point: PdfPoint): PdfPoint {
    val centerX = x + width / 2f
    val centerY = y + height / 2f
    val dx = (point.x - centerX) * FillPageAspect
    val dy = point.y - centerY
    val angle = Math.toRadians(rotationDegrees.toDouble())
    return PdfPoint(
        centerX + (dx * cos(angle) + dy * sin(angle)).toFloat() / FillPageAspect,
        centerY + (-dx * sin(angle) + dy * cos(angle)).toFloat(),
    )
}

internal fun PdfOverlay.isFillMarkResizePoint(point: PdfPoint, toleranceX: Float, toleranceY: Float): Boolean =
    unrotatedFillMarkPoint(point).let { local ->
        local.x >= x + width * .8f && local.y >= y + height * .65f &&
            abs(local.x - (x + width)) <= toleranceX && abs(local.y - (y + height)) <= toleranceY
    }

internal fun PdfOverlay.resizedFillMark(right: Float, bottom: Float): PdfOverlay {
    val newWidth = (right - x).coerceIn(minOf(.025f, 1f - x), 1f - x)
    val newHeight = (bottom - y).coerceIn(minOf(.018f, 1f - y), 1f - y)
    return withFillMarkBounds(x, y, newWidth, newHeight)
}
