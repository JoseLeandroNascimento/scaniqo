package com.joseleandro.scaniqo.domain.model

import kotlinx.serialization.Serializable

/** Recorte retangular na aparência da fonte, antes da rotação adicional da receita. */
@Serializable
data class PdfCropBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    fun isValid(): Boolean = listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f } &&
        right - left >= MIN_SIZE - 0.000001f && bottom - top >= MIN_SIZE - 0.000001f

    fun isIdentity(): Boolean = left == 0f && top == 0f && right == 1f && bottom == 1f

    companion object {
        // Coordenadas pertencem à página: uma captura pequena também pode ser recortada.
        // A interface mantém alças separadas por pelo menos 48 dp na imagem ampliada.
        const val MIN_SIZE = .0001f
    }
}
