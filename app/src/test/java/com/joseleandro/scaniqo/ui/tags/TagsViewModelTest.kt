package com.joseleandro.scaniqo.ui.tags

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.TAG_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private lateinit var repository: FakeTagRepository

    // Inicializa dentro de runTest, após configurar o fake, antes de avançar o agendador.
    private val viewModel: TagsViewModel by lazy {
        TagsViewModel(repository).also { viewModelStore.put("tags", it) }
    }

    private val receipt = TagModel(
        id = 7,
        name = "Comprovantes",
        color = Color(0xFF4F7CAC),
        iconKey = TagIcon.DOCUMENT.key,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeTagRepository()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun loadingEndsWhenTheRepositoryEmitsTags() = runTest(dispatcher) {
        repository.tags.value = listOf(receipt)
        assertTrue(viewModel.uiState.value.isLoading)

        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(listOf(receipt), viewModel.uiState.value.tags)

        repository.tags.value = emptyList()
        runCurrent()

        assertTrue(viewModel.uiState.value.tags.isEmpty())
    }

    @Test
    fun failedObservationCanBeRetriedWithoutDuplicateCollectors() = runTest(dispatcher) {
        repository.loadFailure = IllegalStateException("Falha de leitura")
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        assertTrue(viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)

        repository.loadFailure = null
        repository.tags.value = listOf(receipt)
        viewModel.onEvent(TagsEvent.RetryLoad)
        viewModel.onEvent(TagsEvent.RetryLoad)
        assertTrue(viewModel.uiState.value.isLoading)

        runCurrent()
        viewModel.onEvent(TagsEvent.RetryLoad)
        runCurrent()

        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(listOf(receipt), viewModel.uiState.value.tags)
        assertEquals(2, repository.observationCount)
        assertEquals(1, repository.activeObservers)
        assertEquals(1, repository.maximumActiveObservers)
    }

    @Test
    fun createTrimsTheNameAndPersistsTheSelectedAppearance() = runTest(dispatcher) {
        viewModel.onEvent(TagsEvent.CreateTag)
        viewModel.onEvent(TagsEvent.NameChanged("  Estudos  "))
        viewModel.onEvent(TagsEvent.IconChanged(TagIcon.DOCUMENT))
        viewModel.onEvent(TagsEvent.ColorChanged(Color(0xFF9575CD)))
        viewModel.onEvent(TagsEvent.SaveTag)

        assertTrue(viewModel.uiState.value.editor!!.isSaving)
        runCurrent()

        val saved = repository.created.single()
        assertEquals("Estudos", saved.name)
        assertEquals(TagIcon.DOCUMENT.key, saved.iconKey)
        assertEquals(Color(0xFF9575CD), saved.color)
        assertNull(viewModel.uiState.value.editor)
        assertEquals(TagsMessage.SAVED, viewModel.uiState.value.message)
        assertEquals("Estudos", viewModel.uiState.value.tags.single().name)
    }

    @Test
    fun editingUpdatesTheExistingTagInsteadOfCreatingAnother() = runTest(dispatcher) {
        repository.tags.value = listOf(receipt)
        viewModel.onEvent(TagsEvent.EditTag(receipt))
        assertEquals(receipt.id, viewModel.uiState.value.editor!!.id)
        assertEquals(receipt.iconKey, viewModel.uiState.value.editor!!.iconKey)
        assertEquals(receipt.color, viewModel.uiState.value.editor!!.color)

        viewModel.onEvent(TagsEvent.NameChanged("Recibos"))
        viewModel.onEvent(TagsEvent.SaveTag)
        runCurrent()

        assertEquals(receipt.copy(name = "Recibos"), repository.updated.single())
        assertTrue(repository.created.isEmpty())
        assertEquals("Recibos", viewModel.uiState.value.tags.single().name)
    }

    @Test
    fun blankNamesAreRejectedAndEditingClearsTheValidationError() = runTest(dispatcher) {
        viewModel.onEvent(TagsEvent.CreateTag)
        viewModel.onEvent(TagsEvent.NameChanged("   "))
        viewModel.onEvent(TagsEvent.SaveTag)
        runCurrent()

        assertEquals(TagNameError.REQUIRED, viewModel.uiState.value.editor!!.nameError)
        assertFalse(viewModel.uiState.value.editor!!.isSaving)
        assertTrue(repository.created.isEmpty())

        viewModel.onEvent(TagsEvent.NameChanged("Pessoal"))
        assertNull(viewModel.uiState.value.editor!!.nameError)
    }

    @Test
    fun namesAboveTheLimitAreRejectedButTheLimitIsAccepted() = runTest(dispatcher) {
        viewModel.onEvent(TagsEvent.CreateTag)
        viewModel.onEvent(TagsEvent.NameChanged("a".repeat(TAG_NAME_MAX_LENGTH + 1)))
        viewModel.onEvent(TagsEvent.SaveTag)

        assertEquals(TagNameError.TOO_LONG, viewModel.uiState.value.editor!!.nameError)
        assertTrue(repository.created.isEmpty())

        viewModel.onEvent(TagsEvent.NameChanged("a".repeat(TAG_NAME_MAX_LENGTH)))
        viewModel.onEvent(TagsEvent.SaveTag)
        runCurrent()

        assertEquals(TAG_NAME_MAX_LENGTH, repository.created.single().name.length)
    }

    @Test
    fun failedSavePreservesTheFormAndAllowsAnotherAttempt() = runTest(dispatcher) {
        repository.writeFailure = IllegalStateException("Sem espaço")
        viewModel.onEvent(TagsEvent.EditTag(receipt))
        viewModel.onEvent(TagsEvent.NameChanged("Recibos"))
        viewModel.onEvent(TagsEvent.SaveTag)
        runCurrent()

        val editor = viewModel.uiState.value.editor!!
        assertTrue(editor.saveError)
        assertFalse(editor.isSaving)
        assertEquals(receipt.id, editor.id)
        assertEquals("Recibos", editor.name)
        assertEquals(receipt.color, editor.color)
        assertEquals(receipt.iconKey, editor.iconKey)

        repository.writeFailure = null
        viewModel.onEvent(TagsEvent.SaveTag)
        runCurrent()

        assertNull(viewModel.uiState.value.editor)
        assertEquals(TagsMessage.SAVED, viewModel.uiState.value.message)
        assertEquals(2, repository.updated.size)
    }

    @Test
    fun savingBlocksDuplicateClicksAndChangesToThePendingForm() = runTest(dispatcher) {
        repository.writeGate = CompletableDeferred()
        viewModel.onEvent(TagsEvent.CreateTag)
        viewModel.onEvent(TagsEvent.NameChanged("Trabalho"))
        viewModel.onEvent(TagsEvent.SaveTag)
        viewModel.onEvent(TagsEvent.SaveTag)
        viewModel.onEvent(TagsEvent.NameChanged("Outro nome"))
        viewModel.onEvent(TagsEvent.DismissEditor)
        viewModel.onEvent(TagsEvent.RequestDelete(receipt))
        runCurrent()

        assertEquals(1, repository.created.size)
        assertEquals("Trabalho", viewModel.uiState.value.editor!!.name)
        assertTrue(viewModel.uiState.value.editor!!.isSaving)
        assertNull(viewModel.uiState.value.pendingDeletion)

        repository.writeGate!!.complete(Unit)
        runCurrent()
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun deletionRequiresConfirmationAndUpdatesTheObservedList() = runTest(dispatcher) {
        repository.tags.value = listOf(receipt)
        viewModel.onEvent(TagsEvent.RequestDelete(receipt))
        runCurrent()

        assertTrue(repository.deleted.isEmpty())
        assertEquals(receipt, viewModel.uiState.value.pendingDeletion)

        viewModel.onEvent(TagsEvent.ConfirmDelete)
        assertTrue(viewModel.uiState.value.isDeleting)
        runCurrent()

        assertEquals(listOf(receipt), repository.deleted)
        assertNull(viewModel.uiState.value.pendingDeletion)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertTrue(viewModel.uiState.value.tags.isEmpty())
        assertEquals(TagsMessage.DELETED, viewModel.uiState.value.message)
    }

    @Test
    fun failedDeletionKeepsTheConfirmationAvailableForRetry() = runTest(dispatcher) {
        repository.writeFailure = IllegalStateException("Falha ao excluir")
        viewModel.onEvent(TagsEvent.RequestDelete(receipt))
        viewModel.onEvent(TagsEvent.ConfirmDelete)
        runCurrent()

        assertEquals(receipt, viewModel.uiState.value.pendingDeletion)
        assertEquals(TagsMessage.DELETE_ERROR, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isDeleting)

        repository.writeFailure = null
        viewModel.onEvent(TagsEvent.ConfirmDelete)
        assertNull(viewModel.uiState.value.message)
        runCurrent()

        assertNull(viewModel.uiState.value.pendingDeletion)
        assertEquals(TagsMessage.DELETED, viewModel.uiState.value.message)
        assertEquals(2, repository.deleted.size)
    }

    @Test
    fun deletingBlocksDuplicateClicksAndDialogDismissal() = runTest(dispatcher) {
        repository.writeGate = CompletableDeferred()
        viewModel.onEvent(TagsEvent.RequestDelete(receipt))
        viewModel.onEvent(TagsEvent.ConfirmDelete)
        viewModel.onEvent(TagsEvent.ConfirmDelete)
        viewModel.onEvent(TagsEvent.DismissDelete)
        viewModel.onEvent(TagsEvent.CreateTag)
        runCurrent()

        assertEquals(1, repository.deleted.size)
        assertTrue(viewModel.uiState.value.isDeleting)
        assertEquals(receipt, viewModel.uiState.value.pendingDeletion)
        assertNull(viewModel.uiState.value.editor)

        repository.writeGate!!.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun dismissingDeletionClearsItsInlineError() = runTest(dispatcher) {
        repository.writeFailure = IllegalStateException("Falha ao excluir")
        viewModel.onEvent(TagsEvent.RequestDelete(receipt))
        viewModel.onEvent(TagsEvent.ConfirmDelete)
        runCurrent()

        viewModel.onEvent(TagsEvent.DismissDelete)

        assertNull(viewModel.uiState.value.pendingDeletion)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun saveCancellationDoesNotBecomeAnErrorAndReleasesTheForm() = runTest(dispatcher) {
        repository.writeFailure = CancellationException("Operação cancelada")
        viewModel.onEvent(TagsEvent.CreateTag)
        viewModel.onEvent(TagsEvent.NameChanged("Pessoal"))
        viewModel.onEvent(TagsEvent.SaveTag)
        runCurrent()

        assertFalse(viewModel.uiState.value.editor!!.saveError)
        assertFalse(viewModel.uiState.value.editor!!.isSaving)
        assertEquals("Pessoal", viewModel.uiState.value.editor!!.name)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun deleteCancellationDoesNotBecomeAnErrorAndReleasesTheDialog() = runTest(dispatcher) {
        repository.writeFailure = CancellationException("Operação cancelada")
        viewModel.onEvent(TagsEvent.RequestDelete(receipt))
        viewModel.onEvent(TagsEvent.ConfirmDelete)
        runCurrent()

        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals(receipt, viewModel.uiState.value.pendingDeletion)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun observationCancellationIsNotReportedAsALoadError() = runTest(dispatcher) {
        repository.loadFailure = CancellationException("Observação cancelada")
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()

        assertFalse(viewModel.uiState.value.loadError)
        assertEquals(0, repository.activeObservers)
    }

    @Test
    fun dismissingTheEditorAndMessageClearsOnlyTransientState() = runTest(dispatcher) {
        repository.tags.value = listOf(receipt)
        viewModel.onEvent(TagsEvent.CreateTag)
        viewModel.onEvent(TagsEvent.NameChanged("Descartar"))
        viewModel.onEvent(TagsEvent.DismissEditor)
        runCurrent()
        assertNull(viewModel.uiState.value.editor)
        assertTrue(repository.created.isEmpty())

        viewModel.onEvent(TagsEvent.EditTag(receipt))
        viewModel.onEvent(TagsEvent.SaveTag)
        runCurrent()
        viewModel.onEvent(TagsEvent.DismissMessage)

        assertNull(viewModel.uiState.value.message)
        assertEquals(listOf(receipt), viewModel.uiState.value.tags)
    }

    /** Fake controlável: simula suspensão e falhas sem depender de Room ou de um dispositivo. */
    private class FakeTagRepository : TagRepository {
        val tags = MutableStateFlow<List<TagModel>>(emptyList())
        val created = mutableListOf<TagModel>()
        val updated = mutableListOf<TagModel>()
        val deleted = mutableListOf<TagModel>()
        var loadFailure: Throwable? = null
        var writeFailure: Throwable? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var observationCount = 0
        var activeObservers = 0
        var maximumActiveObservers = 0
        private var nextId = 10L

        override fun observeTags(): Flow<List<TagModel>> = flow {
            observationCount++
            activeObservers++
            maximumActiveObservers = maxOf(maximumActiveObservers, activeObservers)
            try {
                loadFailure?.let { throw it }
                emitAll(tags)
            } finally {
                activeObservers--
            }
        }

        override suspend fun findById(id: Long): TagModel? =
            tags.value.firstOrNull { it.id == id }

        override suspend fun create(tag: TagModel): Long {
            created += tag
            awaitWrite()
            val id = nextId++
            tags.value += tag.copy(id = id)
            return id
        }

        override suspend fun update(tag: TagModel) {
            updated += tag
            awaitWrite()
            tags.value = tags.value.map { if (it.id == tag.id) tag else it }
        }

        override suspend fun delete(tag: TagModel) {
            deleted += tag
            awaitWrite()
            tags.value = tags.value.filterNot { it.id == tag.id }
        }

        private suspend fun awaitWrite() {
            writeGate?.await()
            writeFailure?.let { throw it }
        }
    }
}
