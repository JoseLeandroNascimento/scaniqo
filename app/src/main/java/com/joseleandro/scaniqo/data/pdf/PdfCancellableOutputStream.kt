package com.joseleandro.scaniqo.data.pdf

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ensureActive

/** Interrompe gravações longas do PDFBox nos blocos de saída, sem exceder o espaço permitido. */
internal class PdfCancellableOutputStream(
    output: OutputStream,
    private val context: CoroutineContext,
    private val maxBytes: Long = Long.MAX_VALUE,
    private val limitFailure: () -> IOException = { IOException("O PDF excede o tamanho permitido.") }
) : FilterOutputStream(output) {
    private var written = 0L

    init {
        require(maxBytes >= 0)
    }

    override fun write(value: Int) {
        checkCapacity(1)
        out.write(value)
        written++
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        checkCapacity(length)
        out.write(bytes, offset, length)
        written += length
    }

    private fun checkCapacity(length: Int) {
        context.ensureActive()
        if (length > maxBytes - written) throw limitFailure()
    }
}
