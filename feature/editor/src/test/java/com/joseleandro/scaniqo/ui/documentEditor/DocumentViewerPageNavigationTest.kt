package com.joseleandro.scaniqo.ui.documentEditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentViewerPageNavigationTest {
    @Test
    fun aContentMatchOpensItsOneBasedPage() {
        assertEquals(3, initialViewerPageIndex(pageNumber = 4, pageCount = 9))
    }

    @Test
    fun aNameMatchDoesNotRequestAJump() {
        assertNull(initialViewerPageIndex(pageNumber = null, pageCount = 9))
    }

    @Test
    fun aPageRemovedSinceTheSearchOpensTheLastRemainingPage() {
        assertEquals(2, initialViewerPageIndex(pageNumber = 8, pageCount = 3))
    }

    @Test
    fun noJumpIsRequestedUntilPagesHaveLoaded() {
        assertNull(initialViewerPageIndex(pageNumber = 4, pageCount = 0))
    }

    @Test
    fun invalidPageNumbersStayWithinTheDocumentWithoutOverflow() {
        assertEquals(0, initialViewerPageIndex(pageNumber = Int.MIN_VALUE, pageCount = 2))
        assertEquals(1, initialViewerPageIndex(pageNumber = Int.MAX_VALUE, pageCount = 2))
    }
}
