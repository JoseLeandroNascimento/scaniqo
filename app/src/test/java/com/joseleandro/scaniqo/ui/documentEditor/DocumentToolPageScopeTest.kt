package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentToolPageScopeTest {
    private val pages = listOf("one", "two", "three").mapIndexed { index, id ->
        PdfPageSpec(id, "source.pdf", index)
    }

    @Test
    fun currentScopeFollowsThePreviewPage() {
        val scope = DocumentPageScope()
        assertEquals(setOf("two"), scope.targets(pages, currentPageId = "two"))
    }

    @Test
    fun selectedScopeKeepsStableIdsAfterReordering() {
        val scope = DocumentPageScope(DocumentPageScopeMode.SELECTED, setOf("one", "three"))
        assertEquals(setOf("one", "three"), scope.targets(pages.reversed(), currentPageId = "two"))
    }

    @Test
    fun removedPagesAreExcludedWithoutAddingTheCurrentPage() {
        val scope = DocumentPageScope(DocumentPageScopeMode.SELECTED, setOf("removed", "three"))
        assertEquals(setOf("three"), scope.targets(pages, currentPageId = "two"))
    }

    @Test
    fun emptySelectionDoesNotBecomeAllPages() {
        val scope = DocumentPageScope(DocumentPageScopeMode.SELECTED)
        assertTrue(scope.targets(pages, currentPageId = "one").isEmpty())
    }

    @Test
    fun allScopeIncludesNewPagesAndIgnoresPreviousSelection() {
        val scope = DocumentPageScope(DocumentPageScopeMode.ALL, setOf("one"))
        val updatedPages = pages + PdfPageSpec("four", "source.pdf", 3)
        assertEquals(setOf("one", "two", "three", "four"), scope.targets(updatedPages, currentPageId = "two"))
    }
}
