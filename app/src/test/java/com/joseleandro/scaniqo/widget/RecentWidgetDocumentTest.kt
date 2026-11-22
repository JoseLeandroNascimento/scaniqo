package com.joseleandro.scaniqo.widget

import com.joseleandro.scaniqo.domain.model.DocumentModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentWidgetDocumentTest {
    @Test
    fun recentDocumentsHaveStableNewestFirstOrderRegardlessOfRepositoryOrder() {
        val documents = listOf(
            document(2, createdAt = 200),
            document(7, createdAt = 400),
            document(4, createdAt = 300),
            document(1, createdAt = 100),
            document(6, createdAt = 400),
            document(3, createdAt = 300),
            document(5, createdAt = 300),
        )

        val recent = recentWidgetDocuments(documents)

        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), recent.map { it.id })
        assertEquals(recent, recentWidgetDocuments(documents.reversed()))
    }

    @Test
    fun trashedAndUnsavedDocumentsDoNotOccupyTheAvailableSlots() {
        val active = (1L..5L).map { document(it) }
        val unavailable = listOf(
            document(6).copy(deletedAt = 7_000),
            document(7).copy(deletedAt = 0),
            document(0, createdAt = 10_000),
            document(-1, createdAt = 20_000),
        )

        assertEquals(
            listOf(5L, 4L, 3L, 2L, 1L),
            recentWidgetDocuments(unavailable + active).map { it.id },
        )
        assertTrue(recentWidgetDocuments(unavailable).isEmpty())
        assertTrue(recentWidgetDocuments(emptyList()).isEmpty())
    }

    @Test
    fun renamePageCountAndProtectionChangesRefreshTheVisibleMetadata() {
        val original = document(7).copy(name = "Comprovante", pageCount = 1)
        val changed = original.copy(
            name = "Comprovante atualizado",
            pageCount = 3,
            protectionKeyId = "local-protection-key",
        )

        val before = recentWidgetDocuments(listOf(original)).single()
        val after = recentWidgetDocuments(listOf(changed)).single()

        assertFalse(before.isProtected)
        assertEquals(
            RecentWidgetDocument(
                id = 7,
                name = "Comprovante atualizado",
                pageCount = 3,
                createdAt = original.createdAt,
                isProtected = true,
            ),
            after,
        )
        assertEquals(before, recentWidgetDocuments(listOf(original)).single())
    }

    @Test
    fun trashingOrRemovingTheNewestDocumentPromotesTheNextAvailableDocument() {
        val documents = (1L..6L).map { document(it) }

        val afterTrashing = recentWidgetDocuments(
            documents.map {
                if (it.id == 6L) it.copy(deletedAt = 7_000) else it
            },
        )
        val afterRemoval = recentWidgetDocuments(documents.filterNot { it.id == 6L })

        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), afterTrashing.map { it.id })
        assertEquals(afterTrashing, afterRemoval)
        assertEquals(
            listOf(7L, 5L, 4L, 3L, 2L),
            recentWidgetDocuments(documents.filterNot { it.id == 6L } + document(7)).map { it.id },
        )
        assertEquals(
            listOf(6L, 5L, 4L, 3L, 2L),
            recentWidgetDocuments(documents).map { it.id },
        )
    }

    @Test
    fun internalFileOrTagChangesDoNotInvalidateUnchangedWidgetContent() {
        val original = document(3).copy(protectionKeyId = "first-key")
        val changed = original.copy(
            pdfUri = "content://documents/replaced-file",
            thumbnailUri = "content://documents/new-thumbnail",
            sizeBytes = 50_000,
            tagId = 18,
            protectionKeyId = "rotated-key",
        )

        assertEquals(
            recentWidgetDocuments(listOf(original)),
            recentWidgetDocuments(listOf(changed)),
        )
    }

    private fun document(id: Long, createdAt: Long = id * 1_000) = DocumentModel(
        id = id,
        name = "Documento $id",
        pdfUri = "content://documents/$id",
        pageCount = 2,
        createdAt = createdAt,
        sizeBytes = 4_096,
    )
}
