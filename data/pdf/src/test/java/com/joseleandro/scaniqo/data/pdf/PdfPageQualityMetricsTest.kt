package com.joseleandro.scaniqo.data.pdf

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageQualityMetricsTest {
    private val width = 192
    private val height = 256

    @Test
    fun `folha branca gera aviso sem confundir com duplicata`() = runTest {
        val pixels = IntArray(width * height) { -1 }
        val first = PdfPageQualityMetrics.measure(pixels, width, height)
        val second = PdfPageQualityMetrics.measure(pixels, width, height)

        assertTrue(first.isBlank)
        assertFalse(first.isBlurry)
        assertFalse(first.mayBeClipped)
        assertFalse(first.resembles(second))
    }

    @Test
    fun `poucas letras ainda impedem classificacao de pagina vazia`() = runTest {
        val pixels = pageWithText()
        val result = PdfPageQualityMetrics.measure(pixels, width, height)

        assertFalse(result.isBlank)
        assertFalse(result.isBlurry)
        assertFalse(result.mayBeClipped)
    }

    @Test
    fun `pagina identica sugere duplicata mas conteudo diferente nao`() = runTest {
        val original = pageWithText()
        val changed = original.copyOf()
        for (y in 160..170) for (x in 40..140) changed[y * width + x] = 0xff000000.toInt()
        val first = PdfPageQualityMetrics.measure(original, width, height)
        val same = PdfPageQualityMetrics.measure(original.copyOf(), width, height)
        val different = PdfPageQualityMetrics.measure(changed, width, height)

        assertTrue(first.resembles(same))
        assertFalse(first.resembles(different))
    }

    @Test
    fun `tinta atravessando a borda sugere revisar o recorte`() = runTest {
        val pixels = pageWithText()
        for (y in 50..200) for (x in 0..3) pixels[y * width + x] = 0xff000000.toInt()

        assertTrue(PdfPageQualityMetrics.measure(pixels, width, height).mayBeClipped)
    }

    @Test
    fun `transicoes desfocadas sugerem recaptura sem alterar pixels`() = runTest {
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val distance = (x - 96.0) * (x - 96.0) / 2500 + (y - 120.0) * (y - 120.0) / 225
            val value = (255 - 220 * kotlin.math.exp(-distance)).toInt().coerceIn(0, 255)
            (0xff shl 24) or (value shl 16) or (value shl 8) or value
        }
        val snapshot = pixels.copyOf()
        val result = PdfPageQualityMetrics.measure(pixels, width, height)

        assertFalse(result.isBlank)
        assertTrue(result.isBlurry)
        assertTrue(pixels.contentEquals(snapshot))
    }

    private fun pageWithText() = IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        if (x in 40..140 && y in 50..140 && y % 16 in 0..2 && x % 9 < 6) {
            0xff000000.toInt()
        } else -1
    }
}
