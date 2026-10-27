package com.joseleandro.scaniqo.ui.pdfTools

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.ads.ToolAdAccess
import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.core.ads.ToolAdAccessPolicy
import com.joseleandro.scaniqo.core.ads.ToolAdPermit
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StrongCompressionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private val documents = FakeDocumentRepository()
    private val repository = FakeUtility()
    private val policy = FakeAccessPolicy()
    private val original = DocumentModel(7, "Original", "file:///original.pdf", 5, 1, 1024, tagId = 3)
    private val viewModel by lazy {
        PdfUtilityViewModel(repository, documents, adAccess = policy).also {
            viewModels.put("compression", it)
            it.beginOperation("compress")
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        documents.documents.value = listOf(original)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun requiredAdBlocksStrongCompressionBeforeTouchingTheRepository() = runTest(dispatcher) {
        policy.available = ToolAdAccess.AD_REQUIRED

        assertFalse(prepareStrong())
        runCurrent()

        assertTrue(repository.compressed.isEmpty())
        assertNull(policy.reserved)
        assertEquals(ToolAdAccess.AD_REQUIRED, policy.access(RewardedTool.COMPRESSION))
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun balancedCompressionAlsoRequiresVideoBeforeTouchingTheRepository() = runTest(dispatcher) {
        policy.available = ToolAdAccess.AD_REQUIRED
        assertFalse(viewModel.prepareCompression(original.id, PdfCompressionLevel.BALANCED, null, "Compacto"))
        runCurrent()
        assertTrue(repository.compressed.isEmpty())

        policy.grantReward(RewardedTool.COMPRESSION, "balanced-ad")
        assertTrue(viewModel.prepareCompression(original.id, PdfCompressionLevel.BALANCED, null, "Compacto"))
        runCurrent()
        val permit = requireNotNull(policy.reserved)
        assertEquals(RewardedTool.COMPRESSION, permit.tool)
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)
        viewModel.finishGenerated(output.operationId, 42)

        assertEquals(listOf(PdfCompressionLevel.BALANCED), repository.compressed)
        assertEquals(listOf(permit to true), policy.finishes)
        assertEquals(ToolAdAccess.AD_REQUIRED, policy.available)
    }

    @Test
    fun matchingEarnedUseIsConsumedOnlyByTheMatchingSuccessfulSave() = runTest(dispatcher) {
        assertTrue(prepareStrong())
        runCurrent()
        val permit = requireNotNull(policy.reserved)
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)

        assertTrue(policy.finishes.isEmpty())
        viewModel.confirmPreparedDelivery(output.operationId)
        assertTrue(policy.finishes.isEmpty())
        assertSame(permit, policy.reserved)
        viewModel.finishGenerated("unrelated-operation", 42)
        assertSame(permit, policy.reserved)
        assertTrue(policy.finishes.isEmpty())

        viewModel.finishGenerated(output.operationId, 42)
        viewModel.finishGenerated(output.operationId, 42)

        assertEquals(listOf(permit to true), policy.finishes)
        assertNull(policy.reserved)
        assertEquals(ToolAdAccess.AD_REQUIRED, policy.access(RewardedTool.COMPRESSION))
    }

    @Test
    fun earnedUseIsNotConsumedWhenPreparedOrDeliveredToTheSaveSheet() = runTest(dispatcher) {
        policy.available = ToolAdAccess.REWARDED
        assertTrue(prepareStrong())
        runCurrent()
        val permit = requireNotNull(policy.reserved)
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)

        viewModel.confirmPreparedDelivery(output.operationId)

        assertEquals(ToolAdAccess.REWARDED, policy.available)
        assertTrue(policy.finishes.isEmpty())
        viewModel.finishGenerated(output.operationId, 42)
        assertEquals(listOf(permit to true), policy.finishes)
        assertEquals(ToolAdAccess.AD_REQUIRED, policy.available)
    }

    @Test
    fun cancellingSavePreservesTheEarnedUseForAnotherAttempt() = runTest(dispatcher) {
        policy.available = ToolAdAccess.REWARDED
        assertTrue(prepareStrong())
        runCurrent()
        val permit = requireNotNull(policy.reserved)
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)
        viewModel.confirmPreparedDelivery(output.operationId)

        viewModel.finishGenerated(output.operationId, null)

        assertEquals(listOf(permit to false), policy.finishes)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
        assertNull(viewModel.uiState.value.awaitingSaveOperationId)
        assertTrue(prepareStrong())
        runCurrent()
        assertEquals(2, repository.compressed.size)
    }

    @Test
    fun compressionFailureReleasesReservationWithoutSpendingEarnedUse() = runTest(dispatcher) {
        repository.failure = IllegalStateException("Arquivo inválido")
        assertTrue(prepareStrong())
        val permit = requireNotNull(policy.reserved)
        runCurrent()

        assertEquals(listOf(permit to false), policy.finishes)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
        assertNull(policy.reserved)
        assertEquals("Arquivo inválido", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.awaitingSaveOperationId)
    }

    @Test
    fun missingSourceReleasesReservationWithoutInvokingCompression() = runTest(dispatcher) {
        documents.documents.value = emptyList()
        assertTrue(prepareStrong())
        val permit = requireNotNull(policy.reserved)
        runCurrent()

        assertTrue(repository.compressed.isEmpty())
        assertEquals(listOf(permit to false), policy.finishes)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
    }

    @Test
    fun outputWithoutSavingsPreservesEarnedUseAndDoesNotOpenSaveSheet() = runTest(dispatcher) {
        policy.available = ToolAdAccess.REWARDED
        repository.result = PdfCompressionResult(null, 1024, 1024)
        assertTrue(prepareStrong())
        val permit = requireNotNull(policy.reserved)
        runCurrent()

        assertEquals(listOf(permit to false), policy.finishes)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
        assertNull(policy.reserved)
        assertNull(viewModel.uiState.value.preparedOutput)
        assertNull(viewModel.uiState.value.awaitingSaveOperationId)
    }

    @Test
    fun doubleTapAndPendingSaveCannotReserveOrRunTwice() = runTest(dispatcher) {
        assertTrue(prepareStrong())
        assertFalse(prepareStrong())
        runCurrent()
        assertFalse(prepareStrong())
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)
        viewModel.confirmPreparedDelivery(output.operationId)
        assertFalse(prepareStrong())

        assertEquals(1, policy.acquisitions.size)
        assertEquals(1, repository.compressed.size)
        assertTrue(policy.finishes.isEmpty())
    }

    @Test
    fun backgroundCancellationDuringCompressionPreservesCreditAndAllowsRetry() = runTest(dispatcher) {
        policy.available = ToolAdAccess.REWARDED
        repository.gate = CompletableDeferred()
        assertTrue(prepareStrong())
        runCurrent()
        val permit = requireNotNull(policy.reserved)

        viewModel.backgrounded()
        runCurrent()

        assertEquals(listOf(permit to false), policy.finishes)
        assertNull(policy.reserved)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
        assertFalse(viewModel.uiState.value.busy)
        repository.gate = null
        assertTrue(prepareStrong())
        runCurrent()
        assertNotNull(viewModel.uiState.value.preparedOutput)
    }

    @Test
    fun backgroundCancellationBeforeCoroutineStartsDoesNotLeakReservation() = runTest(dispatcher) {
        assertTrue(prepareStrong())
        val permit = requireNotNull(policy.reserved)

        viewModel.backgrounded()
        runCurrent()

        assertEquals(listOf(permit to false), policy.finishes)
        assertNull(policy.reserved)
        assertFalse(viewModel.uiState.value.busy)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
        assertTrue(prepareStrong())
        runCurrent()
    }

    @Test
    fun clearingViewModelDuringCompressionReleasesOnlyOnce() = runTest(dispatcher) {
        policy.available = ToolAdAccess.REWARDED
        repository.gate = CompletableDeferred()
        assertTrue(prepareStrong())
        runCurrent()
        val permit = requireNotNull(policy.reserved)

        viewModels.clear()
        runCurrent()

        assertEquals(listOf(permit to false), policy.finishes)
        assertNull(policy.reserved)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
    }

    @Test
    fun clearingViewModelWithUnsavedOutputPreservesEarnedUse() = runTest(dispatcher) {
        assertTrue(prepareStrong())
        runCurrent()
        val permit = requireNotNull(policy.reserved)

        viewModels.clear()
        runCurrent()

        assertEquals(listOf(permit to false), policy.finishes)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
    }

    @Test
    fun previousCancelledRequestCannotReleaseTheNewRequestsReservation() = runTest(dispatcher) {
        val oldGate = CompletableDeferred<Unit>()
        repository.gate = oldGate
        repository.ignoreCancellation = true
        assertTrue(prepareStrong())
        runCurrent()
        val oldPermit = requireNotNull(policy.reserved)

        viewModel.beginOperation("replacement")
        val newGate = CompletableDeferred<Unit>()
        repository.gate = newGate
        repository.ignoreCancellation = false
        assertTrue(prepareStrong())
        runCurrent()
        val newPermit = requireNotNull(policy.reserved)
        assertEquals(listOf(oldPermit to false), policy.finishes)

        oldGate.complete(Unit)
        runCurrent()

        assertSame(newPermit, policy.reserved)
        assertEquals(listOf(oldPermit to false), policy.finishes)
        assertTrue(viewModel.uiState.value.busy)
        newGate.complete(Unit)
        runCurrent()
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)
        assertTrue(output.operationId.startsWith("replacement:"))
        viewModel.finishGenerated(output.operationId, 42)
        assertEquals(listOf(oldPermit to false, newPermit to true), policy.finishes)
    }

    @Test
    fun staleSaveResultCannotConsumePermitForReplacementOutput() = runTest(dispatcher) {
        assertTrue(prepareStrong())
        runCurrent()
        val oldPermit = requireNotNull(policy.reserved)
        val oldOutput = requireNotNull(viewModel.uiState.value.preparedOutput)
        viewModel.confirmPreparedDelivery(oldOutput.operationId)

        viewModel.beginOperation("replacement")
        assertTrue(prepareStrong())
        runCurrent()
        val newPermit = requireNotNull(policy.reserved)
        val newOutput = requireNotNull(viewModel.uiState.value.preparedOutput)
        viewModel.finishGenerated(oldOutput.operationId, 41)

        assertSame(newPermit, policy.reserved)
        assertEquals(listOf(oldPermit to false), policy.finishes)
        viewModel.finishGenerated(newOutput.operationId, 42)
        assertEquals(listOf(oldPermit to false, newPermit to true), policy.finishes)
    }

    @Test
    fun adUnavailableFallbackReachesRepositoryAndDoesNotInventCredit() = runTest(dispatcher) {
        policy.available = ToolAdAccess.AD_REQUIRED

        assertTrue(viewModel.prepareCompression(
            original.id, PdfCompressionLevel.STRONG, null, "Compacto", adUnavailable = true,
        ))
        runCurrent()
        val permit = requireNotNull(policy.reserved)
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)
        viewModel.finishGenerated(output.operationId, 42)

        assertEquals(listOf(true), policy.acquisitions.map { it.second })
        assertEquals(listOf(PdfCompressionLevel.STRONG), repository.compressed)
        assertEquals(listOf(permit to true), policy.finishes)
        assertEquals(ToolAdAccess.AD_REQUIRED, policy.available)
    }

    @Test
    fun successfulCompressionRequiresAnotherVideoForAnImmediateRepeat() = runTest(dispatcher) {
        assertTrue(prepareStrong())
        runCurrent()
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)
        viewModel.finishGenerated(output.operationId, 42)
        viewModel.clearGeneratedCompletion()

        assertFalse(prepareStrong())
        assertEquals(1, repository.compressed.size)
        policy.grantReward(RewardedTool.COMPRESSION, "next-ad")
        assertTrue(prepareStrong())
        runCurrent()
        assertEquals(2, repository.compressed.size)
    }

    @Test
    fun legacyCompressionEntryCannotBypassTheGateAtEitherQuality() = runTest(dispatcher) {
        policy.available = ToolAdAccess.AD_REQUIRED
        assertFalse(viewModel.compress(original.id, "Forte", PdfCompressionLevel.STRONG, null))
        assertFalse(viewModel.compress(original.id, "Equilibrada", PdfCompressionLevel.BALANCED, null))
        runCurrent()
        assertTrue(repository.compressed.isEmpty())
        assertTrue(documents.created.isEmpty())

        policy.grantReward(RewardedTool.COMPRESSION, "balanced-ad")
        assertTrue(viewModel.compress(original.id, "Equilibrada", PdfCompressionLevel.BALANCED, null))
        runCurrent()
        assertEquals(listOf(PdfCompressionLevel.BALANCED), repository.compressed)
        assertEquals(1, documents.created.size)
        assertEquals(listOf(true), policy.finishes.map { it.second })
        assertEquals(ToolAdAccess.AD_REQUIRED, policy.available)
    }

    @Test
    fun legacyCompressionConsumesEarnedUseOnlyAfterDatabaseWriteSucceeds() = runTest(dispatcher) {
        val writeGate = CompletableDeferred<Unit>()
        documents.writeGate = writeGate
        assertTrue(viewModel.compress(original.id, "Forte", PdfCompressionLevel.STRONG, null))
        runCurrent()
        val permit = requireNotNull(policy.reserved)
        assertTrue(policy.finishes.isEmpty())
        assertEquals(ToolAdAccess.REWARDED, policy.available)

        writeGate.complete(Unit)
        runCurrent()

        assertEquals(listOf(permit to true), policy.finishes)
        assertEquals(ToolAdAccess.AD_REQUIRED, policy.available)
        assertNotNull(viewModel.uiState.value.compressionResult?.documentId)
    }

    @Test
    fun legacyCompressionDatabaseFailurePreservesRewardAndDiscardsTemporaryOutput() = runTest(dispatcher) {
        policy.available = ToolAdAccess.REWARDED
        documents.writeFailure = IllegalStateException("Sem espaço")
        assertTrue(viewModel.compress(original.id, "Forte", PdfCompressionLevel.STRONG, null))
        val permit = requireNotNull(policy.reserved)
        runCurrent()

        assertEquals(listOf(permit to false), policy.finishes)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
        assertEquals(listOf(repository.result.scan), documents.discarded)
        assertNull(viewModel.uiState.value.compressionResult)
        assertEquals("Sem espaço", viewModel.uiState.value.error)
    }


    @Test
    fun extractionWaitsForMatchingMetadataSaveBeforeSpendingTheCorrectTool() = runTest(dispatcher) {
        assertTrue(viewModel.prepareExtraction(original.id, "1,3", null, "Páginas"))
        runCurrent()
        val permit = requireNotNull(policy.reserved)
        assertEquals(RewardedTool.EXTRACT_PAGES, permit.tool)
        assertEquals(listOf(listOf(0, 2)), repository.extracted)
        assertTrue(policy.finishes.isEmpty())
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)
        viewModel.finishGenerated("stale", 42)
        assertTrue(policy.finishes.isEmpty())
        viewModel.finishGenerated(output.operationId, 42)
        assertEquals(listOf(permit to true), policy.finishes)
    }

    @Test
    fun extractionCancelledSaveAndFailurePreserveEntitlement() = runTest(dispatcher) {
        policy.available = ToolAdAccess.REWARDED
        assertTrue(viewModel.prepareExtraction(original.id, "1", null, "Páginas"))
        runCurrent()
        val permit = requireNotNull(policy.reserved)
        viewModel.finishGenerated(requireNotNull(viewModel.uiState.value.preparedOutput).operationId, null)
        assertEquals(listOf(permit to false), policy.finishes)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
        repository.failure = IllegalStateException("Falhou")
        assertTrue(viewModel.prepareExtraction(original.id, "1", null, "Páginas"))
        runCurrent()
        assertEquals(listOf(false, false), policy.finishes.map { it.second })
        assertNull(policy.reserved)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
    }

    @Test
    fun extractionAndLegacyEntryCannotBypassTheRequiredAd() = runTest(dispatcher) {
        policy.available = ToolAdAccess.AD_REQUIRED
        assertFalse(viewModel.prepareExtraction(original.id, "1", null, "Páginas"))
        assertFalse(viewModel.extract(original.id, "1", "Páginas", null))
        runCurrent()
        assertTrue(repository.extracted.isEmpty())
        assertTrue(viewModel.prepareExtraction(original.id, "1", null, "Páginas", adUnavailable = true))
        runCurrent()
        assertEquals(1, repository.extracted.size)
    }

    @Test
    fun invalidPageSelectionDoesNotSpendExtractionEntitlement() = runTest(dispatcher) {
        assertTrue(viewModel.prepareExtraction(original.id, "999", null, "Páginas"))
        runCurrent()
        assertTrue(repository.extracted.isEmpty())
        assertEquals(listOf(false), policy.finishes.map { it.second })
        assertNull(policy.reserved)
    }

    @Test
    fun imageExportSpendsOnlyAfterZipWriteAndIgnoresDoubleClick() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        assertTrue(exportImages())
        assertFalse(exportImages())
        runCurrent()
        val permit = requireNotNull(policy.reserved)
        assertEquals(RewardedTool.PDF_TO_IMAGES, permit.tool)
        assertTrue(policy.finishes.isEmpty())
        assertFalse(viewModel.uiState.value.exportCompleted)
        repository.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(permit to true), policy.finishes)
        assertTrue(viewModel.uiState.value.exportCompleted)
        assertEquals(listOf(listOf(0, 2)), repository.exported)
    }

    @Test
    fun imageExportFailureAndCancellationKeepReward() = runTest(dispatcher) {
        policy.available = ToolAdAccess.REWARDED
        repository.failure = IllegalStateException("Sem espaço")
        assertTrue(exportImages())
        runCurrent()
        assertEquals(listOf(false), policy.finishes.map { it.second })
        assertFalse(viewModel.uiState.value.exportCompleted)
        repository.failure = null
        repository.gate = CompletableDeferred()
        assertTrue(exportImages())
        runCurrent()
        viewModel.backgrounded()
        runCurrent()
        assertEquals(listOf(false, false), policy.finishes.map { it.second })
        assertNull(policy.reserved)
        assertEquals(ToolAdAccess.REWARDED, policy.available)
    }

    @Test
    fun imageExportRequiresRewardButUnavailableAdDoesNotBlockExport() = runTest(dispatcher) {
        policy.available = ToolAdAccess.AD_REQUIRED
        assertFalse(exportImages())
        runCurrent()
        assertTrue(repository.exported.isEmpty())
        assertTrue(viewModel.exportImages(
            original.id, "1", PdfImageFormat.JPEG, "content://out.zip", null, adUnavailable = true,
        ))
        runCurrent()
        assertTrue(viewModel.uiState.value.exportCompleted)
        assertEquals(ToolAdAccess.AD_REQUIRED, policy.available)
    }

    @Test
    fun pendingExtractionPreventsCrossToolOperationAndStaleSave() = runTest(dispatcher) {
        assertTrue(viewModel.prepareExtraction(original.id, "1", null, "Páginas"))
        runCurrent()
        val output = requireNotNull(viewModel.uiState.value.preparedOutput)
        val oldPermit = requireNotNull(policy.reserved)
        assertFalse(exportImages())
        viewModel.confirmPreparedDelivery(output.operationId)
        viewModel.beginOperation("images")
        repository.gate = CompletableDeferred()
        assertTrue(exportImages())
        val newPermit = requireNotNull(policy.reserved)
        viewModel.finishGenerated(output.operationId, 42)
        assertSame(newPermit, policy.reserved)
        assertEquals(listOf(oldPermit to false), policy.finishes)
        runCurrent()
        repository.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(oldPermit to false, newPermit to true), policy.finishes)
    }

    private fun exportImages() = viewModel.exportImages(
        original.id, "1,3", PdfImageFormat.JPEG, "content://out.zip", null,
    )

    private fun prepareStrong() = viewModel.prepareCompression(
        original.id, PdfCompressionLevel.STRONG, null, "Compacto",
    )

    private class FakeAccessPolicy : ToolAdAccessPolicy {
        var available = ToolAdAccess.REWARDED
        var reserved: ToolAdPermit? = null
        val acquisitions = mutableListOf<Pair<String, Boolean>>()
        val finishes = mutableListOf<Pair<ToolAdPermit, Boolean>>()

        override fun access(tool: RewardedTool) = available

        override fun acquire(tool: RewardedTool, operationId: String, adUnavailable: Boolean): ToolAdPermit? {
            acquisitions += operationId to adUnavailable
            if (reserved != null || (available == ToolAdAccess.AD_REQUIRED && !adUnavailable)) return null
            return ToolAdPermit(tool, operationId, available).also { reserved = it }
        }

        override fun finish(permit: ToolAdPermit, success: Boolean) {
            finishes += permit to success
            if (reserved !== permit) return
            reserved = null
            if (success && permit.origin == ToolAdAccess.REWARDED) {
                available = ToolAdAccess.AD_REQUIRED
            }
        }

        override fun recordAdShown(adId: String) = Unit

        override fun grantReward(tool: RewardedTool, adId: String) {
            available = ToolAdAccess.REWARDED
        }
    }

    private class FakeUtility : PdfUtilityRepository {
        val compressed = mutableListOf<PdfCompressionLevel>()
        var result = PdfCompressionResult(ScannedDocument("file:///compressed.pdf", 5, 1, 500), 1024, 500)
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false
        var failure: Exception? = null

        override suspend fun compress(
            document: DocumentModel,
            level: PdfCompressionLevel,
            password: String?,
        ): PdfCompressionResult {
            compressed += level
            val waitFor = gate
            if (waitFor != null) {
                if (ignoreCancellation) withContext(NonCancellable) { waitFor.await() }
                else waitFor.await()
            }
            failure?.let { throw it }
            return result
        }

        val extracted = mutableListOf<List<Int>>()
        val exported = mutableListOf<List<Int>>()
        override suspend fun extractPages(document: DocumentModel, pages: List<Int>, password: String?): ScannedDocument {
            extracted += pages
            gate?.await()
            failure?.let { throw it }
            return ScannedDocument("file:///extracted.pdf", pages.size, 1, 500)
        }

        override suspend fun exportImages(
            document: DocumentModel, pages: List<Int>, format: PdfImageFormat, targetUri: String, password: String?,
        ) {
            exported += pages
            gate?.await()
            failure?.let { throw it }
        }

        override suspend fun imagesToPdf(imageUris: List<String>, idCard: Boolean): ScannedDocument =
            error("Unexpected image conversion")

        override suspend fun readReceipt(document: DocumentModel, page: Int, password: String?): ReceiptDraft =
            error("Unexpected receipt extraction")

        override suspend fun exportReceipt(receipt: ReceiptDraft, targetUri: String) = Unit
    }
}
