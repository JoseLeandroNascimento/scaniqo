package com.joseleandro.scaniqo.ui.documentForms

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.PdfFormDocument
import com.joseleandro.scaniqo.domain.model.PdfFormExport
import com.joseleandro.scaniqo.domain.model.PdfFormField
import com.joseleandro.scaniqo.domain.model.PdfFormFieldKind
import com.joseleandro.scaniqo.domain.repository.PdfFormRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentFormsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeForms()
    private val viewModel by lazy { DocumentFormsViewModel(repository).also { store.put("forms", it) } }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun newOperationClearsEditedFieldsAndDraftButRotationKeepsThem() = runTest(dispatcher) {
        viewModel.beginOperation("first")
        viewModel.open("content://same")
        runCurrent()
        viewModel.change("name", listOf("Maria"))
        viewModel.prepareExport()
        runCurrent()
        val previous = viewModel.uiState.value
        viewModel.beginOperation("first")
        assertEquals(previous, viewModel.uiState.value)
        viewModel.beginOperation("second")
        assertEquals("second", viewModel.uiState.value.operationId)
        assertNull(viewModel.uiState.value.document)
        assertTrue(viewModel.uiState.value.values.isEmpty())
        assertNull(viewModel.uiState.value.pendingExport)
        assertFalse(viewModel.uiState.value.dirty)
        assertEquals(listOf(previous.document), repository.discardedDocuments)
        assertEquals(listOf(previous.pendingExport), repository.discardedExports)
        viewModel.open("content://same")
        runCurrent()
        assertEquals(listOf("Ana"), viewModel.uiState.value.values["name"])
        assertFalse(viewModel.uiState.value.dirty)
    }

    @Test fun newOperationWaitsForOldReaderAndIgnoresItsCompletion() = runTest(dispatcher) {
        viewModel.beginOperation("first")
        viewModel.open("content://first")
        runCurrent()
        repository.fillGate = CompletableDeferred()
        viewModel.prepareExport()
        runCurrent()
        viewModel.beginOperation("second")
        viewModel.open("content://second")
        runCurrent()
        assertTrue(repository.discardedDocuments.isEmpty())
        repository.fillGate?.complete(Unit)
        runCurrent()
        assertEquals("content://second", viewModel.uiState.value.document?.sessionId)
        assertEquals("second", viewModel.uiState.value.operationId)
        assertNull(viewModel.uiState.value.pendingExport)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.busy)
        assertEquals(listOf("content://first"), repository.discardedDocuments.map { it.sessionId })
    }

    @Test fun failedReplacementKeepsEditedFieldsAndPendingExportAvailable() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.change("name", listOf("Maria"))
        viewModel.prepareExport()
        runCurrent()
        val snapshot = viewModel.uiState.value
        repository.openFailure = IllegalStateException("inválido")
        viewModel.open("content://broken")
        runCurrent()
        assertEquals(snapshot.document, viewModel.uiState.value.document)
        assertEquals(snapshot.values, viewModel.uiState.value.values)
        assertEquals(snapshot.pendingExport, viewModel.uiState.value.pendingExport)
        assertTrue(repository.discardedDocuments.isEmpty())
        assertTrue(repository.discardedExports.isEmpty())
        assertEquals("inválido", viewModel.uiState.value.error)
    }

    @Test fun readonlyUnknownAndBusyChangesCannotModifyTheExportInput() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.change("fixed", listOf("alterado"))
        viewModel.change("missing", listOf("inventado"))
        repository.fillGate = CompletableDeferred()
        viewModel.prepareExport()
        viewModel.prepareExport()
        viewModel.change("name", listOf("during operation"))
        runCurrent()
        assertEquals(1, repository.fillInputs.size)
        assertEquals(listOf("Ana"), repository.fillInputs.single().getValue("name"))
        assertEquals(listOf("somente leitura"), repository.fillInputs.single().getValue("fixed"))
        assertFalse(repository.fillInputs.single().containsKey("missing"))
        repository.fillGate?.complete(Unit)
        runCurrent()
    }

    @Test fun changingFieldsDiscardsPreviouslyPreparedExportAndRequiresPreparationAgain() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.prepareExport()
        runCurrent()
        val prepared = requireNotNull(viewModel.uiState.value.pendingExport)
        viewModel.change("name", listOf("Nova versão"))
        viewModel.export("content://destination")
        runCurrent()
        assertEquals(listOf(prepared), repository.discardedExports)
        assertNull(viewModel.uiState.value.pendingExport)
        assertTrue(repository.exported.isEmpty())
        assertEquals(listOf("Nova versão"), viewModel.uiState.value.values["name"])
    }

    @Test fun oldDestinationCallbackCannotExportANewerEditedSnapshot() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.prepareExport()
        runCurrent()
        val first = requireNotNull(viewModel.uiState.value.pendingExport)
        viewModel.change("name", listOf("Maria"))
        viewModel.prepareExport()
        runCurrent()
        val current = viewModel.uiState.value.pendingExport
        viewModel.export("content://old-picker-result", first.id)
        runCurrent()
        assertTrue(repository.exported.isEmpty())
        assertEquals(current, viewModel.uiState.value.pendingExport)
    }

    @Test fun failedExportRetainsThePreparedSnapshotForRetry() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.change("name", listOf("Maria"))
        viewModel.prepareExport()
        runCurrent()
        val prepared = viewModel.uiState.value.pendingExport
        repository.exportFailure = IllegalStateException("destino indisponível")
        viewModel.export("content://destination")
        runCurrent()
        assertEquals(prepared, viewModel.uiState.value.pendingExport)
        assertTrue(viewModel.uiState.value.dirty)
        assertTrue(repository.discardedExports.isEmpty())
        repository.exportFailure = null
        viewModel.export("content://other")
        runCurrent()
        assertNull(viewModel.uiState.value.pendingExport)
        assertFalse(viewModel.uiState.value.dirty)
        assertEquals(listOf(prepared), repository.discardedExports)
    }

    @Test fun successfulReplacementDiscardsOldDocumentAndItsPendingCopy() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.prepareExport()
        runCurrent()
        val first = requireNotNull(viewModel.uiState.value.document)
        val prepared = requireNotNull(viewModel.uiState.value.pendingExport)
        viewModel.open("content://second")
        runCurrent()
        assertEquals("content://second", viewModel.uiState.value.document?.sessionId)
        assertEquals(listOf(first), repository.discardedDocuments)
        assertEquals(listOf(prepared), repository.discardedExports)
    }

    @Test fun cleanupWaitsUntilActiveReaderStopsUsingTheSource() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        repository.fillGate = CompletableDeferred()
        viewModel.prepareExport()
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(repository.discardedDocuments.isEmpty())
        repository.fillGate?.complete(Unit)
        runCurrent()
        assertEquals(listOf("content://first"), repository.discardedDocuments.map { it.sessionId })
        assertTrue(repository.fillReaderFinished)
    }

    private class FakeForms : PdfFormRepository {
        val discardedDocuments = mutableListOf<PdfFormDocument>()
        val discardedExports = mutableListOf<PdfFormExport>()
        val fillInputs = mutableListOf<Map<String, List<String>>>()
        val exported = mutableListOf<Pair<PdfFormExport, String>>()
        var openFailure: Exception? = null
        var exportFailure: Exception? = null
        var fillGate: CompletableDeferred<Unit>? = null
        var fillReaderFinished = false
        override suspend fun open(uri: String): PdfFormDocument {
            openFailure?.let { throw it }
            return PdfFormDocument(uri, "form.pdf", listOf(
                PdfFormField("name", "Nome", PdfFormFieldKind.TEXT, listOf("Ana")),
                PdfFormField("fixed", "Fixo", PdfFormFieldKind.TEXT, listOf("somente leitura"), readOnly = true),
            ), 0)
        }
        override suspend fun fill(document: PdfFormDocument, values: Map<String, List<String>>): PdfFormExport {
            fillInputs += values
            fillGate?.let { withContext(NonCancellable) { it.await(); fillReaderFinished = true } }
            currentCoroutineContext().ensureActive()
            return PdfFormExport("export-${fillInputs.size}", "preenchido.pdf")
        }
        override suspend fun export(result: PdfFormExport, destinationUri: String) {
            exportFailure?.let { throw it }
            exported += result to destinationUri
        }
        override fun discard(document: PdfFormDocument) { discardedDocuments += document }
        override fun discard(result: PdfFormExport) { discardedExports += result }
    }
}
