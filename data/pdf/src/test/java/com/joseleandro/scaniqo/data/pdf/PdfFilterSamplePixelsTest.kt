package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfFilterSamplePixelsTest {
    @Test
    fun `cada amostra usa a mesma fonte corrigida sem acumular filtros`() = runTest {
        val original = IntArray(16 * 18) { index ->
            val value = 60 + index % 160
            (0xff shl 24) or (value shl 16) or ((value + 12) shl 8) or (value + 20)
        }
        val source = original.copyOf()
        val seen = mutableSetOf<PdfImageFilter>()
        PdfFilterSamplePixels.forEach(source, 16, 18, allowFilters = true) { filter, sample ->
            val expected = original.copyOf()
            PdfImageFilters.apply(expected, filter, 16, 18, 1f)
            assertArrayEquals(expected, sample)
            seen += filter
            sample.fill(0) // O consumidor pode reciclar seu buffer sem atingir a próxima opção.
        }
        assertEquals(PdfImageFilter.entries.toSet(), seen)
        assertArrayEquals(original, source)
    }

    @Test
    fun `texto preservado gera apenas uma amostra original`() = runTest {
        val original = intArrayOf(-1, 0xff000000.toInt(), 0xff776655.toInt(), -1)
        val seen = mutableListOf<PdfImageFilter>()
        PdfFilterSamplePixels.forEach(original, 2, 2, allowFilters = false) { filter, sample ->
            seen += filter
            assertArrayEquals(original, sample)
        }
        assertEquals(listOf(PdfImageFilter.ORIGINAL), seen)
    }

    @Test
    fun `cancelamento entre opcoes interrompe lote e preserva fonte`() = runTest {
        val source = IntArray(32 * 32) { 0xffaa8877.toInt() }
        val original = source.copyOf()
        var generated = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            PdfFilterSamplePixels.forEach(source, 32, 32, allowFilters = true) { _, _ -> generated++ }
        }
        assertEquals(1, generated)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, generated)
        assertArrayEquals(original, source)
    }
}
