package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfImageProcessingTest {
    @Test
    fun `intensidade zero preserva caminho vetorial quando nao ha correcao`() {
        val layout = PdfPageLayout(imageFilter = PdfImageFilter.ENHANCE, filterIntensity = 0f)
        assertFalse(layout.needsImageProcessing())
        assertTrue(layout.copy(straightenDegrees = 1f).needsImageProcessing())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `receita rejeita intensidade fora dos limites`() {
        recipe(PdfPageLayout(filterIntensity = 2f)).validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `receita rejeita inclinacao nao finita`() {
        recipe(PdfPageLayout(straightenDegrees = Float.NaN)).validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `receita rejeita perspectiva com cantos cruzados`() {
        recipe(PdfPageLayout(perspective = PdfPerspective(
            PdfPoint(0f, 0f), PdfPoint(1f, 1f), PdfPoint(1f, 0f), PdfPoint(0f, 1f)
        ))).validate()
    }

    @Test
    fun `recorte retangular preserva caminho vetorial enquanto mascara pede processamento`() {
        val crop = PdfPageLayout(crop = PdfCropBounds(.1f, .2f, .8f, .9f))
        crop.validateImageProcessing()
        assertFalse(crop.needsImageProcessing())
        assertTrue(crop.copy(backgroundMask = PdfPerspective(topLeft = PdfPoint(.1f, .1f))).needsImageProcessing())
        assertFalse(crop.copy(backgroundMask = PdfPerspective()).needsImageProcessing())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `receita rejeita recorte vazio`() {
        recipe(PdfPageLayout(crop = PdfCropBounds(left = .5f, right = .5f))).validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `receita rejeita mascara fora da fonte`() {
        recipe(PdfPageLayout(backgroundMask = PdfPerspective(topLeft = PdfPoint(-.1f, 0f)))).validate()
    }

    private fun recipe(layout: PdfPageLayout) = PdfProjectRecipe(
        pages = listOf(RecipePage("pagina", 0, layout))
    )
}
