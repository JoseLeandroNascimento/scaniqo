package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfFilterAccelerationTest {
    @Test
    fun `preferencia desativada evita GPU e mudanca entra em vigor na proxima operacao`() = runBlocking {
        var enabled = false
        var attempts = 0
        val processor = PdfImageFilterProcessor({ enabled }, PdfFilterAcceleration { pixels, _, _, _, _ ->
            attempts++
            pixels.fill(0xff112233.toInt())
            true
        })
        val original = source()
        val expected = original.copyOf()
        PdfImageFilters.apply(expected, PdfImageFilter.DENOISE, 8, 8)
        val disabled = original.copyOf()
        processor.apply(disabled, PdfImageFilter.DENOISE, 8, 8, 1f)
        assertArrayEquals(expected, disabled)
        assertEquals(0, attempts)
        enabled = true
        val accelerated = original.copyOf()
        processor.apply(accelerated, PdfImageFilter.DENOISE, 8, 8, 1f)
        assertEquals(1, attempts)
        assertTrue(accelerated.all { it == 0xff112233.toInt() })
        enabled = false
        val disabledAgain = original.copyOf()
        processor.apply(disabledAgain, PdfImageFilter.DENOISE, 8, 8, 1f)
        assertArrayEquals(expected, disabledAgain)
        assertEquals(1, attempts)
    }

    @Test
    fun `GPU indisponivel preserva todos os filtros e intensidades da CPU`() = runBlocking {
        val processor = PdfImageFilterProcessor({ true }, PdfFilterAcceleration { _, _, _, _, _ -> false })
        for (filter in PdfImageFilter.entries) {
            val expected = source()
            val actual = source()
            PdfImageFilters.apply(expected, filter, 8, 8, .37f)
            processor.apply(actual, filter, 8, 8, .37f)
            assertArrayEquals(filter.name, expected, actual)
        }
    }

    @Test
    fun `melhorar acelera convolucoes mantendo iluminacao e tons da CPU`() = runBlocking {
        val attempts = mutableListOf<PdfImageFilter>()
        val processor = PdfImageFilterProcessor({ true }, PdfFilterAcceleration { pixels, filter, width, height, intensity ->
            attempts += filter
            if (filter == PdfImageFilter.DENOISE || filter == PdfImageFilter.SHARPEN) {
                PdfImageFilters.apply(pixels, filter, width, height, intensity)
                true
            } else false
        })
        val expected = source()
        val actual = source()
        PdfImageFilters.apply(expected, PdfImageFilter.ENHANCE, 8, 8, .6f)
        processor.apply(actual, PdfImageFilter.ENHANCE, 8, 8, .6f)
        assertArrayEquals(expected, actual)
        assertEquals(listOf(PdfImageFilter.ENHANCE, PdfImageFilter.DENOISE, PdfImageFilter.SHARPEN), attempts)
    }

    @Test(expected = CancellationException::class)
    fun `cancelamento do processamento nao inicia fallback`() = runBlocking {
        val processor = PdfImageFilterProcessor({ true }, PdfFilterAcceleration { _, _, _, _, _ ->
            throw CancellationException("Cancelado")
        })
        processor.apply(source(), PdfImageFilter.DENOISE, 8, 8, 1f)
    }

    @Test
    fun `GPU limitada a convolucoes e imagens com custo e memoria adequados`() {
        assertFalse(GlesPdfFilterAcceleration.isEligible(PdfImageFilter.DENOISE, 220, 220))
        assertFalse(GlesPdfFilterAcceleration.isEligible(PdfImageFilter.SHARPEN, 4000, 4000))
        assertFalse(GlesPdfFilterAcceleration.isEligible(PdfImageFilter.AUTO, 1200, 1600))
        assertFalse(GlesPdfFilterAcceleration.isEligible(PdfImageFilter.DENOISE, 0, 1500))
        assertTrue(GlesPdfFilterAcceleration.isEligible(PdfImageFilter.DENOISE, 1200, 1600))
        assertTrue(GlesPdfFilterAcceleration.isEligible(PdfImageFilter.SHARPEN, 512, 512))
    }

    private fun source() = IntArray(64) { index ->
        val value = (index * 31 + 17) and 255
        0xff000000.toInt() or (value shl 16) or ((255 - value) shl 8) or value
    }
}
