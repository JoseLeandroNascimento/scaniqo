package com.joseleandro.scaniqo.ui.documents

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.TagRepository
import com.joseleandro.scaniqo.ui.documentCommon.effect.DocumentActionEffect
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMessage
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentNameError
import com.joseleandro.scaniqo.ui.documents.event.DocumentsEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private val savedStateHandle = SavedStateHandle()
    private val repository = FakeDocumentRepository()
    private val tagRepository = FakeTagRepository()

    // A coleta inicial começa dentro do teste, após configurar os valores e as falhas do fake.
    private val viewModel by lazy {
        DocumentsViewModel(repository, tagRepository, savedStateHandle)
            .also { viewModelStore.put("documents", it) }
    }

    private val tag = TagModel(id = 3, name = "Trabalho", color = Color.Blue)
    private val document = DocumentModel(
        id = 7,
        name = "Contrato",
        pdfUri = "file:///documents/contract.pdf",
        pageCount = 2,
        createdAt = 1_750_000_000_000,
        sizeBytes = 2048,
        thumbnailUri = "file:///documents/contract.jpg",
        tagId = tag.id,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoadingEndsWithDocumentsAndTagsAndKeepsObservingChanges() = runTest(dispatcher) {
        repository.documents.value = listOf(document)
        tagRepository.tags.value = listOf(tag)
        assertTrue(viewModel.uiState.value.isLoading)

        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(listOf(document), viewModel.uiState.value.documents)
        assertEquals(listOf(tag), viewModel.uiState.value.tags)

        repository.documents.value = emptyList()
        runCurrent()
        assertTrue(viewModel.uiState.value.documents.isEmpty())
    }

    @Test
    fun failedObservationCanBeRetriedWithoutDuplicateCollectors() = runTest(dispatcher) {
        repository.loadFailure = IllegalStateException("Falha de leitura")
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        assertTrue(viewModel.uiState.value.loadError)

        repository.loadFailure = null
        repository.documents.value = listOf(document)
        viewModel.onEvent(DocumentsEvent.RetryLoad)
        viewModel.onEvent(DocumentsEvent.RetryLoad)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()

        viewModel.onEvent(DocumentsEvent.RetryLoad)
        runCurrent()
        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(listOf(document), viewModel.uiState.value.documents)
        assertEquals(2, repository.observationCount)
        assertEquals(1, repository.activeObservers)
        assertEquals(1, repository.maximumActiveObservers)
    }

    @Test
    fun deletingATagClearsTheFilterAndEditorSelection() = runTest(dispatcher) {
        repository.documents.value = listOf(document, document.copy(id = 8, tagId = null))
        tagRepository.tags.value = listOf(tag)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()

        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        assertEquals(listOf(document), viewModel.uiState.value.visibleDocuments)
        viewModel.onDocumentAction(DocumentActionEvent.EditMetadata(document))
        assertEquals(tag.id, viewModel.uiState.value.editor!!.tagId)

        tagRepository.tags.value = emptyList()
        runCurrent()

        assertNull(viewModel.uiState.value.selectedTagId)
        assertNull(viewModel.uiState.value.editor!!.tagId)
        assertEquals(2, viewModel.uiState.value.visibleDocuments.size)
    }

    @Test
    fun scannerLaunchIsConsumedOnceAndCancellationDoesNotCreateADraft() = runTest(dispatcher) {
        val effects = collectEffects()
        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        assertTrue(viewModel.uiState.value.isScanning)
        runCurrent()
        assertEquals(listOf(DocumentActionEffect.LaunchScanner), effects)

        viewModel.onDocumentAction(DocumentActionEvent.ScanCancelled)
        viewModel.onDocumentAction(DocumentActionEvent.ScanCompleted(listOf("content://stale-page")))
        runCurrent()

        assertFalse(viewModel.uiState.value.isScanning)
        assertNull(viewModel.uiState.value.editor)
        assertNull(viewModel.uiState.value.message)
        assertTrue(repository.preparedPages.isEmpty())
    }

    @Test
    fun scannerFailureReleasesTheActionForAnotherAttempt() = runTest(dispatcher) {
        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        viewModel.onDocumentAction(DocumentActionEvent.ScanFailed)

        assertFalse(viewModel.uiState.value.isScanning)
        assertEquals(DocumentMessage.SCAN_ERROR, viewModel.uiState.value.message)

        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        assertTrue(viewModel.uiState.value.isScanning)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun completedScanPreparesAPdfAndSuggestsANameAndTheCurrentTag() = runTest(dispatcher) {
        tagRepository.tags.value = listOf(tag)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        viewModel.onDocumentAction(DocumentActionEvent.ScanCompleted(listOf("content://page-1", "content://page-2")))
        assertTrue(viewModel.uiState.value.isPreparingScan)
        assertFalse(viewModel.uiState.value.isScanning)
        runCurrent()

        val editor = viewModel.uiState.value.editor!!
        assertFalse(viewModel.uiState.value.isPreparingScan)
        assertEquals(repository.scan, editor.draft)
        assertTrue(editor.name.matches(Regex("Documento \\d{2}-\\d{2}-\\d{4} \\d{2}\\.\\d{2}")))
        assertEquals(tag.id, editor.tagId)
        assertNull(editor.documentId)
        assertEquals(listOf("content://page-1", "content://page-2"), repository.preparedPages.single())
    }

    @Test
    fun duplicateScanResultsDoNotPrepareMultipleDocuments() = runTest(dispatcher) {
        repository.prepareGate = CompletableDeferred()
        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        viewModel.onDocumentAction(DocumentActionEvent.ScanCompleted(listOf("content://page-1")))
        viewModel.onDocumentAction(DocumentActionEvent.ScanCompleted(listOf("content://page-1")))
        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        runCurrent()

        assertEquals(1, repository.preparedPages.size)
        assertTrue(viewModel.uiState.value.isPreparingScan)
        repository.prepareGate!!.complete(Unit)
        runCurrent()
        assertEquals(repository.scan, viewModel.uiState.value.editor!!.draft)
    }

    @Test
    fun emptyScanResultIsRejectedBeforePreparingFiles() = runTest(dispatcher) {
        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        viewModel.onDocumentAction(DocumentActionEvent.ScanCompleted(emptyList()))
        runCurrent()

        assertTrue(repository.preparedPages.isEmpty())
        assertFalse(viewModel.uiState.value.isScanning)
        assertFalse(viewModel.uiState.value.isPreparingScan)
        assertEquals(DocumentMessage.PREPARE_ERROR, viewModel.uiState.value.message)
    }

    @Test
    fun preparationFailureLeavesNoEditorAndAllowsAnotherScan() = runTest(dispatcher) {
        repository.prepareFailure = IllegalStateException("Página ilegível")
        prepareDraft()

        assertNull(viewModel.uiState.value.editor)
        assertFalse(viewModel.uiState.value.isPreparingScan)
        assertEquals(DocumentMessage.PREPARE_ERROR, viewModel.uiState.value.message)

        repository.prepareFailure = null
        prepareDraft()
        assertEquals(repository.scan, viewModel.uiState.value.editor!!.draft)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun saveCreatesAnUntaggedDocumentWithATrimmedName() = runTest(dispatcher) {
        prepareDraft()
        viewModel.onDocumentAction(DocumentActionEvent.NameChanged("  Recibo  "))
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        assertTrue(viewModel.uiState.value.editor!!.isSaving)
        runCurrent()

        assertEquals(CreateRequest(repository.scan, "Recibo", null), repository.created.single())
        assertNull(viewModel.uiState.value.editor)
        assertEquals(DocumentMessage.SAVED, viewModel.uiState.value.message)
        assertEquals("Recibo", viewModel.uiState.value.documents.single().name)
        assertTrue(repository.discarded.isEmpty())
    }

    @Test
    fun savingWithAnotherTagUpdatesTheFilterAndKeepsTheNewDocumentVisible() = runTest(dispatcher) {
        val anotherTag = tag.copy(id = 4, name = "Pessoal")
        tagRepository.tags.value = listOf(tag, anotherTag)
        prepareDraft()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        viewModel.onDocumentAction(DocumentActionEvent.TagChanged(anotherTag.id))
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()

        assertEquals(anotherTag.id, viewModel.uiState.value.selectedTagId)
        assertEquals(anotherTag.id, viewModel.uiState.value.visibleDocuments.single().tagId)
    }

    @Test
    fun savingWithoutATagClearsTheFilterAndKeepsTheNewDocumentVisible() = runTest(dispatcher) {
        tagRepository.tags.value = listOf(tag)
        prepareDraft()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        viewModel.onDocumentAction(DocumentActionEvent.TagChanged(null))
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()

        assertNull(viewModel.uiState.value.selectedTagId)
        assertNull(viewModel.uiState.value.visibleDocuments.single().tagId)
    }

    @Test
    fun editingMetadataPreservesTheCurrentFilter() = runTest(dispatcher) {
        repository.documents.value = listOf(document)
        tagRepository.tags.value = listOf(tag)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        viewModel.onDocumentAction(DocumentActionEvent.EditMetadata(document))
        viewModel.onDocumentAction(DocumentActionEvent.TagChanged(null))
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()

        assertEquals(tag.id, viewModel.uiState.value.selectedTagId)
        assertTrue(viewModel.uiState.value.visibleDocuments.isEmpty())
    }

    @Test
    fun blankAndLongNamesAreRejectedAndTheMaximumLengthIsAccepted() = runTest(dispatcher) {
        prepareDraft()
        viewModel.onDocumentAction(DocumentActionEvent.NameChanged("   "))
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        assertEquals(DocumentNameError.REQUIRED, viewModel.uiState.value.editor!!.nameError)

        viewModel.onDocumentAction(DocumentActionEvent.NameChanged("a".repeat(DOCUMENT_NAME_MAX_LENGTH + 1)))
        assertNull(viewModel.uiState.value.editor!!.nameError)
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        assertEquals(DocumentNameError.TOO_LONG, viewModel.uiState.value.editor!!.nameError)
        assertTrue(repository.created.isEmpty())

        viewModel.onDocumentAction(DocumentActionEvent.NameChanged("a".repeat(DOCUMENT_NAME_MAX_LENGTH)))
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()
        assertEquals(DOCUMENT_NAME_MAX_LENGTH, repository.created.single().name.length)
    }

    @Test
    fun failedSavePreservesTheDraftAndCanBeRetried() = runTest(dispatcher) {
        prepareDraft()
        repository.writeFailure = IllegalStateException("Sem espaço")
        viewModel.onDocumentAction(DocumentActionEvent.NameChanged("Comprovante"))
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()

        assertEquals(repository.scan, viewModel.uiState.value.editor!!.draft)
        assertEquals("Comprovante", viewModel.uiState.value.editor!!.name)
        assertTrue(viewModel.uiState.value.editor!!.saveError)
        assertFalse(viewModel.uiState.value.editor!!.isSaving)
        assertTrue(repository.discarded.isEmpty())

        repository.writeFailure = null
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()
        assertNull(viewModel.uiState.value.editor)
        assertEquals(2, repository.created.size)
        assertEquals(1, viewModel.uiState.value.documents.size)
    }

    @Test
    fun savingBlocksDuplicateClicksAndDiscardingTheDraft() = runTest(dispatcher) {
        prepareDraft()
        repository.writeGate = CompletableDeferred()
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        viewModel.onDocumentAction(DocumentActionEvent.DismissEditor)
        viewModel.onDocumentAction(DocumentActionEvent.NameChanged("Mudança indevida"))
        runCurrent()

        assertEquals(1, repository.created.size)
        assertTrue(repository.discarded.isEmpty())
        assertTrue(viewModel.uiState.value.editor!!.name.startsWith("Documento "))

        repository.writeGate!!.complete(Unit)
        runCurrent()
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun editingMetadataUpdatesTheExistingIdAndCanRemoveItsTag() = runTest(dispatcher) {
        repository.documents.value = listOf(document)
        tagRepository.tags.value = listOf(tag)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        viewModel.onDocumentAction(DocumentActionEvent.EditMetadata(document))
        viewModel.onDocumentAction(DocumentActionEvent.NameChanged("Contrato revisado"))
        viewModel.onDocumentAction(DocumentActionEvent.TagChanged(null))
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()

        assertEquals(UpdateRequest(document.id, "Contrato revisado", null), repository.updated.single())
        assertTrue(repository.created.isEmpty())
        assertEquals(DocumentMessage.UPDATED, viewModel.uiState.value.message)
        assertEquals(document.pdfUri, viewModel.uiState.value.documents.single().pdfUri)
        assertNull(viewModel.uiState.value.documents.single().tagId)
    }

    @Test
    fun failedDraftCleanupPreservesTheEditorUntilCleanupCanBeRetried() = runTest(dispatcher) {
        prepareDraft()
        repository.discardFailure = IllegalStateException("Arquivo temporariamente ocupado")
        viewModel.onDocumentAction(DocumentActionEvent.DismissEditor)
        runCurrent()

        assertEquals(repository.scan, viewModel.uiState.value.editor!!.draft)
        assertTrue(viewModel.uiState.value.editor!!.saveError)
        assertFalse(viewModel.uiState.value.editor!!.isSaving)

        repository.discardFailure = null
        viewModel.onDocumentAction(DocumentActionEvent.DismissEditor)
        runCurrent()
        assertNull(viewModel.uiState.value.editor)
        assertEquals(listOf(repository.scan, repository.scan), repository.discarded)
    }

    @Test
    fun dismissingMetadataDoesNotDeleteTheSavedPdf() = runTest(dispatcher) {
        viewModel.onDocumentAction(DocumentActionEvent.EditMetadata(document))
        viewModel.onDocumentAction(DocumentActionEvent.NameChanged("Descartar alterações"))
        viewModel.onDocumentAction(DocumentActionEvent.DismissEditor)
        runCurrent()

        assertNull(viewModel.uiState.value.editor)
        assertTrue(repository.discarded.isEmpty())
        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun deletionNeedsConfirmationAndBlocksDuplicateClicks() = runTest(dispatcher) {
        repository.documents.value = listOf(document)
        repository.writeGate = CompletableDeferred()
        viewModel.onDocumentAction(DocumentActionEvent.RequestDelete(document))
        runCurrent()
        assertTrue(repository.deleted.isEmpty())

        viewModel.onDocumentAction(DocumentActionEvent.ConfirmDelete)
        viewModel.onDocumentAction(DocumentActionEvent.ConfirmDelete)
        viewModel.onDocumentAction(DocumentActionEvent.DismissDelete)
        runCurrent()
        assertTrue(viewModel.uiState.value.isDeleting)
        assertEquals(document, viewModel.uiState.value.pendingDeletion)
        assertEquals(listOf(document), repository.deleted)

        repository.writeGate!!.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isDeleting)
        assertNull(viewModel.uiState.value.pendingDeletion)
        assertTrue(viewModel.uiState.value.documents.isEmpty())
        assertEquals(DocumentMessage.DELETED, viewModel.uiState.value.message)
    }

    @Test
    fun failedDeletionKeepsConfirmationAndCanBeRetried() = runTest(dispatcher) {
        repository.writeFailure = IllegalStateException("Falha ao excluir")
        viewModel.onDocumentAction(DocumentActionEvent.RequestDelete(document))
        viewModel.onDocumentAction(DocumentActionEvent.ConfirmDelete)
        runCurrent()
        assertEquals(document, viewModel.uiState.value.pendingDeletion)
        assertEquals(DocumentMessage.DELETE_ERROR, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isDeleting)

        repository.writeFailure = null
        viewModel.onDocumentAction(DocumentActionEvent.ConfirmDelete)
        assertNull(viewModel.uiState.value.message)
        runCurrent()
        assertNull(viewModel.uiState.value.pendingDeletion)
        assertEquals(DocumentMessage.DELETED, viewModel.uiState.value.message)
    }

    @Test
    fun cancellingTheDownloadPickerConsumesTheRequestWithoutExporting() = runTest(dispatcher) {
        val effects = collectEffects()
        viewModel.onDocumentAction(DocumentActionEvent.Download(document))
        viewModel.onDocumentAction(DocumentActionEvent.Download(document))
        runCurrent()
        assertEquals(
            listOf(DocumentActionEffect.ChooseDownloadLocation("Contrato.pdf")),
            effects,
        )
        assertEquals(document.id, viewModel.uiState.value.busyDocumentId)

        viewModel.onDocumentAction(DocumentActionEvent.ExportDestinationSelected(null))
        viewModel.onDocumentAction(DocumentActionEvent.ExportDestinationSelected("content://stale"))
        runCurrent()

        assertNull(viewModel.uiState.value.busyDocumentId)
        assertNull(viewModel.uiState.value.message)
        assertTrue(repository.exported.isEmpty())
    }

    @Test
    fun exportUsesTheChosenUriAndTheLatestDocumentMetadata() = runTest(dispatcher) {
        val latestDocument = document.copy(name = "Contrato atualizado")
        repository.documents.value = listOf(latestDocument)
        viewModel.onDocumentAction(DocumentActionEvent.Download(document))
        viewModel.onDocumentAction(DocumentActionEvent.ExportDestinationSelected("content://downloads/contract"))
        viewModel.onDocumentAction(DocumentActionEvent.ExportDestinationSelected("content://duplicate"))
        runCurrent()

        assertEquals(
            listOf(latestDocument to "content://downloads/contract"),
            repository.exported,
        )
        assertNull(viewModel.uiState.value.busyDocumentId)
        assertEquals(DocumentMessage.DOWNLOADED, viewModel.uiState.value.message)
    }

    @Test
    fun failedExportReleasesTheDocumentAndCanBeRequestedAgain() = runTest(dispatcher) {
        repository.documents.value = listOf(document)
        repository.exportFailure = IllegalStateException("Destino inacessível")
        viewModel.onDocumentAction(DocumentActionEvent.Download(document))
        viewModel.onDocumentAction(DocumentActionEvent.ExportDestinationSelected("content://downloads/contract"))
        runCurrent()
        assertNull(viewModel.uiState.value.busyDocumentId)
        assertEquals(DocumentMessage.EXPORT_ERROR, viewModel.uiState.value.message)

        repository.exportFailure = null
        viewModel.onDocumentAction(DocumentActionEvent.Download(document))
        viewModel.onDocumentAction(DocumentActionEvent.ExportDestinationSelected("content://downloads/retry"))
        runCurrent()
        assertEquals(DocumentMessage.DOWNLOADED, viewModel.uiState.value.message)
        assertEquals(2, repository.exported.size)
    }

    @Test
    fun pickerLaunchFailureClearsThePendingDownload() = runTest(dispatcher) {
        viewModel.onDocumentAction(DocumentActionEvent.Download(document))
        viewModel.onDocumentAction(DocumentActionEvent.ExportFailed)
        viewModel.onDocumentAction(DocumentActionEvent.ExportDestinationSelected("content://stale"))
        runCurrent()

        assertNull(viewModel.uiState.value.busyDocumentId)
        assertEquals(DocumentMessage.EXPORT_ERROR, viewModel.uiState.value.message)
        assertTrue(repository.exported.isEmpty())
    }

    @Test
    fun sharingPreparesAContentUriAndEmitsASingleEffect() = runTest(dispatcher) {
        val effects = collectEffects()
        repository.writeGate = CompletableDeferred()
        viewModel.onDocumentAction(DocumentActionEvent.Share(document))
        viewModel.onDocumentAction(DocumentActionEvent.Share(document))
        runCurrent()
        assertEquals(document.id, viewModel.uiState.value.busyDocumentId)
        assertEquals(listOf(document), repository.shared)

        repository.writeGate!!.complete(Unit)
        runCurrent()
        assertNull(viewModel.uiState.value.busyDocumentId)
        assertEquals(
            listOf(DocumentActionEffect.SharePdf("content://shared/7.pdf", "Contrato.pdf")),
            effects,
        )
    }

    @Test
    fun sharePreparationAndLauncherFailuresAreReported() = runTest(dispatcher) {
        repository.shareFailure = IllegalStateException("Arquivo ausente")
        viewModel.onDocumentAction(DocumentActionEvent.Share(document))
        runCurrent()
        assertNull(viewModel.uiState.value.busyDocumentId)
        assertEquals(DocumentMessage.SHARE_ERROR, viewModel.uiState.value.message)

        viewModel.onDocumentAction(DocumentActionEvent.DismissMessage)
        assertNull(viewModel.uiState.value.message)
        viewModel.onDocumentAction(DocumentActionEvent.ShareFailed)
        assertEquals(DocumentMessage.SHARE_ERROR, viewModel.uiState.value.message)
    }

    @Test
    fun cancelledPreparationAndSaveAreNotReportedAsFailures() = runTest(dispatcher) {
        repository.prepareFailure = CancellationException("Preparação cancelada")
        prepareDraft()
        assertFalse(viewModel.uiState.value.isPreparingScan)
        assertNull(viewModel.uiState.value.editor)
        assertNull(viewModel.uiState.value.message)

        repository.prepareFailure = null
        prepareDraft()
        repository.writeFailure = CancellationException("Salvamento cancelado")
        viewModel.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()
        assertEquals(repository.scan, viewModel.uiState.value.editor!!.draft)
        assertFalse(viewModel.uiState.value.editor!!.isSaving)
        assertFalse(viewModel.uiState.value.editor!!.saveError)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun cancelledExportAndDeletionReleaseBusyStateWithoutError() = runTest(dispatcher) {
        repository.documents.value = listOf(document)
        repository.exportFailure = CancellationException("Exportação cancelada")
        viewModel.onDocumentAction(DocumentActionEvent.Download(document))
        viewModel.onDocumentAction(DocumentActionEvent.ExportDestinationSelected("content://downloads/contract"))
        runCurrent()
        assertNull(viewModel.uiState.value.busyDocumentId)
        assertNull(viewModel.uiState.value.message)

        repository.writeFailure = CancellationException("Exclusão cancelada")
        viewModel.onDocumentAction(DocumentActionEvent.RequestDelete(document))
        viewModel.onDocumentAction(DocumentActionEvent.ConfirmDelete)
        runCurrent()
        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals(document, viewModel.uiState.value.pendingDeletion)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun draftAndFilterAreRestoredWithoutPersistingBusyFlags() = runTest(dispatcher) {
        tagRepository.tags.value = listOf(tag)
        prepareDraft()
        viewModel.onDocumentAction(DocumentActionEvent.NameChanged("Recibo para revisar"))
        viewModel.onDocumentAction(DocumentActionEvent.TagChanged(tag.id))
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))

        val restored = recreateViewModel()
        runCurrent()

        assertEquals(repository.scan, restored.uiState.value.editor!!.draft)
        assertEquals("Recibo para revisar", restored.uiState.value.editor!!.name)
        assertEquals(tag.id, restored.uiState.value.editor!!.tagId)
        assertEquals(tag.id, restored.uiState.value.selectedTagId)
        assertFalse(restored.uiState.value.editor!!.isSaving)
        assertTrue(repository.discarded.isEmpty())
    }

    @Test
    fun pendingDownloadSurvivesRecreationAndCanComplete() = runTest(dispatcher) {
        repository.documents.value = listOf(document)
        viewModel.onDocumentAction(DocumentActionEvent.Download(document))
        val restored = recreateViewModel()
        assertEquals(document.id, restored.uiState.value.busyDocumentId)

        restored.onDocumentAction(DocumentActionEvent.ExportDestinationSelected("content://downloads/restored"))
        runCurrent()

        assertEquals(listOf(document to "content://downloads/restored"), repository.exported)
        assertNull(restored.uiState.value.busyDocumentId)
        assertEquals(DocumentMessage.DOWNLOADED, restored.uiState.value.message)
    }

    @Test
    fun scannerResultCanReturnAfterRecreation() = runTest(dispatcher) {
        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        val restored = recreateViewModel()
        assertTrue(restored.uiState.value.isScanning)

        restored.onDocumentAction(DocumentActionEvent.ScanCompleted(listOf("content://restored-page")))
        runCurrent()

        assertFalse(restored.uiState.value.isScanning)
        assertEquals(repository.scan, restored.uiState.value.editor!!.draft)
    }

    @Test
    fun importedProtectedPdfKeepsItsNameAndPasswordVersionAfterRecreation() = runTest(dispatcher) {
        val imported = repository.scan.copy(protectionKeyId = "imported-password")
        viewModel.onDocumentAction(DocumentActionEvent.ImportStarted)
        assertTrue(viewModel.uiState.value.isPreparingScan)
        viewModel.onDocumentAction(DocumentActionEvent.ImportPrepared(imported, "Contrato importado"))
        assertFalse(viewModel.uiState.value.isPreparingScan)
        val restored = recreateViewModel()
        runCurrent()
        assertEquals(imported, restored.uiState.value.editor!!.draft)
        assertEquals("Contrato importado", restored.uiState.value.editor!!.name)
    }

    @Test
    fun cancelledImportReleasesProgressWithoutCreatingADocument() = runTest(dispatcher) {
        viewModel.onDocumentAction(DocumentActionEvent.ImportStarted)
        viewModel.onDocumentAction(DocumentActionEvent.ImportCancelled)
        runCurrent()
        assertFalse(viewModel.uiState.value.isPreparingScan)
        assertNull(viewModel.uiState.value.editor)
        assertTrue(repository.created.isEmpty())
    }

    private fun TestScope.prepareDraft() {
        viewModel.onDocumentAction(DocumentActionEvent.StartScan)
        viewModel.onDocumentAction(DocumentActionEvent.ScanCompleted(listOf("content://page-1")))
        runCurrent()
    }

    private fun TestScope.collectEffects(): List<DocumentActionEffect> {
        val captured = mutableListOf<DocumentActionEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { captured += it }
        }
        return captured
    }

    private fun recreateViewModel(): DocumentsViewModel {
        val savedValues = savedStateHandle.keys().associateWith { savedStateHandle.get<Any?>(it) }
        viewModelStore.clear()
        dispatcher.scheduler.runCurrent()
        return DocumentsViewModel(repository, tagRepository, SavedStateHandle(savedValues))
            .also { viewModelStore.put("restored", it) }
    }

    private data class CreateRequest(val draft: ScannedDocument, val name: String, val tagId: Long?)
    private data class UpdateRequest(val id: Long, val name: String, val tagId: Long?)

    /** Simula operações e falhas sem abrir arquivos, banco de dados ou contratos Android. */
    private class FakeDocumentRepository : DocumentRepository {
        val documents = MutableStateFlow<List<DocumentModel>>(emptyList())
        val scan = ScannedDocument(
            pdfUri = "file:///documents/draft.pdf",
            pageCount = 2,
            createdAt = 1_750_000_000_000,
            sizeBytes = 2048,
            thumbnailUri = "file:///documents/draft.jpg",
        )
        val preparedPages = mutableListOf<List<String>>()
        val discarded = mutableListOf<ScannedDocument>()
        val created = mutableListOf<CreateRequest>()
        val updated = mutableListOf<UpdateRequest>()
        val deleted = mutableListOf<DocumentModel>()
        val exported = mutableListOf<Pair<DocumentModel, String>>()
        val shared = mutableListOf<DocumentModel>()
        var loadFailure: Throwable? = null
        var prepareFailure: Throwable? = null
        var writeFailure: Throwable? = null
        var discardFailure: Throwable? = null
        var exportFailure: Throwable? = null
        var shareFailure: Throwable? = null
        var prepareGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var observationCount = 0
        var activeObservers = 0
        var maximumActiveObservers = 0
        private var nextId = 20L

        override fun observeDocuments(): Flow<List<DocumentModel>> = flow {
            observationCount++
            activeObservers++
            maximumActiveObservers = maxOf(maximumActiveObservers, activeObservers)
            try {
                loadFailure?.let { throw it }
                emitAll(documents)
            } finally {
                activeObservers--
            }
        }

        override suspend fun findById(id: Long): DocumentModel? =
            documents.value.firstOrNull { it.id == id }

        override suspend fun prepareScan(pageUris: List<String>): ScannedDocument {
            preparedPages += pageUris
            prepareGate?.await()
            prepareFailure?.let { throw it }
            return scan
        }

        override suspend fun discardScan(scan: ScannedDocument) {
            discarded += scan
            discardFailure?.let { throw it }
        }

        override suspend fun create(scan: ScannedDocument, name: String, tagId: Long?): Long {
            created += CreateRequest(scan, name, tagId)
            awaitWrite()
            val id = nextId++
            documents.value += DocumentModel(
                id = id,
                name = name,
                pdfUri = scan.pdfUri,
                pageCount = scan.pageCount,
                createdAt = scan.createdAt,
                sizeBytes = scan.sizeBytes,
                thumbnailUri = scan.thumbnailUri,
                tagId = tagId,
            )
            return id
        }

        override suspend fun update(id: Long, name: String, tagId: Long?) {
            updated += UpdateRequest(id, name, tagId)
            awaitWrite()
            documents.value = documents.value.map {
                if (it.id == id) it.copy(name = name, tagId = tagId) else it
            }
        }

        override suspend fun replaceContent(
            id: Long,
            scan: ScannedDocument,
            allowProtectionRemoval: Boolean,
        ): DocumentModel = error("A edição de páginas pertence ao editor de PDF")

        override suspend fun delete(document: DocumentModel) {
            deleted += document
            awaitWrite()
            documents.value = documents.value.filterNot { it.id == document.id }
        }

        override suspend fun export(document: DocumentModel, targetUri: String) {
            exported += document to targetUri
            writeGate?.await()
            exportFailure?.let { throw it }
        }

        override suspend fun prepareShare(document: DocumentModel): String {
            shared += document
            writeGate?.await()
            shareFailure?.let { throw it }
            return "content://shared/" + document.id + ".pdf"
        }

        private suspend fun awaitWrite() {
            writeGate?.await()
            writeFailure?.let { throw it }
        }
    }

    private class FakeTagRepository : TagRepository {
        val tags = MutableStateFlow<List<TagModel>>(emptyList())

        override fun observeTags(): Flow<List<TagModel>> = tags

        override suspend fun findById(id: Long): TagModel? =
            tags.value.firstOrNull { it.id == id }

        override suspend fun create(tag: TagModel): Long = error("Fora do escopo desta tela")

        override suspend fun update(tag: TagModel) = error("Fora do escopo desta tela")

        override suspend fun delete(tag: TagModel) = error("Fora do escopo desta tela")
    }
}
