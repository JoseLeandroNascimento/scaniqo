package com.joseleandro.scaniqo.ui.documents

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
import com.joseleandro.scaniqo.ui.documentCommon.FakeTagRepository
import com.joseleandro.scaniqo.ui.documents.event.DocumentsEvent
import kotlin.system.measureNanoTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentLibraryViewModelLoadTest {
    @Test fun selectingTwoHundredFiltersDoesNotRescanOneHundredThousandDocuments() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val documents = CountingDocuments(List(100_000) { index ->
                DocumentModel(
                    id = index + 1L, name = "Document $index", pdfUri = "file:///fixture/$index.pdf",
                    pageCount = 1, createdAt = index.toLong(), sizeBytes = 4096, tagId = index % 100L,
                )
            })
            val repository = FakeDocumentRepository().apply { this.documents.value = documents }
            val tags = FakeTagRepository().apply {
                this.tags.value = List(100) { TagModel(id = it.toLong(), name = "Tag $it", color = Color.Blue) }
            }
            val viewModel = DocumentsViewModel(repository, tags, SavedStateHandle(), dispatcher)
                .also { store.put("volume-library", it) }
            val initialNanos = measureNanoTime { runCurrent() }
            assertFalse(viewModel.uiState.value.isLoading)
            assertSame(documents, viewModel.uiState.value.documents)
            assertEquals(100_000, documents.reads)
            documents.reads = 0

            val filterNanos = measureNanoTime {
                repeat(200) { index ->
                    val tagId = index % 100L
                    viewModel.onEvent(DocumentsEvent.SelectTag(tagId))
                    assertEquals(1_000, viewModel.uiState.value.visibleDocuments.size)
                    assertEquals(tagId, viewModel.uiState.value.visibleDocuments.first().tagId)
                }
            }
            val previousFilter = viewModel.uiState.value.visibleDocuments
            tags.tags.value = tags.tags.value.map { if (it.id == 99L) it.copy(name = "Renamed") else it }
            runCurrent()
            assertSame(previousFilter, viewModel.uiState.value.visibleDocuments)
            assertEquals("Renamed", viewModel.uiState.value.tagsById[99L]?.name)
            assertEquals("Filter changes and tag renames must not rescan the library", 0, documents.reads)
            println(
                "LOAD_METRIC library_vm documents=100000 filters=200 filterReads=${documents.reads} " +
                    "initialMs=${initialNanos / 1_000_000.0} filterMs=${filterNanos / 1_000_000.0}",
            )
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
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
