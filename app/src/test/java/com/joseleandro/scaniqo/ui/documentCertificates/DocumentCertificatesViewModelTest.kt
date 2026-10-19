package com.joseleandro.scaniqo.ui.documentCertificates

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.PdfCertificateDocument
import com.joseleandro.scaniqo.domain.model.PdfSignedExport
import com.joseleandro.scaniqo.domain.repository.PdfCertificateRepository
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
class DocumentCertificatesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeCertificates()
    private val viewModel by lazy { DocumentCertificatesViewModel(repository).also { store.put("certificates", it) } }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun newOperationDropsCertificateAndExportButSameOperationIsPreserved() = runTest(dispatcher) {
        viewModel.beginOperation("first")
        viewModel.open("content://same")
        runCurrent()
        viewModel.selectCertificate("first-key", "content://same")
        viewModel.sign()
        runCurrent()
        val previous = viewModel.uiState.value
        viewModel.beginOperation("first")
        assertEquals(previous, viewModel.uiState.value)
        viewModel.beginOperation("second")
        assertNull(viewModel.uiState.value.document)
        assertNull(viewModel.uiState.value.alias)
        assertNull(viewModel.uiState.value.pendingExport)
        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf(previous.document), repository.discardedDocuments)
        assertEquals(listOf(previous.pendingExport), repository.discardedExports)
        viewModel.open("content://same")
        runCurrent()
        assertNull(viewModel.uiState.value.alias)
        assertEquals("second", viewModel.uiState.value.operationId)
    }

    @Test fun oldSigningCompletionCannotRestoreStateInANewOperation() = runTest(dispatcher) {
        viewModel.beginOperation("first")
        viewModel.open("content://first")
        runCurrent()
        viewModel.selectCertificate("key", "content://first")
        repository.signGate = CompletableDeferred()
        viewModel.sign()
        runCurrent()
        viewModel.beginOperation("second")
        viewModel.open("content://second")
        runCurrent()
        assertTrue(repository.discardedDocuments.isEmpty())
        repository.signGate?.complete(Unit)
        runCurrent()
        assertEquals("content://second", viewModel.uiState.value.document?.sessionId)
        assertNull(viewModel.uiState.value.alias)
        assertNull(viewModel.uiState.value.pendingExport)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.busy)
        assertEquals(listOf("content://first"), repository.discardedDocuments.map { it.sessionId })
    }

    @Test fun failedReplacementKeepsCertificateSelectionAndSignedCopy() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.selectCertificate("first-key", "content://first")
        viewModel.sign()
        runCurrent()
        val snapshot = viewModel.uiState.value
        repository.openFailure = IllegalStateException("corrompido")
        viewModel.open("content://broken")
        runCurrent()
        assertEquals(snapshot.document, viewModel.uiState.value.document)
        assertEquals(snapshot.alias, viewModel.uiState.value.alias)
        assertEquals(snapshot.pendingExport, viewModel.uiState.value.pendingExport)
        assertTrue(repository.discardedDocuments.isEmpty())
        assertTrue(repository.discardedExports.isEmpty())
    }

    @Test fun changingCertificateInvalidatesOldSignedCopyButStalePickerCannotChangeCurrentSession() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.selectCertificate("first-key", "content://first")
        viewModel.sign()
        runCurrent()
        val signed = requireNotNull(viewModel.uiState.value.pendingExport)
        viewModel.selectCertificate("wrong-session", "content://old")
        assertEquals("first-key", viewModel.uiState.value.alias)
        assertEquals(signed, viewModel.uiState.value.pendingExport)
        viewModel.selectCertificate("second-key", "content://first")
        assertNull(viewModel.uiState.value.pendingExport)
        assertEquals(listOf(signed), repository.discardedExports)
        viewModel.export("content://destination")
        runCurrent()
        assertTrue(repository.exported.isEmpty())
    }

    @Test fun cancelledCertificatePickerKeepsTheCurrentCertificateAndSignedCopy() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.selectCertificate("first-key", "content://first")
        viewModel.sign()
        runCurrent()
        val signed = viewModel.uiState.value.pendingExport
        viewModel.selectCertificate(null, "content://first")
        assertEquals("first-key", viewModel.uiState.value.alias)
        assertEquals(signed, viewModel.uiState.value.pendingExport)
        assertTrue(repository.discardedExports.isEmpty())
    }

    @Test fun staleDestinationCallbackDoesNotExportTheCopySignedWithAnotherCertificate() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.selectCertificate("first-key", "content://first")
        viewModel.sign()
        runCurrent()
        val first = requireNotNull(viewModel.uiState.value.pendingExport)
        viewModel.selectCertificate("second-key", "content://first")
        viewModel.sign()
        runCurrent()
        val current = viewModel.uiState.value.pendingExport
        viewModel.export("content://old-picker-result", first.id)
        runCurrent()
        assertTrue(repository.exported.isEmpty())
        assertEquals(current, viewModel.uiState.value.pendingExport)
    }

    @Test fun busySigningIgnoresDuplicateSignAndCertificateReplacement() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.selectCertificate("first-key", "content://first")
        repository.signGate = CompletableDeferred()
        viewModel.sign()
        viewModel.sign()
        viewModel.selectCertificate("second-key", "content://first")
        runCurrent()
        assertEquals(listOf("first-key"), repository.signedAliases)
        assertEquals("first-key", viewModel.uiState.value.alias)
        repository.signGate?.complete(Unit)
        runCurrent()
        assertNotNull(viewModel.uiState.value.pendingExport)
    }

    @Test fun documentThatCannotBeSignedNeverReachesKeyOperation() = runTest(dispatcher) {
        repository.canSign = false
        viewModel.open("content://signed")
        runCurrent()
        viewModel.selectCertificate("key", "content://signed")
        viewModel.sign()
        runCurrent()
        assertTrue(repository.signedAliases.isEmpty())
        assertNull(viewModel.uiState.value.pendingExport)
    }

    @Test fun failedExportKeepsSignedSnapshotUntilSuccessfulRetry() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.selectCertificate("key", "content://first")
        viewModel.sign()
        runCurrent()
        val signed = viewModel.uiState.value.pendingExport
        repository.exportFailure = IllegalStateException("provider offline")
        viewModel.export("content://destination")
        runCurrent()
        assertEquals(signed, viewModel.uiState.value.pendingExport)
        assertTrue(repository.discardedExports.isEmpty())
        repository.exportFailure = null
        viewModel.export("content://other")
        runCurrent()
        assertNull(viewModel.uiState.value.pendingExport)
        assertEquals(listOf(signed), repository.discardedExports)
    }

    @Test fun clearWaitsForSignerBeforeDeletingItsSource() = runTest(dispatcher) {
        viewModel.open("content://first")
        runCurrent()
        viewModel.selectCertificate("key", "content://first")
        repository.signGate = CompletableDeferred()
        viewModel.sign()
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(repository.discardedDocuments.isEmpty())
        repository.signGate?.complete(Unit)
        runCurrent()
        assertTrue(repository.signReaderFinished)
        assertEquals(listOf("content://first"), repository.discardedDocuments.map { it.sessionId })
    }

    private class FakeCertificates : PdfCertificateRepository {
        val discardedDocuments = mutableListOf<PdfCertificateDocument>()
        val discardedExports = mutableListOf<PdfSignedExport>()
        val signedAliases = mutableListOf<String>()
        val exported = mutableListOf<Pair<PdfSignedExport, String>>()
        var canSign = true
        var openFailure: Exception? = null
        var exportFailure: Exception? = null
        var signGate: CompletableDeferred<Unit>? = null
        var signReaderFinished = false
        override suspend fun open(uri: String): PdfCertificateDocument {
            openFailure?.let { throw it }
            return PdfCertificateDocument(uri, "document.pdf", emptyList(), canSign)
        }
        override suspend fun sign(document: PdfCertificateDocument, certificateAlias: String): PdfSignedExport {
            signedAliases += certificateAlias
            signGate?.let { withContext(NonCancellable) { it.await(); signReaderFinished = true } }
            currentCoroutineContext().ensureActive()
            return PdfSignedExport("signed-${signedAliases.size}", "assinado.pdf")
        }
        override suspend fun export(result: PdfSignedExport, destinationUri: String) {
            exportFailure?.let { throw it }
            exported += result to destinationUri
        }
        override fun discard(document: PdfCertificateDocument) { discardedDocuments += document }
        override fun discard(result: PdfSignedExport) { discardedExports += result }
    }
}
