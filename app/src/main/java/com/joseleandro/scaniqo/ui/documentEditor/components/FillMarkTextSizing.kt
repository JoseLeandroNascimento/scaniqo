package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.core.graphics.ShapedTextLayout
import com.joseleandro.scaniqo.core.graphics.androidTypeface
import com.joseleandro.scaniqo.domain.model.PdfOverlay

/**
 * Keeps the tap anchor and requested font size while fitting the text box to its content.
 * At the page edge, text wraps first; only a lack of vertical room scales the rendered text.
 */
internal fun PdfOverlay.fittedFillMarkText(): PdfOverlay {
    if (!autoSizeText || kind !in textKinds || !isValid()) return this
    val availableWidth = (1f - x) * 595f
    val availableHeight = (1f - y) * 842f
    if (availableWidth <= 0f || availableHeight <= 0f) return this
    val layout = ShapedTextLayout.create(
        text = text, fontSize = fontSize, color = color,
        maxWidth = availableWidth, maxHeight = availableHeight,
        wrap = true, horizontalAlignment = textHorizontalAlignment,
        typeface = androidTypeface(), underlined = underlined,
    )
    // A blank field still leaves a visible caret and a usable selection box.
    val pointWidth = maxOf(layout.naturalWidth, fontSize * .75f).coerceAtMost(availableWidth)
    val pointHeight = maxOf(layout.naturalHeight, fontSize * 1.2f).coerceAtMost(availableHeight)
    return copy(width = pointWidth / 595f, height = pointHeight / 842f, wrapText = true)
}
