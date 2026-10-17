package com.joseleandro.scaniqo.domain.model

/**
 * Aparência da captura antes de posicionar, girar ou dimensionar o conteúdo na folha.
 *
 * O produtor transfere a propriedade do vetor ARGB e não volta a modificá-lo. Quem recebe também
 * deve tratá-lo como imutável; assinaturas, margens e numeração são compostas em outra camada.
 * As dimensões da fonte já consideram o recorte e a orientação original do PDF.
 */
class PdfInteractivePixels(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val sourceWidth: Float,
    val sourceHeight: Float,
) {
    init {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong()) {
            "As dimensões da prévia não correspondem aos pixels."
        }
        require(sourceWidth.isFinite() && sourceHeight.isFinite() && sourceWidth > 0f && sourceHeight > 0f) {
            "As dimensões da captura são inválidas."
        }
    }
}
