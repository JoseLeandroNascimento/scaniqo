package com.joseleandro.scaniqo.ui.pdfTools

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.joseleandro.scaniqo.domain.model.PdfCompressionResult
import com.joseleandro.scaniqo.domain.model.PdfImageFormat
import com.joseleandro.scaniqo.domain.model.ReceiptDraft
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.PdfUtilityRepository
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfUtilityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val documents = FakeDocumentRepository()
    private val utility = FakeUtility()
    private val original = DocumentModel(7, "Original", "file:///original.pdf", 5, 1, 1024, tagId = 3)
    private val viewModel by lazy {
        PdfUtilityViewModel(utility, documents).also { store.put("utility", it) }
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        documents.documents.value = listOf(original)
    }
    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun newOperationClearsOldResultsButKeepsLibraryAndSameOperationSurvivesRecreation() = runTest(dispatcher) {
        viewModel.beginOperation("first")
        viewModel.compress(original.id, "Comprimido", PdfCompressionLevel.BALANCED, null)
        runCurrent()
        val snapshot = viewModel.uiState.value
        assertNotNull(snapshot.compressionResult)
        viewModel.beginOperation("first")
        assertEquals(snapshot, viewModel.uiState.value)
        viewModel.reportError("erro antigo")
        viewModel.beginOperation("second")
        assertNull(viewModel.uiState.value.compressionResult)
        assertNull(viewModel.uiState.value.createdDocumentId)
        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.message)
        assertEquals(snapshot.documents, viewModel.uiState.value.documents)
        assertEquals(2, documents.documents.value.size)
        assertTrue(documents.deleted.isEmpty())
        viewModel.compress(original.id, "Outra cópia", PdfCompressionLevel.STRONG, null)
        runCurrent()
        assertEquals(2, utility.compressed.size)
        assertNotNull(viewModel.uiState.value.compressionResult)
    }

    @Test fun newOperationDoesNotReceiveCompletionFromAnEarlierCommit() = runTest(dispatcher) {
        viewModel.beginOperation("first")
        documents.writeGate = CompletableDeferred()
        viewModel.compress(original.id, "Cópia", PdfCompressionLevel.BALANCED, null)
        runCurrent()
        assertTrue(viewModel.uiState.value.busy)
        viewModel.beginOperation("second")
        viewModel.exportImages(original.id, "1", PdfImageFormat.PNG, "content://destination", null)
        runCurrent()
        val message = viewModel.uiState.value.message
        assertNotNull(message)
        documents.writeGate?.complete(Unit)
        runCurrent()
        assertEquals("second", viewModel.uiState.value.operationId)
        assertEquals(message, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.compressionResult)
        assertNull(viewModel.uiState.value.createdDocumentId)
        assertFalse(viewModel.uiState.value.busy)
        assertEquals(2, documents.documents.value.size)
        assertTrue(documents.discarded.isEmpty())
    }

    @Test fun newOperationInvalidatesReceiptExportsAndDoesNotRetainSensitiveFields() = runTest(dispatcher) {
        viewModel.beginOperation("first")
        viewModel.readReceipt(original.id, "1", null)
        runCurrent()
        val token = viewModel.prepareReceiptExport()
        assertNotNull(token)
        viewModel.beginOperation("second")
        assertNull(viewModel.uiState.value.receipt)
        assertFalse(viewModel.uiState.value.receiptProtected)
        viewModel.completeReceiptExport("content://old-result", token)
        runCurrent()
        assertNull(utility.exportedReceipt)
    }

    @Test fun compressionCreatesProtectedCopyAndReportsMeasuredSizesWithoutLeavingResults() = runTest(dispatcher) {
        val protected = original.copy(protectionKeyId = "key")
        documents.documents.value = listOf(protected)
        viewModel.compress(original.id, "  Original compacto  ", PdfCompressionLevel.STRONG, "secret")
        viewModel.compress(original.id, "Duplicado", PdfCompressionLevel.BALANCED, "secret")
        runCurrent()
        assertEquals(listOf(Triple(protected, PdfCompressionLevel.STRONG, "secret")), utility.compressed)
        assertEquals("Original compacto", documents.created.single().name)
        assertEquals(3L, documents.created.single().tagId)
        assertEquals("key", documents.created.single().draft.protectionKeyId)
        assertEquals(protected, documents.documents.value.first())
        assertTrue(documents.updated.isEmpty())
        val result = requireNotNull(viewModel.uiState.value.compressionResult)
        assertEquals(1024L, result.originalBytes)
        assertEquals(500L, result.resultBytes)
        assertNotNull(result.documentId)
        assertNull(viewModel.uiState.value.createdDocumentId)
    }

    @Test fun compressionWithoutSavingsDoesNotCreateRedundantCopy() = runTest(dispatcher) {
        utility.compressionHasSavings = false
        viewModel.compress(original.id, "Cópia", PdfCompressionLevel.BALANCED, null)
        runCurrent()
        assertTrue(documents.created.isEmpty())
        assertEquals(PdfCompressionSummary(1024, 1024, null), viewModel.uiState.value.compressionResult)
        viewModel.clearCompressionResult()
        assertNull(viewModel.uiState.value.compressionResult)
    }

    @Test fun failedCompressionCommitDiscardsPreparedFileAndKeepsOriginal() = runTest(dispatcher) {
        documents.writeFailure = IllegalStateException("sem espaço")
        viewModel.compress(original.id, "Cópia", PdfCompressionLevel.BALANCED, null)
        runCurrent()
        assertEquals(listOf(utility.scan), documents.discarded)
        assertEquals(listOf(original), documents.documents.value)
        assertNull(viewModel.uiState.value.compressionResult)
        assertEquals("sem espaço", viewModel.uiState.value.error)
    }

    @Test fun compressionValidatesNameBeforePreparingFile() = runTest(dispatcher) {
        viewModel.compress(original.id, " ", PdfCompressionLevel.BALANCED, null)
        runCurrent()
        assertTrue(utility.compressed.isEmpty())
        assertTrue(documents.created.isEmpty())
    }

    @Test fun extractCreatesIndependentDocumentInRequestedOrderAndPreservesOriginal() = runTest(dispatcher) {
        viewModel.extract(original.id, "5, 2-3", "  Seleção  ", null)
        viewModel.extract(original.id, "1", "Duplicado", null)
        runCurrent()
        assertEquals(listOf(4, 1, 2), utility.extracted.single().second)
        assertEquals("Seleção", documents.created.single().name)
        assertEquals(3L, documents.created.single().tagId)
        assertEquals(original, documents.documents.value.first())
        assertTrue(documents.deleted.isEmpty())
        assertTrue(documents.updated.isEmpty())
        assertNotNull(viewModel.uiState.value.createdDocumentId)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test fun validationFailureDoesNotCreateOrChangeFiles() = runTest(dispatcher) {
        viewModel.extract(original.id, "8", "Seleção", null)
        runCurrent()
        assertTrue(utility.extracted.isEmpty())
        assertTrue(documents.created.isEmpty())
        assertEquals(listOf(original), documents.documents.value)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test fun existingNameLimitIsEnforcedBeforePreparingImages() = runTest(dispatcher) {
        viewModel.createFromImages(listOf("content://image"), "a".repeat(101), false)
        runCurrent()
        assertTrue(utility.images.isEmpty())
        assertTrue(documents.created.isEmpty())
    }

    @Test fun failedDatabaseWriteDiscardsOnlyPreparedResult() = runTest(dispatcher) {
        documents.writeFailure = IllegalStateException("sem espaço")
        viewModel.extract(original.id, "1", "Seleção", null)
        runCurrent()
        assertEquals(listOf(utility.scan), documents.discarded)
        assertEquals(listOf(original), documents.documents.value)
        assertEquals("sem espaço", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.createdDocumentId)
    }

    @Test fun committedCreationFinishesEvenIfScreenClosesDuringDatabaseWrite() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        documents.writeGate = gate
        viewModel.createFromImages(listOf("content://image"), "Nova imagem", false)
        runCurrent()
        assertTrue(viewModel.uiState.value.busy)
        store.clear()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, documents.documents.value.size)
        assertTrue(documents.discarded.isEmpty())
        assertEquals(original, documents.documents.value.first())
    }

    @Test fun receiptUsesOnePageAndReviewedFieldsAreExported() = runTest(dispatcher) {
        viewModel.readReceipt(original.id, "2", null)
        runCurrent()
        assertEquals(1, utility.receiptPage)
        val revised = requireNotNull(viewModel.uiState.value.receipt).copy(total = "42,90", items = "Item corrigido")
        viewModel.updateReceipt(revised)
        viewModel.exportReceipt("content://csv")
        runCurrent()
        assertEquals(revised, utility.exportedReceipt)
        assertTrue(documents.created.isEmpty())
        assertEquals(listOf(original), documents.documents.value)
    }

    @Test fun protectedReceiptIsClearedOnBackground() = runTest(dispatcher) {
        documents.documents.value = listOf(original.copy(protectionKeyId = "key"))
        viewModel.readReceipt(original.id, "1", "secret")
        runCurrent()
        assertTrue(viewModel.uiState.value.receiptProtected)
        viewModel.backgrounded()
        assertNull(viewModel.uiState.value.receipt)
        assertFalse(viewModel.uiState.value.receiptProtected)
    }

    @Test fun protectedCsvExportsReviewedSnapshotAfterVisibleReceiptLocksOnBackground() = runTest(dispatcher) {
        documents.documents.value = listOf(original.copy(protectionKeyId = "key"))
        viewModel.readReceipt(original.id, "1", "secret")
        runCurrent()
        viewModel.updateReceipt(requireNotNull(viewModel.uiState.value.receipt).copy(total = "42,90"))
        val token = viewModel.prepareReceiptExport()
        viewModel.backgrounded()
        assertNull(viewModel.uiState.value.receipt)
        viewModel.completeReceiptExport("content://csv", token)
        runCurrent()
        assertEquals("42,90", utility.exportedReceipt?.total)
        assertEquals("", utility.exportedReceipt?.sourceText)
        assertNull(viewModel.uiState.value.receipt)
    }

    @Test fun staleCsvPickerCannotExportDifferentReviewSnapshot() = runTest(dispatcher) {
        viewModel.readReceipt(original.id, "1", null)
        runCurrent()
        val oldToken = viewModel.prepareReceiptExport()
        viewModel.updateReceipt(requireNotNull(viewModel.uiState.value.receipt).copy(total = "99,00"))
        val token = viewModel.prepareReceiptExport()
        viewModel.completeReceiptExport("content://old-callback", oldToken)
        runCurrent()
        assertNull(utility.exportedReceipt)
        viewModel.completeReceiptExport("content://csv", token)
        runCurrent()
        assertEquals("99,00", utility.exportedReceipt?.total)
    }

    @Test fun cancelledCsvPickerDoesNotRetainItsExportSnapshot() = runTest(dispatcher) {
        viewModel.readReceipt(original.id, "1", null)
        runCurrent()
        val token = viewModel.prepareReceiptExport()
        viewModel.completeReceiptExport(null, token)
        viewModel.completeReceiptExport("content://late-callback", token)
        runCurrent()
        assertNull(utility.exportedReceipt)
    }

    @Test fun observationCanRecoverWithoutMultipleCollectors() = runTest(dispatcher) {
        documents.loadFailure = IllegalStateException("offline")
        viewModel
        runCurrent()
        assertTrue(viewModel.uiState.value.loadError)
        documents.loadFailure = null
        viewModel.retryDocuments()
        viewModel.retryDocuments()
        runCurrent()
        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(listOf(original), viewModel.uiState.value.documents)
        assertEquals(1, documents.activeObservers)
    }

    @Test fun staleDocumentSelectionDoesNotUseOldFile() = runTest(dispatcher) {
        viewModel
        runCurrent()
        documents.documents.value = emptyList()
        viewModel.extract(original.id, "1", "Seleção", null)
        runCurrent()
        assertTrue(utility.extracted.isEmpty())
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test fun imageOrderAndIdModeReachTheRepositoryUnchanged() = runTest(dispatcher) {
        viewModel.createFromImages(listOf("content://front", "content://back"), "Identidade", true)
        runCurrent()
        assertEquals(listOf("content://front", "content://back") to true, utility.images.single())
    }

    private class FakeUtility : PdfUtilityRepository {
        val scan = ScannedDocument("file:///new/document.pdf", 1, 1, 500)
        val extracted = mutableListOf<Pair<DocumentModel, List<Int>>>()
        val compressed = mutableListOf<Triple<DocumentModel, PdfCompressionLevel, String?>>()
        var compressionHasSavings = true
        val images = mutableListOf<Pair<List<String>, Boolean>>()
        var receiptPage: Int? = null
        var exportedReceipt: ReceiptDraft? = null

        override suspend fun compress(document: DocumentModel, level: PdfCompressionLevel, password: String?): PdfCompressionResult {
            compressed += Triple(document, level, password)
            return if (compressionHasSavings) {
                PdfCompressionResult(scan.copy(protectionKeyId = document.protectionKeyId), 1024, scan.sizeBytes)
            } else PdfCompressionResult(null, 1024, 1024)
        }
        override suspend fun extractPages(document: DocumentModel, pages: List<Int>, password: String?): ScannedDocument {
            extracted += document to pages
            return scan.copy(protectionKeyId = document.protectionKeyId)
        }
        override suspend fun exportImages(document: DocumentModel, pages: List<Int>, format: PdfImageFormat,
            targetUri: String, password: String?) = Unit
        override suspend fun imagesToPdf(imageUris: List<String>, idCard: Boolean): ScannedDocument {
            images += imageUris to idCard
            return scan
        }
        override suspend fun readReceipt(document: DocumentModel, page: Int, password: String?): ReceiptDraft {
            receiptPage = page
            return ReceiptDraft(merchant = "Mercado", total = "12,00", sourceText = "TOTAL 12,00")
        }
        override suspend fun exportReceipt(receipt: ReceiptDraft, targetUri: String) { exportedReceipt = receipt }
    }
}
