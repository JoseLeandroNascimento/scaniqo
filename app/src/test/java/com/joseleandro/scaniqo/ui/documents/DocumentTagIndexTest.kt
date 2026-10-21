package com.joseleandro.scaniqo.ui.documents

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documents.state.DocumentTagIndex
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DocumentTagIndexTest {
    @Test fun filtersReuseOrderedListsAndKeepUnassignedDocumentsInTheFullLibrary() {
        val documents = listOf(document(4, 2), document(3, null), document(2, 1), document(1, 2))
        val index = DocumentTagIndex.build(documents)

        assertSame(documents, index.visibleDocuments(null))
        assertEquals(listOf(4L, 1L), index.visibleDocuments(2).map { it.id })
        assertSame(index.visibleDocuments(2), index.visibleDocuments(2))
        assertTrue(index.visibleDocuments(99).isEmpty())
    }

    @Test fun updatedMetadataBuildsANewIndexWithoutChangingThePreviousSnapshot() {
        val original = document(1, 2)
        val previous = DocumentTagIndex.build(listOf(original))
        val updated = original.copy(name = "Renamed", tagId = 3)
        val current = DocumentTagIndex.build(listOf(updated))

        assertEquals(listOf(original), previous.visibleDocuments(2))
        assertTrue(current.visibleDocuments(2).isEmpty())
        assertEquals(listOf(updated), current.visibleDocuments(3))
    }

    @Test fun cancellationStopsIndexingInsteadOfPublishingAPartialLibrary() {
        var checks = 0
        try {
            DocumentTagIndex.build(List(1_000) { document(it.toLong(), 1) }) {
                if (++checks == 2) throw CancellationException("Superseded snapshot")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(2, checks)
        }
    }

    private fun document(id: Long, tagId: Long?) = DocumentModel(
        id = id, name = "Document $id", pdfUri = "file:///fixture/$id.pdf",
        pageCount = 1, createdAt = id, sizeBytes = 1, tagId = tagId,
    )
}
