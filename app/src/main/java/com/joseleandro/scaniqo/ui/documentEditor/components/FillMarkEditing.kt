package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.abs

internal val repeatableFillMarkKinds = setOf(PdfOverlayKind.TEXT, PdfOverlayKind.CHECKMARK, PdfOverlayKind.CROSSMARK)
internal val binaryFillMarkKinds = setOf(PdfOverlayKind.CHECKMARK, PdfOverlayKind.CROSSMARK)

internal fun updatedFillMarkRecentColors(current: List<Int>, color: Int): List<Int> =
    (listOf(color) + current.filterNot { it == color }).take(5)

/** Converte somente marcas selecionadas; identidade, tamanho, posição e aparência permanecem intactos. */
internal fun PdfOverlay.withFillMarkKind(kind: PdfOverlayKind): PdfOverlay =
    if (this.kind in binaryFillMarkKinds && kind in binaryFillMarkKinds) copy(kind = kind) else this

internal fun PdfOverlay.nextFillMarkAt(point: PdfPoint): PdfOverlay? =
    takeIf { it.kind in binaryFillMarkKinds }
        ?.copy(id = UUID.randomUUID().toString())
        ?.placedFillMarkAt(point)
        ?.takeIf(PdfOverlay::isValid)

/** Apenas aparência: textos, identidade, posição e escopo nunca são lembrados. */
@Serializable
internal data class FillMarkStyle(
    val color: Int,
    val fontSize: Float,
    val width: Float,
    val height: Float,
    val opacity: Float,
    val textHorizontalAlignment: PdfTextHorizontalAlignment = PdfTextHorizontalAlignment.START,
    val textVerticalAlignment: PdfTextVerticalAlignment = PdfTextVerticalAlignment.TOP,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
) {
    fun isValid(): Boolean = fontSize.isFinite() && fontSize in 6f..72f &&
        width.isFinite() && width > 0f && width <= 1f &&
        height.isFinite() && height > 0f && height <= 1f &&
        opacity.isFinite() && opacity in .05f..1f

    fun applyTo(overlay: PdfOverlay): PdfOverlay = if (!isValid()) overlay else overlay.copy(
        color = color, fontSize = fontSize, width = width, height = height, opacity = opacity,
        textHorizontalAlignment = textHorizontalAlignment, textVerticalAlignment = textVerticalAlignment,
        bold = bold, italic = italic, underlined = underlined,
    ).movedTo(overlay.x, overlay.y)

    companion object {
        fun from(overlay: PdfOverlay) = FillMarkStyle(
            overlay.color, overlay.fontSize, overlay.width, overlay.height, overlay.opacity,
            overlay.textHorizontalAlignment, overlay.textVerticalAlignment,
            overlay.bold, overlay.italic, overlay.underlined,
        )
    }
}

/** A caixa automática é recalculada pelo editor; a manual conserva a proporção do gesto de escala. */
internal fun PdfOverlay.withFillMarkFontSize(size: Float): PdfOverlay {
    if (!size.isFinite()) return this
    val font = size.coerceIn(6f, 72f)
    return if (autoSizeText) copy(fontSize = font) else scaledFillMarkAtCorner(2, font / fontSize)
}

/** Uma cópia independente, próxima do original e inteiramente dentro da página. */
internal fun PdfOverlay.duplicatedFillMark(): PdfOverlay {
    fun offset(position: Float, extent: Float): Float =
        if (position + extent + .025f <= 1f) position + .025f else (position - .025f).coerceAtLeast(0f)
    return copy(id = UUID.randomUUID().toString()).movedTo(offset(x, width), offset(y, height))
}

internal data class FillMarkAlignment(
    val overlay: PdfOverlay,
    val verticalGuide: Float? = null,
    val horizontalGuide: Float? = null,
)

/** A tolerância vem dos pixels do palco, mantendo o mesmo alcance em qualquer zoom. */
internal fun PdfOverlay.alignedFillMark(
    others: List<PdfOverlay>,
    toleranceX: Float,
    toleranceY: Float,
): FillMarkAlignment {
    if (!isValid() || !toleranceX.isFinite() || !toleranceY.isFinite() ||
        toleranceX <= 0f || toleranceY <= 0f) return FillMarkAlignment(this)
    val neighbors = others.filter { it.id != id && it.isValid() }
    val horizontal = closestAlignment(
        x, width, toleranceX, neighbors.flatMap { listOf(it.x, it.x + it.width / 2f, it.x + it.width) },
    )
    val vertical = closestAlignment(
        y, height, toleranceY, neighbors.flatMap { listOf(it.y, it.y + it.height / 2f, it.y + it.height) },
    )
    return FillMarkAlignment(
        movedTo(horizontal?.first ?: x, vertical?.first ?: y),
        verticalGuide = horizontal?.second,
        horizontalGuide = vertical?.second,
    )
}

private fun closestAlignment(
    position: Float,
    extent: Float,
    tolerance: Float,
    neighborAnchors: List<Float>,
): Pair<Float, Float>? {
    val candidates = buildList {
        // Na página, alinha as bordas às bordas e o centro ao centro.
        add(0f to 0f)
        add((.5f - extent / 2f) to .5f)
        add((1f - extent) to 1f)
        neighborAnchors.forEach { target ->
            listOf(0f, extent / 2f, extent).forEach { anchor -> add((target - anchor) to target) }
        }
    }
    return candidates.filter { (aligned, _) ->
        aligned >= 0f && aligned + extent <= 1.000001f && abs(aligned - position) <= tolerance
    }.minByOrNull { (aligned, _) -> abs(aligned - position) }
}
