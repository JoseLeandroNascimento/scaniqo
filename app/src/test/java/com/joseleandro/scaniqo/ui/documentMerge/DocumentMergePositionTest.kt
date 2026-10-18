package com.joseleandro.scaniqo.ui.documentMerge

import com.joseleandro.scaniqo.ui.documentMerge.model.DocumentMergePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentMergePositionTest {
    @Test fun beginningAndEndUseTheCurrentPageCount() {
        assertEquals(0, DocumentMergePosition.START.insertionIndex(5, ""))
        assertEquals(5, DocumentMergePosition.END.insertionIndex(5, ""))
    }

    @Test fun afterPageUsesTheDisplayedPageNumberAsInsertionIndex() {
        assertEquals(1, DocumentMergePosition.AFTER_PAGE.insertionIndex(5, "1"))
        assertEquals(5, DocumentMergePosition.AFTER_PAGE.insertionIndex(5, "5"))
    }

    @Test fun invalidOrUnloadedDestinationsCannotBeSubmitted() {
        listOf("", "0", "6", "-1", "page").forEach {
            assertNull(DocumentMergePosition.AFTER_PAGE.insertionIndex(5, it))
        }
        DocumentMergePosition.entries.forEach {
            assertNull(it.insertionIndex(0, "1"))
        }
    }
}
