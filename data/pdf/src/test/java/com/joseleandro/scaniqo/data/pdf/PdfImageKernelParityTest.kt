package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/** Referências pequenas e diretas garantem que otimizar memória não altera os pixels exportados. */
class PdfImageKernelParityTest {
    @Test
    fun `nitidez deslizante coincide com convolucao direta inclusive bordas e alfa`() = runBlocking {
        for (width in listOf(3, 4, 9, 17, 32)) {
            val height = 19
            val source = IntArray(width * height) { index ->
                val red = (index * 67 + 17) and 255
                val green = (index * 31 + 9) and 255
                val blue = (index * 13 + 71) and 255
                ((index % 3 * 80 + 90).coerceAtMost(255) shl 24) or
                    (red shl 16) or (green shl 8) or blue
            }
            val expected = source.copyOf()
            for (y in 1 until height - 1) for (x in 1 until width - 1) {
                var sum = 0
                for (dy in -1..1) for (dx in -1..1) sum += luminance(source[(y + dy) * width + x + dx])
                val center = source[y * width + x]
                val difference = luminance(center) - sum / 9
                val detail = if (abs(difference) < 3) 0 else
                    (difference * .6f).roundToInt().coerceIn(-24, 24)
                expected[y * width + x] = mapChannels(center) { (it + detail).coerceIn(0, 255) }
            }
            val actual = source.copyOf()
            PdfImageFilters.apply(actual, PdfImageFilter.SHARPEN, width, height)
            assertArrayEquals("Largura $width", expected, actual)
        }
    }

