package com.joseleandro.scaniqo.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A4PageLayoutTest {

    @Test
    fun `pagina possui tamanho A4 em pontos e margens de 32 pontos`() {
        assertEquals(595, A4PageLayout.PAGE_WIDTH)
        assertEquals(842, A4PageLayout.PAGE_HEIGHT)
        val bounds = A4PageLayout.fit(531, 778)
        assertEquals(32f, bounds.left, 0.001f)
        assertEquals(32f, bounds.top, 0.001f)
        assertEquals(563f, bounds.right, 0.001f)
        assertEquals(810f, bounds.bottom, 0.001f)
    }

    @Test
    fun `imagens retrato paisagem e quadradas preservam proporcao e centralizacao`() {
        listOf(1200 to 2400, 2400 to 1200, 500 to 500).forEach { (width, height) ->
            val bounds = A4PageLayout.fit(width, height)

            assertEquals(width.toFloat() / height, bounds.width / bounds.height, 0.001f)
            assertEquals(595f / 2, (bounds.left + bounds.right) / 2, 0.001f)
            assertEquals(842f / 2, (bounds.top + bounds.bottom) / 2, 0.001f)
            assertTrue(bounds.left >= 32f - 0.001f)
            assertTrue(bounds.top >= 32f - 0.001f)
            assertTrue(bounds.right <= 563f + 0.001f)
            assertTrue(bounds.bottom <= 810f + 0.001f)
        }
    }

    @Test
    fun `imagem panoramica usa toda a largura disponivel sem corte`() {
        val bounds = A4PageLayout.fit(4000, 500)

        assertEquals(531f, bounds.width, 0.001f)
        assertEquals(66.375f, bounds.height, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dimensao zero nao produz geometria invalida`() {
        A4PageLayout.fit(0, 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dimensao negativa nao produz geometria invalida`() {
        A4PageLayout.fit(100, -1)
    }
}
