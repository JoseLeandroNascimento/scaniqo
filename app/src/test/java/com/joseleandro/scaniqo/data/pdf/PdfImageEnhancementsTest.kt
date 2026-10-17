package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PdfImageEnhancementsTest {
    @Test
    fun `intensidade zero preserva todos os pixels em todos os filtros`() = runBlocking {
        val source = IntArray(256) { (0x80 shl 24) or (it shl 16) or (255 - it) }
        PdfImageFilter.entries.forEach { filter ->
            val result = source.copyOf()
            PdfImageFilters.apply(result, filter, 16, 16, 0f)
            assertArrayEquals(filter.name, source, result)
        }
    }

    @Test
    fun `intensidade intermediaria mistura o resultado sem alterar alfa`() = runBlocking {
        val original = intArrayOf(0x80FF0000.toInt())
        val result = original.copyOf()
        PdfImageFilters.apply(result, PdfImageFilter.GRAYSCALE, 1, 1, .5f)
        assertEquals(0x80A62727.toInt(), result[0])
    }

    @Test
    fun `correcao local reduz gradiente de sombra mantendo a tinta escura`() = runBlocking {
        val width = 256
        val height = 96
        val result = IntArray(width * height) { index -> gray(100 + (index % width) * 120 / 255) }
        result[48 * width + 40] = gray(25)
        val differenceBefore = abs(light(result[20 * width + 8]) - light(result[20 * width + 240]))
        PdfImageFilters.apply(result, PdfImageFilter.SHADOW_REMOVAL, width, height)
        val differenceAfter = abs(light(result[20 * width + 8]) - light(result[20 * width + 240]))
        assertTrue(differenceAfter < differenceBefore / 3)
        assertTrue(light(result[48 * width + 40]) < 70)
    }

    @Test
    fun `limiar adaptativo recupera tinta em ambos os lados de uma pagina sombreada`() = runBlocking {
        val width = 128
        val height = 64
        val result = IntArray(width * height) { index -> gray(90 + index % width) }
        for (x in 8 until width - 8) {
            result[30 * width + x] = gray(25 + x / 4)
        }
        PdfImageFilters.apply(result, PdfImageFilter.ADAPTIVE_BLACK_WHITE, width, height)
        assertEquals(gray(0), result[30 * width + 12])
        assertEquals(gray(0), result[30 * width + 115])
        assertEquals(gray(255), result[10 * width + 12])
        assertEquals(gray(255), result[10 * width + 115])
    }

    @Test
    fun `reducao de ruido suaviza granulacao sem borrar borda de alto contraste`() = runBlocking {
        val width = 15
        val source = IntArray(width * width) { index ->
            val x = index % width
            gray(if (x < 7) 20 + (index % 3) * 8 else 220 + (index % 3) * 8)
        }
        val result = source.copyOf()
        PdfImageFilters.apply(result, PdfImageFilter.DENOISE, width, width)
        assertTrue((2..12).all { light(result[it * width + 6]) < 55 })
        assertTrue((2..12).all { light(result[it * width + 7]) > 200 })
        val before = (3..5).map { light(source[7 * width + it]) }
        val after = (3..5).map { light(result[7 * width + it]) }
        assertTrue(after.max() - after.min() < before.max() - before.min())
    }

    @Test
    fun `branqueamento limpa o papel amarelado e conserva texto escuro`() = runBlocking {
        val result = intArrayOf(0xFFF4E8BD.toInt(), gray(30))
        PdfImageFilters.apply(result, PdfImageFilter.WHITEN, 2, 1)
        assertTrue((result[0] and 255) > 189)
        assertEquals(gray(30), result[1])
    }

    @Test
    fun `atenuacao do verso preserva tinta forte e enfraquece marca clara`() = runBlocking {
        val result = IntArray(64 * 64) { gray(248) }
        result[500] = gray(35)
        result[600] = gray(205)
        PdfImageFilters.apply(result, PdfImageFilter.BLEED_THROUGH, 64, 64)
        assertEquals(gray(35), result[500])
        assertTrue(light(result[600]) > 220)
    }

    @Test
    fun `nitidez aumenta contraste de uma transicao suave com limite de halo`() = runBlocking {
        val width = 9
        val result = IntArray(width * width) { gray(if (it % width < 4) 100 else 160) }
        PdfImageFilters.apply(result, PdfImageFilter.SHARPEN, width, width)
        assertTrue(light(result[4 * width + 3]) < 100)
        assertTrue(light(result[4 * width + 4]) > 160)
        assertTrue(light(result[4 * width + 3]) >= 76)
        assertTrue(light(result[4 * width + 4]) <= 184)
    }

    @Test
    fun `cancelamento previo nao deixa nenhuma melhoria parcial`() = runBlocking {
        PdfImageFilter.entries.forEach { filter ->
            val pixels = IntArray(81) { gray(it + 90) }
            val source = pixels.copyOf()
            var cancelled = false
            val task = launch {
                currentCoroutineContext().cancel()
                try {
                    PdfImageFilters.apply(pixels, filter, 9, 9, .7f)
                } catch (_: CancellationException) {
                    cancelled = true
                }
            }
            task.join()
            assertTrue(filter.name, cancelled)
            assertArrayEquals(source, pixels)
        }
    }

    private fun gray(value: Int): Int = -0x1000000 or (value shl 16) or (value shl 8) or value
    private fun light(pixel: Int): Int = pixel and 255
}
