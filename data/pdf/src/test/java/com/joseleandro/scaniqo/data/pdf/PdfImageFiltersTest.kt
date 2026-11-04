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

class PdfImageFiltersTest {
    @Test
    fun `original nao modifica cores nem transparencias`() = runBlocking {
        val pixels = intArrayOf(0xFF205080.toInt(), 0x8040A050.toInt(), 0)
        val original = pixels.copyOf()

        PdfImageFilters.apply(pixels, PdfImageFilter.ORIGINAL)

        assertArrayEquals(original, pixels)
    }

    @Test
    fun `cinza usa luminancia e conserva o canal alfa`() = runBlocking {
        val pixels = intArrayOf(0x80FF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())

        PdfImageFilters.apply(pixels, PdfImageFilter.GRAYSCALE)

        assertArrayEquals(
            intArrayOf(0x804D4D4D.toInt(), 0xFF959595.toInt(), 0xFF1D1D1D.toInt()), pixels
        )
    }

    @Test
    fun `realce separa texto claro do papel sem modificar a fonte`() = runBlocking {
        val original = IntArray(1000) { gray(220) }.apply {
            this[20] = gray(190)
            this[21] = gray(190)
        }
        val pixels = original.copyOf()

        PdfImageFilters.apply(pixels, PdfImageFilter.AUTO)

        assertEquals(gray(0), pixels[20])
        assertEquals(gray(255), pixels[500])
        assertEquals(gray(190), original[20])
        assertEquals(gray(220), original[500])
    }

    @Test
    fun `realce preserva pagina uniforme sem divisao por zero`() = runBlocking {
        val pixels = IntArray(100) { gray(210) }

        PdfImageFilters.apply(pixels, PdfImageFilter.AUTO)

        assertTrue(pixels.all { it == gray(210) })
    }

    @Test
    fun `preto e branco adapta limiar para escrita clara sobre papel claro`() = runBlocking {
        val pixels = intArrayOf(gray(170), gray(175), gray(180), gray(215), gray(220), gray(225))

        PdfImageFilters.apply(pixels, PdfImageFilter.BLACK_WHITE)

        assertArrayEquals(
            intArrayOf(gray(0), gray(0), gray(0), gray(255), gray(255), gray(255)), pixels
        )
    }

    @Test
    fun `filtros produzem resultado deterministico a partir da mesma fonte`() = runBlocking {
        val source = IntArray(256) { gray(it) }
        PdfImageFilter.entries.forEach { filter ->
            val preview = source.copyOf()
            val exported = source.copyOf()

            PdfImageFilters.apply(preview, filter, 16, 16)
            PdfImageFilters.apply(exported, filter, 16, 16)

            assertArrayEquals(preview, exported)
        }
    }

    @Test
    fun `melhoria remove sal e pimenta isolados em fundo uniforme sem alterar a fonte`() = runBlocking {
        val width = 11
        val height = 9
        val original = IntArray(width * height) { gray(128) }.apply {
            this[3 * width + 3] = gray(0)
            this[5 * width + 7] = gray(255)
        }
        val result = original.copyOf()

        PdfImageFilters.apply(result, PdfImageFilter.ENHANCE, width, height)

        assertTrue(result.all { it == gray(128) })
        assertEquals(gray(0), original[3 * width + 3])
        assertEquals(gray(255), original[5 * width + 7])
    }

    @Test
    fun `melhoria preserva bordas nitidas sem espalhar tinta pelo papel`() = runBlocking {
        val width = 9
        val original = IntArray(width * width) { index ->
            gray(if (index % width < 4) 0 else 255)
        }
        val result = original.copyOf()

        PdfImageFilters.apply(result, PdfImageFilter.ENHANCE, width, width)

        assertArrayEquals(original, result)
    }

    @Test
    fun `melhoria conserva tracos de um pixel horizontais verticais e diagonais`() = runBlocking {
        val width = 9
        repeat(3) { direction ->
            val original = IntArray(width * width) { gray(255) }
            for (position in 1..7) {
                val index = when (direction) {
                    0 -> 4 * width + position
                    1 -> position * width + 4
                    else -> position * width + position
                }
                original[index] = gray(0)
            }
            val result = original.copyOf()

            PdfImageFilters.apply(result, PdfImageFilter.ENHANCE, width, width)

            assertArrayEquals(original, result)
        }
    }

    @Test
    fun `melhoria preserva pequenos tracos conectados mesmo com dois pixels`() = runBlocking {
        val original = IntArray(7 * 7) { gray(255) }.apply {
            this[3 * 7 + 3] = gray(0)
            this[3 * 7 + 4] = gray(0)
        }
        val result = original.copyOf()

        PdfImageFilters.apply(result, PdfImageFilter.ENHANCE, 7, 7)

        assertArrayEquals(original, result)
    }

    @Test
    fun `melhoria cancelada propaga cancelamento e nao altera os pixels`() = runBlocking {
        val pixels = IntArray(81) { gray(180) }.apply { this[40] = gray(0) }
        val original = pixels.copyOf()
        var cancelled = false
        val task = launch {
            currentCoroutineContext().cancel()
            try {
                PdfImageFilters.apply(pixels, PdfImageFilter.ENHANCE, 9, 9)
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        task.join()

        assertTrue(cancelled)
        assertArrayEquals(original, pixels)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `melhoria exige dimensoes corretas antes de percorrer a vizinhanca`() = runBlocking {
        PdfImageFilters.apply(IntArray(25), PdfImageFilter.ENHANCE, 4, 5)
    }

    private fun gray(value: Int): Int =
        -0x1000000 or (value shl 16) or (value shl 8) or value
}
