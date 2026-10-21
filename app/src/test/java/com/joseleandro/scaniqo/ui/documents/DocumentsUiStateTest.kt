package com.joseleandro.scaniqo.ui.documents

import androidx.compose.ui.graphics.Color
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.documents.state.DocumentTagIndex
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentsUiStateTest {
    private val first = DocumentModel(
        id = 1, name = "First", pdfUri = "file:///1.pdf",
        pageCount = 1, createdAt = 1, sizeBytes = 1, tagId = 1,
    )
    private val second = first.copy(id = 2, tagId = 2)
    private val tag = TagModel(id = 1, name = "Original", color = Color.Blue)

    @Test fun changingDocumentsAndSelectionWithCopyCannotReuseTheOldFilter() {
        val documents = listOf(first, second)
        val original = DocumentsUiState(documents = documents, selectedTagId = 1)
            .withPreparedLookups(DocumentTagIndex.build(documents), emptyMap())
        assertEquals(listOf(first), original.visibleDocuments)

        val moved = first.copy(name = "Moved", tagId = 2)
        val copy = original.copy(documents = listOf(moved), selectedTagId = 2)

        assertEquals(listOf(moved), copy.visibleDocuments)
        assertSame(copy.visibleDocuments, copy.visibleDocuments)
        assertEquals(listOf(first), original.visibleDocuments)
        assertTrue(copy.copy(selectedTagId = 99).visibleDocuments.isEmpty())
    }

    @Test fun copyingOnlyTheSelectedTagOrClearingItRecomputesFromThatSnapshot() {
        val documents = listOf(first, second)
        val original = DocumentsUiState(documents = documents, selectedTagId = 1)
        assertEquals(listOf(first), original.visibleDocuments)

        assertEquals(listOf(second), original.copy(selectedTagId = 2).visibleDocuments)
        assertSame(documents, original.copy(selectedTagId = null).visibleDocuments)
    }

    @Test fun copyingTagsCannotKeepThePreviousLookup() {
        val original = DocumentsUiState(tags = listOf(tag))
            .withPreparedLookups(DocumentTagIndex.build(emptyList()), mapOf(tag.id to tag))
        assertEquals(tag, original.tagsById[1])
        val renamed = tag.copy(name = "Renamed")

        val copy = original.copy(tags = listOf(renamed))

        assertEquals(renamed, copy.tagsById[1])
        assertSame(copy.tagsById, copy.tagsById)
        assertEquals(tag, original.tagsById[1])
        assertTrue(copy.copy(tags = emptyList()).tagsById.isEmpty())
    }
}
