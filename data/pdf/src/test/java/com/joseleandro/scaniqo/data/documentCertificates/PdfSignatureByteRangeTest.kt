package com.joseleandro.scaniqo.data.documentCertificates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSignatureByteRangeTest {
    @Test
    fun acceptsTwoOrderedRangesAndDistinguishesAnUnsignedLaterRevision() {
        val range = requireNotNull(PdfSignatureByteRange.parse(intArrayOf(0, 120, 300, 100), 400))
        assertEquals(400L, range.signedRevisionLength)
        assertTrue(range.covers(400))
        assertFalse(range.covers(401))
        assertEquals(range, PdfSignatureByteRange.parse(intArrayOf(0, 120, 300, 100), 500))
    }

    @Test
    fun rejectsMissingCoverageOverlapsNegativeLengthsAndOutOfBoundsRanges() {
        listOf(
            intArrayOf(), intArrayOf(0, 1, 2), intArrayOf(1, 120, 300, 100),
            intArrayOf(0, 0, 300, 100), intArrayOf(0, -1, 300, 100),
            intArrayOf(0, 120, 119, 100), intArrayOf(0, 120, 121, 100),
            intArrayOf(0, 120, 300, 0), intArrayOf(0, 120, 300, -1),
            intArrayOf(0, 120, 300, 101),
        ).forEach { assertNull(PdfSignatureByteRange.parse(it, 400)) }
    }

    @Test
    fun calculatesTheRevisionEndWithoutIntegerOverflow() {
        assertNull(PdfSignatureByteRange.parse(intArrayOf(0, 100, Int.MAX_VALUE - 10, 100), Int.MAX_VALUE.toLong()))
        val range = requireNotNull(PdfSignatureByteRange.parse(
            intArrayOf(0, 100, Int.MAX_VALUE - 10, 100), Int.MAX_VALUE.toLong() + 90,
        ))
        assertEquals(Int.MAX_VALUE.toLong() + 90, range.signedRevisionLength)
    }
}
