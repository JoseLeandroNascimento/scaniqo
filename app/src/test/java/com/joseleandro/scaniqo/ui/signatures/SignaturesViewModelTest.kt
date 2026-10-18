package com.joseleandro.scaniqo.ui.signatures

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.*
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignaturesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeSignatures()
    private val model by lazy { SignaturesViewModel(repository).also { store.put("signatures", it) } }

    @Before fun prepare() { Dispatchers.setMain(dispatcher) }
    @After fun finish() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun typedSignaturePreservesFontAndColorAndBlocksDuplicateSave() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.TYPE))
        model.onEvent(SignaturesEvent.TextChanged("Maria Silva"))
        model.onEvent(SignaturesEvent.FontChanged(SignatureFont.CLASSIC))
        model.onEvent(SignaturesEvent.ColorChanged(0xFF153D83.toInt()))
        repository.gate = CompletableDeferred()
        model.onEvent(SignaturesEvent.Save)
        model.onEvent(SignaturesEvent.Save)
        model.onEvent(SignaturesEvent.DismissEditor)
        model.onEvent(SignaturesEvent.Create)
        runCurrent()
        assertEquals(1, repository.calls)
        assertTrue(model.uiState.value.editor!!.isSaving)
        assertEquals("Maria Silva", repository.text)
        assertEquals(SignatureFont.CLASSIC, repository.font)
        assertEquals(0xFF153D83.toInt(), repository.color)
        repository.gate!!.complete(Unit)
        runCurrent()
        assertNull(model.uiState.value.editor)
        assertEquals(1, model.uiState.value.signatures.size)
    }

    @Test fun failedSaveKeepsDrawingAndCanRetry() = runTest(dispatcher) {
        val stroke = SignatureStroke(listOf(SignaturePoint(.1f, .2f), SignaturePoint(.5f, .7f)), 0xFF000000.toInt(), .004f)
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.StrokeAdded(stroke))
        repository.failure = IllegalStateException("Falha de armazenamento")
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals(listOf(stroke), model.uiState.value.editor!!.strokes)
        assertFalse(model.uiState.value.editor!!.isSaving)
        assertNotNull(model.uiState.value.editor!!.error)
        repository.failure = null
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertNull(model.uiState.value.editor)
        assertEquals(listOf(stroke), repository.strokes)
    }

    @Test fun cancellingImagePickerKeepsPreviousSelectionAndTransparencyChoice() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.IMAGE))
        model.onEvent(SignaturesEvent.ImageSelected("content://signature"))
        model.onEvent(SignaturesEvent.RemoveBackgroundChanged(true))
        model.onEvent(SignaturesEvent.ImageSelected(null))
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals("content://signature", repository.image)
        assertTrue(repository.removeBackground)
    }

    @Test fun missingImageDoesNotCreateAnEmptySignature() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.IMAGE))
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals(0, repository.calls)
        assertNotNull(model.uiState.value.editor!!.error)
    }

    @Test fun deletingRequiresConfirmationAndPreservesDialogOnFailure() = runTest(dispatcher) {
        val signature = SignatureModel("one", "Pessoal", "file:///signature.png", 3f)
        repository.items.value = listOf(signature)
        model.onEvent(SignaturesEvent.RequestDelete(signature))
        runCurrent()
        assertTrue(repository.deleted.isEmpty())
        repository.failure = IllegalStateException("Arquivo ocupado")
        model.onEvent(SignaturesEvent.ConfirmDelete)
        runCurrent()
        assertEquals(signature, model.uiState.value.deleteCandidate)
        assertNotNull(model.uiState.value.error)
        repository.failure = null
        model.onEvent(SignaturesEvent.ConfirmDelete)
        runCurrent()
        assertNull(model.uiState.value.deleteCandidate)
        assertTrue(model.uiState.value.signatures.isEmpty())
    }

    @Test fun undoOnlyRemovesTheLastStroke() = runTest(dispatcher) {
        val first = SignatureStroke(listOf(SignaturePoint(.1f,.1f), SignaturePoint(.2f,.2f)), 0xFF000000.toInt(), .004f)
        val second = first.copy(width = .008f)
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.StrokeAdded(first))
        model.onEvent(SignaturesEvent.StrokeAdded(second))
        model.onEvent(SignaturesEvent.Undo)
        assertEquals(listOf(first), model.uiState.value.editor!!.strokes)
        model.onEvent(SignaturesEvent.Clear)
        assertTrue(model.uiState.value.editor!!.strokes.isEmpty())
    }

    @Test fun documentCreationDeliversTheSavedSignatureOnlyToItsOriginalDocument() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.CreateForDocument(documentId = 42))
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.TYPE))
        model.onEvent(SignaturesEvent.TextChanged("Maria"))
        model.onEvent(SignaturesEvent.Save)
        runCurrent()

        assertEquals(SignatureDocumentSelection(42, "new"), model.uiState.value.pendingDocumentSelection)
        model.onEvent(SignaturesEvent.AcknowledgeDocumentSelection("another-signature"))
        assertNotNull(model.uiState.value.pendingDocumentSelection)
        model.onEvent(SignaturesEvent.AcknowledgeDocumentSelection("new"))
        assertNull(model.uiState.value.pendingDocumentSelection)
    }

    @Test fun normalLibraryCreationDoesNotAutomaticallyApplyToADocument() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.TYPE))
        model.onEvent(SignaturesEvent.TextChanged("Maria"))
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertNull(model.uiState.value.pendingDocumentSelection)
        assertEquals(1, model.uiState.value.signatures.size)
    }

    /** Permite exercitar falhas e concorrência sem depender de bitmaps Android. */
    private class FakeSignatures : SignatureRepository {
        val items = MutableStateFlow<List<SignatureModel>>(emptyList())
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        var calls = 0
        var text = ""
        var font: SignatureFont? = null
        var color = 0
        var strokes = emptyList<SignatureStroke>()
        var image = ""
        var removeBackground = false
        val deleted = mutableListOf<String>()
        override fun observeSignatures() = items
        override suspend fun createDrawing(name: String, strokes: List<SignatureStroke>): SignatureModel {
            this.strokes = strokes
            return create(name)
        }
        override suspend fun createTyped(name: String, text: String, font: SignatureFont, colorArgb: Int): SignatureModel {
            this.text = text; this.font = font; color = colorArgb
            return create(name)
        }
        override suspend fun importImage(name: String, uri: String, removeLightBackground: Boolean): SignatureModel {
            image = uri; removeBackground = removeLightBackground
            return create(name)
        }
        private suspend fun create(name: String): SignatureModel {
            calls++
            gate?.await()
            failure?.let { throw it }
            return SignatureModel("new", name, "file:///new.png", 3f).also { items.value += it }
        }
        override suspend fun rename(id: String, name: String) {
            failure?.let { throw it }
            items.value = items.value.map { if (it.id == id) it.copy(name = name) else it }
        }
        override suspend fun delete(id: String) {
            deleted += id
            failure?.let { throw it }
            items.value = items.value.filterNot { it.id == id }
        }
    }
}
