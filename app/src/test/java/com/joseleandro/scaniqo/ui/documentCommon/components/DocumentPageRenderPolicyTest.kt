package com.joseleandro.scaniqo.ui.documentCommon.components

import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPageRenderPolicyTest {
    @Test fun standardPagesRetainReadingResolution() {
        assertEquals(DocumentPageRasterSize(1400, 1981), documentPageRasterSize(595, 842, 1400))
        assertEquals(DocumentPageRasterSize(280, 396), documentPageRasterSize(595, 842, 280))
    }

    @Test fun veryTallReceiptIsScaledInsteadOfRejected() {
        val size = documentPageRasterSize(600, 60_000, 1400)
        assertEquals(DocumentPageRasterSize(42, 4200), size)
        assertEquals(100.0, size.height.toDouble() / size.width, 0.001)
    }

    @Test fun variedAndExtremePageSizesStayWithinBitmapBudget() {
        val random = Random(42)
        val dimensions = listOf(1 to Int.MAX_VALUE, Int.MAX_VALUE to 1, Int.MAX_VALUE to Int.MAX_VALUE) +
            List(10_000) { random.nextInt(1, Int.MAX_VALUE) to random.nextInt(1, Int.MAX_VALUE) }
        for ((width, height) in dimensions) {
            for (requestedWidth in listOf(1, 280, 1400)) {
                val size = documentPageRasterSize(width, height, requestedWidth)
                assertTrue(size.width in 1..requestedWidth)
                assertTrue(size.height in 1..requestedWidth * 3)
                assertTrue(size.width.toLong() * size.height <= requestedWidth.toLong() * requestedWidth * 2)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedZeroWidthIsRejectedBeforeAllocation() {
        documentPageRasterSize(0, 842, 1400)
    }

    @Test fun failedDecodeReleasesItsSlot() = runBlocking {
        val gate = DocumentPageRenderGate(1)
        try {
            gate.render<Unit> { throw IllegalStateException("decode failure") }
        } catch (_: IllegalStateException) {
            // A subsequent decode must not deadlock behind the failed request.
        }
        assertEquals(42, gate.render { 42 })
    }
}
