package com.joseleandro.scaniqo.ui.documentCommon.components

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Limits simultaneous native PDF/bitmap allocations across reader pages and library thumbnails.
 * Waiting requests are cancellable, so fast scrolling does not render pages that already left UI.
 * This gate retains neither documents nor bitmaps.
 */
internal class DocumentPageRenderGate(parallelism: Int = 2) {
    private val permits = Semaphore(parallelism)

    suspend fun <T> render(block: suspend () -> T): T = permits.withPermit {
        currentCoroutineContext().ensureActive()
        block()
    }
}

internal data class DocumentPageRasterSize(val width: Int, val height: Int)

/** Fits even very tall pages within a bounded pixel budget, preserving their aspect ratio. */
internal fun documentPageRasterSize(
    pageWidth: Int,
    pageHeight: Int,
    requestedWidth: Int,
): DocumentPageRasterSize {
    require(pageWidth > 0 && pageHeight > 0)
    require(requestedWidth in 1..1400)
    val maxPixels = requestedWidth.toLong() * requestedWidth * 2
    val maxHeight = requestedWidth * 3
    val scale = min(
        requestedWidth.toDouble() / pageWidth,
        min(
            maxHeight.toDouble() / pageHeight,
            sqrt(maxPixels.toDouble() / (pageWidth.toDouble() * pageHeight)),
        ),
    )
    val width = (pageWidth * scale).toInt().coerceIn(1, requestedWidth)
    return DocumentPageRasterSize(
        width = width,
        // A very narrow page still needs one pixel: account for that rounding in the budget.
        height = (pageHeight * scale).toInt().coerceIn(1, min(maxHeight, (maxPixels / width).toInt())),
    )
}
