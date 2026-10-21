package com.joseleandro.scaniqo.ui.documents

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
import com.joseleandro.scaniqo.ui.documents.model.PdfImportDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Metadata/state workload only: this does not claim to parse or persist 2,500 PDF files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class PdfImportVolumeLoadTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var vault: PasswordVault

    @Before fun setUp() {
        vault = PasswordVault(RuntimeEnvironment.getApplication())
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun sequentialImportsAndCancellationsDoNotDuplicateDeliveryOrRetainDraftState() = runTest(dispatcher) {
        val editor = MetadataEditor()
        val documents = FakeDocumentRepository()
        val saved = SavedStateHandle()
        val model = PdfImportViewModel(editor, documents, vault, saved).also { store.put("volume", it) }
        backgroundScope.launch { model.effects.collect {} }
        val count = 2_500
        val started = System.nanoTime()
        var peakObservedHeap = 0L

        repeat(count) { index ->
            model.chooseFile(PdfImportDestination.EDITOR)
            // Repeated taps must not launch or adopt a second request.
            model.chooseFile(PdfImportDestination.SIGN)
            model.fileSelected("content://synthetic-volume/$index") { "Document $index.pdf" }
            runCurrent()
            assertEquals("Document $index", model.uiState.value.pending?.name)
            if (index % 5 == 0) {
                model.cancel()
                runCurrent()
            } else {
                model.saveForTool()
                model.saveForTool()
                runCurrent()
                val id = requireNotNull(model.uiState.value.importedDocumentId)
                model.acknowledgeToolDelivery(id)
            }
            assertFalse(model.uiState.value.isActive)
            assertNull(saved.get<String>("pending_pdf"))
            assertNull(saved.get<Long>("tool_document_id"))
            assertNull(saved.get<String>("tool_destination"))
            if (index % 100 == 0) {
                val runtime = Runtime.getRuntime()
                peakObservedHeap = maxOf(peakObservedHeap, runtime.totalMemory() - runtime.freeMemory())
            }
        }

        assertEquals(count, editor.imports)
        assertEquals(2_000, documents.created.size)
        assertEquals(500, documents.discarded.size)
        assertEquals(2_000, documents.documents.value.map { it.id }.distinct().size)
        val committedUris = documents.created.map { it.draft.pdfUri }.toSet()
        assertEquals(2_000, committedUris.size)
        assertTrue(documents.discarded.none { it.pdfUri in committedUris })
        println("LOAD_METRIC importMetadata requests=$count committed=${documents.created.size} " +
            "cancelled=${documents.discarded.size} elapsedMs=${(System.nanoTime() - started) / 1_000_000} " +
            "peakObservedHeapBytes=$peakObservedHeap")
    }

    private class MetadataEditor : PdfEditorRepository {
        var imports = 0
        override suspend fun importPdf(uri: String, password: String?, protectionKeyId: String?): ScannedDocument {
            imports++
            return ScannedDocument("file:///synthetic-owned/$imports.pdf", 100, imports.toLong(), 50L * 1024 * 1024)
        }
        override suspend fun open(document: DocumentModel, password: String?): PdfWorkspace = error("Not part of metadata workload")
        override suspend fun appendScans(workspace: PdfWorkspace, pageUris: List<String>): PdfWorkspace = error("unused")
        override suspend fun appendPdfs(workspace: PdfWorkspace, sources: List<PdfMergeSource>): PdfWorkspace = error("unused")
        override suspend fun copySignature(workspace: PdfWorkspace, imageUri: String): String = error("unused")
        override suspend fun preview(workspace: PdfWorkspace, retainedPreviewUri: String?): String = error("unused")
        override suspend fun clearPreview(workspace: PdfWorkspace) = Unit
        override suspend fun save(
            workspace: PdfWorkspace, password: String?, protectionKeyId: String?, removeProtection: Boolean,
        ): DocumentModel = error("unused")
        override suspend fun discard(workspace: PdfWorkspace) = Unit
    }
}
