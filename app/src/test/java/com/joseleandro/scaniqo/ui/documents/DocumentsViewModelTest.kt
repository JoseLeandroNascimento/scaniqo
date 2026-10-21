package com.joseleandro.scaniqo.ui.documents

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
import com.joseleandro.scaniqo.ui.documentCommon.FakeTagRepository
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documents.event.DocumentsEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    private val actionsSavedStateHandle = SavedStateHandle()
    private val repository = FakeDocumentRepository()
    private val tagRepository = FakeTagRepository()

    // A coleta inicial começa dentro do teste, após configurar os valores e as falhas do fake.
    private val viewModel by lazy {
        DocumentsViewModel(repository, tagRepository, savedStateHandle, dispatcher)
            .also { viewModelStore.put("documents", it) }
    }

    private val actions by lazy {
        DocumentActionsViewModel(
            repository, tagRepository, actionsSavedStateHandle,
            defaultDocumentName = { createdAt -> "Localized document $createdAt" },
        )
            .also { viewModelStore.put("actions", it) }
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
        actions.onDocumentAction(DocumentActionEvent.EditMetadata(document))
        assertEquals(tag.id, actions.uiState.value.editor!!.tagId)

        tagRepository.tags.value = emptyList()
        runCurrent()

        assertNull(viewModel.uiState.value.selectedTagId)
        assertNull(actions.uiState.value.editor!!.tagId)
        assertEquals(2, viewModel.uiState.value.visibleDocuments.size)
    }

    @Test
    fun completedScanPreparesAPdfAndSuggestsANameAndTheCurrentTag() = runTest(dispatcher) {
        tagRepository.tags.value = listOf(tag)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        actions.onDocumentAction(DocumentActionEvent.StartScan(viewModel.uiState.value.selectedTagId))
        actions.onDocumentAction(DocumentActionEvent.ScanCompleted(listOf("content://page-1", "content://page-2")))
        assertTrue(actions.uiState.value.isPreparingScan)
        assertFalse(actions.uiState.value.isScanning)
        runCurrent()

        val editor = actions.uiState.value.editor!!
        assertFalse(actions.uiState.value.isPreparingScan)
        assertEquals(repository.scan, editor.draft)
        assertEquals("Localized document ${repository.scan.createdAt}", editor.name)
        assertEquals(tag.id, editor.tagId)
        assertNull(editor.documentId)
        assertEquals(listOf("content://page-1", "content://page-2"), repository.preparedPages.single())
    }

    @Test
    fun savingWithAnotherTagUpdatesTheFilterAndKeepsTheNewDocumentVisible() = runTest(dispatcher) {
        val anotherTag = tag.copy(id = 4, name = "Pessoal")
        tagRepository.tags.value = listOf(tag, anotherTag)
        prepareDraft()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        actions.onDocumentAction(DocumentActionEvent.TagChanged(anotherTag.id))
        actions.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()
        deliverCreation()

        assertEquals(anotherTag.id, viewModel.uiState.value.selectedTagId)
        assertEquals(anotherTag.id, viewModel.uiState.value.visibleDocuments.single().tagId)
    }

    @Test
    fun savingWithoutATagClearsTheFilterAndKeepsTheNewDocumentVisible() = runTest(dispatcher) {
        tagRepository.tags.value = listOf(tag)
        prepareDraft()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        actions.onDocumentAction(DocumentActionEvent.TagChanged(null))
        actions.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()
        deliverCreation()

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
        actions.onDocumentAction(DocumentActionEvent.EditMetadata(document))
        actions.onDocumentAction(DocumentActionEvent.TagChanged(null))
        actions.onDocumentAction(DocumentActionEvent.SaveDocument)
        runCurrent()

        assertNull(actions.uiState.value.pendingCreation)
        assertEquals(tag.id, viewModel.uiState.value.selectedTagId)
        assertTrue(viewModel.uiState.value.visibleDocuments.isEmpty())
    }

    @Test
    fun draftAndFilterAreRestoredWithoutPersistingBusyFlags() = runTest(dispatcher) {
        tagRepository.tags.value = listOf(tag)
        prepareDraft()
        actions.onDocumentAction(DocumentActionEvent.NameChanged("Recibo para revisar"))
        actions.onDocumentAction(DocumentActionEvent.TagChanged(tag.id))
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        val savedActions = actionsSavedStateHandle.keys()
            .associateWith { actionsSavedStateHandle.get<Any?>(it) }

        val restored = recreateViewModel()
        val restoredActions = DocumentActionsViewModel(
            repository,
            tagRepository,
            SavedStateHandle(savedActions),
        ).also { viewModelStore.put("restoredActions", it) }
        runCurrent()

        assertEquals(repository.scan, restoredActions.uiState.value.editor!!.draft)
        assertEquals("Recibo para revisar", restoredActions.uiState.value.editor!!.name)
        assertEquals(tag.id, restoredActions.uiState.value.editor!!.tagId)
        assertEquals(tag.id, restored.uiState.value.selectedTagId)
        assertFalse(restoredActions.uiState.value.editor!!.isSaving)
        assertTrue(repository.discarded.isEmpty())
    }

    @Test
    fun creationCompletionBeforeTagsLoadPreservesTheTagUntilValidation() = runTest(dispatcher) {
        val anotherTag = tag.copy(id = 4, name = "Pessoal")
        tagRepository.tags.value = listOf(tag, anotherTag)
        tagRepository.loadGate = CompletableDeferred()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        viewModel.onEvent(DocumentsEvent.DocumentCreated(20, anotherTag.id))
        runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(anotherTag.id, viewModel.uiState.value.selectedTagId)

        tagRepository.loadGate!!.complete(Unit)
        runCurrent()
        assertEquals(anotherTag.id, viewModel.uiState.value.selectedTagId)
    }

    @Test
    fun creationWithADeletedTagClearsTheFilterWhenTagsArrive() = runTest(dispatcher) {
        tagRepository.tags.value = listOf(tag)
        tagRepository.loadGate = CompletableDeferred()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        viewModel.onEvent(DocumentsEvent.DocumentCreated(20, 99))
        runCurrent()
        assertEquals(99L, viewModel.uiState.value.selectedTagId)

        tagRepository.loadGate!!.complete(Unit)
        runCurrent()
        assertNull(viewModel.uiState.value.selectedTagId)
    }

    @Test
    fun replayedCreationAfterRecreationPreservesTheLaterFilterSelection() = runTest(dispatcher) {
        val anotherTag = tag.copy(id = 4, name = "Pessoal")
        val laterTag = tag.copy(id = 5, name = "Estudos")
        tagRepository.tags.value = listOf(tag, anotherTag, laterTag)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        viewModel.onEvent(DocumentsEvent.SelectTag(tag.id))
        viewModel.onEvent(DocumentsEvent.DocumentCreated(20, anotherTag.id))
        assertEquals(anotherTag.id, viewModel.uiState.value.selectedTagId)
        viewModel.onEvent(DocumentsEvent.SelectTag(laterTag.id))

        val restored = recreateViewModel()
        restored.onEvent(DocumentsEvent.DocumentCreated(20, anotherTag.id))
        runCurrent()

        assertEquals(laterTag.id, restored.uiState.value.selectedTagId)
    }

    @Test
    fun creationKeepsAllDocumentsVisibleWhenThereWasNoTagFilter() = runTest(dispatcher) {
        tagRepository.tags.value = listOf(tag)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        viewModel.onEvent(DocumentsEvent.DocumentCreated(20, tag.id))

        assertNull(viewModel.uiState.value.selectedTagId)
    }

    private fun TestScope.prepareDraft() {
        actions.onDocumentAction(DocumentActionEvent.StartScan(viewModel.uiState.value.selectedTagId))
        actions.onDocumentAction(DocumentActionEvent.ScanCompleted(listOf("content://page-1")))
        runCurrent()
    }

    private fun deliverCreation() {
        val creation = checkNotNull(actions.uiState.value.pendingCreation)
        viewModel.onEvent(DocumentsEvent.DocumentCreated(creation.documentId, creation.tagId))
        actions.acknowledgeCreation(creation.documentId)
        assertNull(actions.uiState.value.pendingCreation)
    }

    private fun recreateViewModel(): DocumentsViewModel {
        val savedValues = savedStateHandle.keys().associateWith { savedStateHandle.get<Any?>(it) }
        viewModelStore.clear()
        dispatcher.scheduler.runCurrent()
        return DocumentsViewModel(repository, tagRepository, SavedStateHandle(savedValues), dispatcher)
            .also { viewModelStore.put("restored", it) }
    }
}
