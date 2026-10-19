package com.joseleandro.scaniqo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfPageSelectionTest {
    @Test fun emptySelectionIncludesAllPages() {
        assertEquals(listOf(0, 1, 2), PdfPageSelection.parse(" ", 3))
    }
    @Test fun rangesPreserveUserOrderWithoutDuplicates() {
        assertEquals(listOf(4, 1, 2, 3, 0), PdfPageSelection.parse("5, 2-4, 2, 1", 5))
    }
    @Test fun acceptsSpacesAroundRanges() {
        assertEquals(listOf(1, 2), PdfPageSelection.parse(" 2 - 3 ", 4))
    }
    @Test fun invalidSelectionsAreRejectedBeforeProcessing() {
        listOf("0", "-1", "4", "3-1", "1-4", "1,", "1,,2", "a", "1-2-3", "999999999999").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { PdfPageSelection.parse(it, 3) }
        }
    }
    @Test fun documentLimitAndInputLimitAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) { PdfPageSelection.parse("", 101) }
        assertThrows(IllegalArgumentException::class.java) { PdfPageSelection.parse("", 0) }
        assertThrows(IllegalArgumentException::class.java) { PdfPageSelection.parse("1,".repeat(501), 3) }
    }
}
