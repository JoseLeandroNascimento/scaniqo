package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/** Reutiliza os pixels corrigidos; mantém só uma cópia filtrada por vez na memória. */
internal object PdfFilterSamplePixels {
    suspend fun forEach(
        correctedPixels: IntArray,
        width: Int,
        height: Int,
        allowFilters: Boolean,
        consume: suspend (PdfImageFilter, IntArray) -> Unit,
    ) {
        require(width > 0 && height > 0 && width.toLong() * height == correctedPixels.size.toLong())
        val filters = if (allowFilters) PdfImageFilter.entries else listOf(PdfImageFilter.ORIGINAL)
        for (filter in filters) {
            currentCoroutineContext().ensureActive()
            val sample = correctedPixels.copyOf()
            PdfImageFilters.apply(sample, filter, width, height, intensity = 1f)
            consume(filter, sample)
            yield()
        }
    }
}
