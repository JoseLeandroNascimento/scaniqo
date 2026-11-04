package com.joseleandro.scaniqo.ui.documentSignature

import com.joseleandro.scaniqo.domain.model.PdfContentPosition
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.transformSignaturePlacement

internal data class SignaturePositionMapping(
    val horizontalInset: Float,
    val verticalInset: Float,
) {
    val horizontalTravel: Float get() = (1f - horizontalInset * 2f).coerceAtLeast(0f)
    val verticalTravel: Float get() = (1f - verticalInset * 2f).coerceAtLeast(0f)

    fun position(placement: PdfSignaturePlacement): PdfContentPosition = PdfContentPosition(
        horizontal = if (horizontalTravel > .0001f) {
            ((placement.x + placement.width / 2f - horizontalInset) / horizontalTravel).coerceIn(0f, 1f)
        } else .5f,
        vertical = if (verticalTravel > .0001f) {
            ((placement.y + placement.height / 2f - verticalInset) / verticalTravel).coerceIn(0f, 1f)
        } else .5f,
    )

    fun place(placement: PdfSignaturePlacement, position: PdfContentPosition): PdfSignaturePlacement =
        transformSignaturePlacement(
            placement,
            x = horizontalInset + position.horizontal.coerceIn(0f, 1f) * horizontalTravel - placement.width / 2f,
            y = verticalInset + position.vertical.coerceIn(0f, 1f) * verticalTravel - placement.height / 2f,
        )
}

internal fun signaturePositionMapping(placement: PdfSignaturePlacement): SignaturePositionMapping {
    val origin = transformSignaturePlacement(placement, x = 0f, y = 0f)
    return SignaturePositionMapping(
        horizontalInset = origin.x + origin.width / 2f,
        verticalInset = origin.y + origin.height / 2f,
    )
}