    @Test
    fun `bilateral com tres linhas coincide com referencia sobre textura suave`() = runBlocking {
        val width = 23
        val height = 19
        // A amplitude baixa não é um impulso isolado: aqui exercitamos apenas o núcleo bilateral.
        val source = IntArray(width * height) { index ->
            val alpha = if (index % 7 == 0) 128 else 255
            (alpha shl 24) or ((85 + index * 7 % 23) shl 16) or
                ((85 + index * 11 % 23) shl 8) or (85 + index * 17 % 23)
        }
        val expected = source.copyOf()
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val center = source[y * width + x]
            var weightSum = 0
            val colors = IntArray(3)
            for (dy in -1..1) for (dx in -1..1) {
                val neighbor = source[(y + dy) * width + x + dx]
                if (center ushr 24 != neighbor ushr 24) continue
                val difference = (0..2).maxOf { abs(channel(center, it) - channel(neighbor, it)) }
                if (difference >= 40) continue
                val weight = (40 - difference) * if (dx == 0 && dy == 0) 4 else 1
                weightSum += weight
                for (color in 0..2) colors[color] += channel(neighbor, color) * weight
            }
            expected[y * width + x] = (center and -0x1000000) or
                ((colors[2] / weightSum) shl 16) or ((colors[1] / weightSum) shl 8) or (colors[0] / weightSum)
        }
        val actual = source.copyOf()
        PdfImageFilters.apply(actual, PdfImageFilter.DENOISE, width, height)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun `limiar adaptativo contiguo conserva resultado da media local nas margens`() = runBlocking {
        for ((width, height) in listOf(1 to 41, 41 to 1, 13 to 17, 31 to 27)) {
            val source = IntArray(width * height) { index -> gray(if (index * 17 % 13 < 4) 24 else 196) }
            val expected = source.copyOf()
            val radius = (minOf(width, height) / 32).coerceIn(2, 40)
            for (y in 0 until height) for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (dy in maxOf(0, y - radius)..minOf(height - 1, y + radius)) {
                    for (dx in maxOf(0, x - radius)..minOf(width - 1, x + radius)) {
                        sum += luminance(source[dy * width + dx])
                        count++
                    }
                }
                val mean = sum.toFloat() / count
                val threshold = mean - maxOf(5f, mean * .065f)
                expected[y * width + x] = gray(if (luminance(source[y * width + x]) < threshold) 0 else 255)
            }
            val actual = source.copyOf()
            PdfImageFilters.apply(actual, PdfImageFilter.ADAPTIVE_BLACK_WHITE, width, height)
            assertArrayEquals("Dimensões $width × $height", expected, actual)
        }
    }

    @Test
    fun `mistura pontual de intensidade preserva arredondamento e alfa`() = runBlocking {
        val source = IntArray(256) { index ->
            (128 shl 24) or (index shl 16) or ((255 - index) shl 8) or ((index * 13) and 255)
        }
        for (filter in listOf(PdfImageFilter.AUTO, PdfImageFilter.GRAYSCALE,
            PdfImageFilter.BLACK_WHITE, PdfImageFilter.WHITEN)) {
            val full = source.copyOf()
            PdfImageFilters.apply(full, filter, 16, 16)
            val expected = IntArray(source.size) { index ->
                var pixel = source[index] and -0x1000000
                for (color in 0..2) {
                    val before = channel(source[index], color)
                    val after = channel(full[index], color)
                    pixel = pixel or ((before + (after - before) * .37f).roundToInt() shl (color * 8))
                }
                pixel
            }
            val actual = source.copyOf()
            PdfImageFilters.apply(actual, filter, 16, 16, .37f)
            assertArrayEquals(filter.name, expected, actual)
        }
    }

    @Test
    fun `cantos padrao reutilizam buffer e nao obrigam rasterizacao`() = runBlocking {
        val source = IntArray(256) { it or -0x1000000 }
        val identity = PdfPageLayout(perspective = PdfPerspective())
        assertFalse(identity.needsImageProcessing())
        assertSame(source, PdfImageCorrections.apply(source, 16, 16, identity))
        val smallChange = identity.copy(perspective = PdfPerspective(topRight = PdfPoint(.9999f, 0f)))
        assertTrue(smallChange.needsImageProcessing())
    }

    @Test
    fun `filtros caros cedem execucao para cancelar edicao superada`() = runTest {
        for (filter in listOf(PdfImageFilter.ENHANCE, PdfImageFilter.DENOISE,
            PdfImageFilter.SHARPEN, PdfImageFilter.SHADOW_REMOVAL, PdfImageFilter.ADAPTIVE_BLACK_WHITE)) {
            var finished = false
            val task = launch(start = CoroutineStart.UNDISPATCHED) {
                PdfImageFilters.apply(IntArray(128 * 64) { gray(90 + it % 60) }, filter, 128, 64)
                finished = true
            }
            assertFalse("O filtro $filter precisa permitir cancelamento antes de terminar", finished)
            task.cancel()
            task.join()
            assertTrue(task.isCancelled)
            assertFalse(finished)
        }
    }

    @Test
    fun `correcao geometrica cede execucao sem sobrescrever a captura`() = runTest {
        val source = IntArray(128 * 64) { gray(it % 255) }
        val original = source.copyOf()
        var finished = false
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            PdfImageCorrections.apply(source, 128, 64, PdfPageLayout(straightenDegrees = 3f))
            finished = true
        }
        assertFalse(finished)
        task.cancel()
        task.join()
        assertTrue(task.isCancelled)
        assertArrayEquals(original, source)
    }

    private fun channel(pixel: Int, color: Int): Int = (pixel ushr (color * 8)) and 255
    private fun luminance(pixel: Int): Int =
        (77 * channel(pixel, 2) + 150 * channel(pixel, 1) + 29 * channel(pixel, 0) + 128) ushr 8
    private fun gray(value: Int): Int = -0x1000000 or (value shl 16) or (value shl 8) or value
    private inline fun mapChannels(pixel: Int, transform: (Int) -> Int): Int =
        (pixel and -0x1000000) or (transform(channel(pixel, 2)) shl 16) or
            (transform(channel(pixel, 1)) shl 8) or transform(channel(pixel, 0))
}
