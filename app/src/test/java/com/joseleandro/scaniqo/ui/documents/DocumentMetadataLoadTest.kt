package com.joseleandro.scaniqo.ui.documents

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opt-in load fixture: synthetic metadata only; no PDF or user storage is accessed. */
class DocumentMetadataLoadTest {
    @Test
    fun repeatedTagFilteringAcrossOneHundredThousandDocuments() {
        val documents = CountingDocuments(List(100_000) { index ->
            DocumentModel(
                id = index + 1L,
                name = "Receipt تقرير $index",
                pdfUri = "file:///load-fixture/$index.pdf",
                pageCount = index % 12 + 1,
                createdAt = index.toLong(),
                sizeBytes = 4096L,
                tagId = index % 100L,
            )
        })
        lateinit var state: DocumentsUiState
        val setupNanos = measureNanoTime {
            state = DocumentsUiState(documents = documents, selectedTagId = 7L, isLoading = false)
        }
        documents.reads = 0
        var count = 0
        val elapsed = measureNanoTime {
            repeat(200) {
                val visible = state.visibleDocuments
                count += visible.size
                assertEquals(8L, visible.first().id)
                assertEquals(99_908L, visible.last().id)
            }
        }
        assertEquals(200_000, count)
        assertTrue(state.visibleDocuments.all { it.tagId == 7L })
        assertEquals("Each standalone snapshot filters only once", 100_000, documents.reads)
        println(
            "LOAD_METRIC metadata documents=100000 filterReads=${documents.reads} " +
                "repeats=200 setupMs=${setupNanos / 1_000_000.0} elapsedMs=${elapsed / 1_000_000.0}",
        )
    }

    private class CountingDocuments(private val source: List<DocumentModel>) : AbstractList<DocumentModel>() {
        var reads = 0
        override val size: Int get() = source.size
        override fun get(index: Int): DocumentModel {
            reads++
            return source[index]
        }
    }
}
