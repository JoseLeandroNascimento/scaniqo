package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.isValidBackgroundMask
import kotlin.math.abs

/** Valida também receitas restauradas, que não passaram pelos limites dos controles da tela. */
internal fun PdfPageLayout.validateImageProcessing() {
    require(contentPosition?.isValid() != false) { "A posição do conteúdo é inválida." }
    require(contentScale.isFinite() && contentScale in .25f..2f) {
        "O tamanho do conteúdo deve ficar entre 25% e 200%."
    }
    require(filterIntensity.isFinite() && filterIntensity in 0f..1f) {
        "A intensidade do filtro deve ficar entre 0% e 100%."
    }
    require(straightenDegrees.isFinite() && straightenDegrees in -15f..15f) {
        "A correção de inclinação deve ficar entre −15° e 15°."
    }
    require(curvature.isFinite() && curvature in -1f..1f) {
        "A correção de curvatura está fora do intervalo permitido."
    }
    require(perspective?.let(PdfImageCorrections::isValidPerspective) != false) {
        "Selecione os quatro cantos da folha sem cruzar as bordas."
    }
    require(crop?.isValid() != false) { "O recorte deve ficar dentro da captura." }
    require(backgroundMask?.isValidBackgroundMask() != false) {
        "Selecione o documento sem cruzar as bordas."
    }
}

internal fun PdfPageLayout.needsImageProcessing(): Boolean =
    (imageFilter != PdfImageFilter.ORIGINAL && filterIntensity > 0f) ||
        abs(straightenDegrees) > .01f ||
        (perspective?.let(PdfImageCorrections::isIdentityPerspective) == false) ||
        (backgroundMask?.let(PdfImageCorrections::isIdentityPerspective) == false) ||
        abs(curvature) > .001f
