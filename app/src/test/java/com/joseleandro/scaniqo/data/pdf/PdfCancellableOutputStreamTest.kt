package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfMergeLimitException
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCancellableOutputStreamTest {
    @Test
    fun `cancelamento interrompe escrita sem acrescentar bytes e fecha a saida`() {
        val job = Job()
        var closed = false
        val destination = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
                super.close()
            }
        }
        val failure = runCatching {
            PdfCancellableOutputStream(destination, job).use { output ->
                output.write(byteArrayOf(1, 2))
                job.cancel(CancellationException("Operacao cancelada"))
                output.write(byteArrayOf(3, 4), 0, 2)
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals("Operacao cancelada", failure?.message)
        assertArrayEquals(byteArrayOf(1, 2), destination.toByteArray())
        assertTrue(closed)
    }

    @Test
    fun `limite rejeita bloco inteiro antes de exceder o tamanho permitido`() {
        val destination = ByteArrayOutputStream()
        val failure = runCatching {
            PdfCancellableOutputStream(destination, Job(), maxBytes = 3) {
                PdfMergeLimitException(PdfMergeLimitException.Reason.TOTAL_BYTES)
            }.use { output ->
                output.write(1)
                output.write(byteArrayOf(2, 3))
                output.write(4)
            }
        }.exceptionOrNull()

        assertTrue(failure is PdfMergeLimitException)
        assertEquals(PdfMergeLimitException.Reason.TOTAL_BYTES, (failure as PdfMergeLimitException).reason)
        assertArrayEquals(byteArrayOf(1, 2, 3), destination.toByteArray())
    }

    @Test
    fun `escrita com offset respeita somente o trecho pedido`() {
        val destination = ByteArrayOutputStream()
        PdfCancellableOutputStream(destination, Job(), maxBytes = 2).use {
            it.write(byteArrayOf(7, 8, 9, 10), 1, 2)
        }
        assertArrayEquals(byteArrayOf(8, 9), destination.toByteArray())
    }
}
