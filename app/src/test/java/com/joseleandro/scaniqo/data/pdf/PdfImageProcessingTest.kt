package com.joseleandro.scaniqo.data.pdf

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

    private fun recipe(layout: PdfPageLayout) = PdfProjectRecipe(
        pages = listOf(RecipePage("pagina", 0, layout))
    )
}
