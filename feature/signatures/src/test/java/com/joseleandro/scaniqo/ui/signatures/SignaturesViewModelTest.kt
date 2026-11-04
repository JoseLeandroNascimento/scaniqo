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
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        model.onEvent(SignaturesEvent.RequestSave)
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
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.DRAW))
        model.onEvent(SignaturesEvent.StrokeAdded(stroke))
        repository.failure = IllegalStateException("Falha de armazenamento")
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals(listOf(stroke), model.uiState.value.editor!!.strokes)
        assertFalse(model.uiState.value.editor!!.isSaving)
        assertNotNull(model.uiState.value.editor!!.error)
        repository.failure = null
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertNull(model.uiState.value.editor)
        assertEquals(listOf(stroke), repository.strokes)
    }

    @Test fun importRequiresCropThenPreviewAndSavesTheExactProcessedImage() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.ImageSelected("content://signature"))
        runCurrent()
        assertEquals(SignatureImageStep.CROP, model.uiState.value.editor!!.imageStep)
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals(0, repository.calls)
        val crop = PdfSignatureCrop(.1f, .2f, .9f, .8f)
        model.onEvent(SignaturesEvent.CropChanged(crop))
        model.onEvent(SignaturesEvent.ImageSelected(null))
        model.onEvent(SignaturesEvent.ProcessImage)
        runCurrent()
        assertEquals(crop, repository.crop)
        assertEquals(SignatureImageStep.PREVIEW, model.uiState.value.editor!!.imageStep)
        assertEquals(0, repository.calls)
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals("processed:staged:content://signature", repository.image)
        assertTrue(repository.discarded.contains("staged:content://signature"))
        assertTrue(repository.discarded.contains(repository.image))
        assertNull(model.uiState.value.editor)
    }

    @Test fun cameraProcessesFrameImmediatelyAndNeverShowsManualCrop() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.SCAN))
        assertEquals(SignatureImageStep.CAMERA, model.uiState.value.editor!!.imageStep)
        val frame = PdfSignatureCrop(.15f, .3f, .85f, .6f)
        repository.processGate = CompletableDeferred()
        model.onEvent(SignaturesEvent.PhotoCaptured("file:///capture.jpg", frame))
        assertEquals(SignatureImageStep.PREVIEW, model.uiState.value.editor!!.imageStep)
        assertTrue(model.uiState.value.editor!!.isProcessing)
        runCurrent()
        assertEquals(frame, repository.crop)
        assertFalse(model.uiState.value.editor!!.canSave)
        repository.processGate!!.complete(Unit)
        runCurrent()
        assertTrue(model.uiState.value.editor!!.fromCamera)
        assertTrue(model.uiState.value.editor!!.canSave)
        model.onEvent(SignaturesEvent.AdjustCrop)
        assertEquals(SignatureImageStep.PREVIEW, model.uiState.value.editor!!.imageStep)
        model.onEvent(SignaturesEvent.OpenCamera)
        model.onEvent(SignaturesEvent.EditorBack)
        assertEquals(SignatureImageStep.PREVIEW, model.uiState.value.editor!!.imageStep)
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals("processed:staged:file:///capture.jpg", repository.image)
    }

    @Test fun poorCameraResultRequiresRetakeAndNeverOffersSaveOrCrop() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.SCAN))
        repository.processFailure = IllegalArgumentException("Refaça a foto")
        model.onEvent(SignaturesEvent.PhotoCaptured("file:///bad.jpg"))
        runCurrent()
        val editor = model.uiState.value.editor!!
        assertEquals(SignatureImageStep.PREVIEW, editor.imageStep)
        assertFalse(editor.isProcessing)
        assertFalse(editor.canSave)
        assertNotNull(editor.error)
        assertTrue(repository.discarded.contains("file:///bad.jpg"))
        assertTrue(repository.discarded.contains("staged:file:///bad.jpg"))
        model.onEvent(SignaturesEvent.RequestSave)
        assertFalse(model.uiState.value.editor!!.showSaveDialog)
        model.onEvent(SignaturesEvent.OpenCamera)
        assertEquals(SignatureImageStep.CAMERA, model.uiState.value.editor!!.imageStep)
        repository.processFailure = null
        model.onEvent(SignaturesEvent.PhotoCaptured("file:///good.jpg"))
        runCurrent()
        assertTrue(model.uiState.value.editor!!.canSave)
    }

    @Test fun nameIsRequestedOnlyForValidContentAndEmptyNameNeverSaves() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        assertFalse(model.uiState.value.editor!!.showSaveDialog)
        assertFalse(model.uiState.value.editor!!.canSave)
        model.onEvent(SignaturesEvent.RequestSave)
        assertFalse(model.uiState.value.editor!!.showSaveDialog)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.TYPE))
        model.onEvent(SignaturesEvent.TextChanged("Maria"))
        assertFalse(model.uiState.value.editor!!.showSaveDialog)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals(0, repository.calls)
        model.onEvent(SignaturesEvent.RequestSave)
        assertTrue(model.uiState.value.editor!!.showSaveDialog)
        model.onEvent(SignaturesEvent.NameChanged("   "))
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals(0, repository.calls)
        assertTrue(model.uiState.value.editor!!.showSaveDialog)
        model.onEvent(SignaturesEvent.CancelSave)
        assertFalse(model.uiState.value.editor!!.showSaveDialog)
        assertEquals("Maria", model.uiState.value.editor!!.text)
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.NameChanged("Pessoal"))
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals("Pessoal", model.uiState.value.signatures.single().name)
    }

    @Test fun switchingImageMethodsCannotSaveTheOtherMethodsDraft() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.ImageSelected("content://gallery"))
        runCurrent()
        model.onEvent(SignaturesEvent.ProcessImage)
        runCurrent()
        assertTrue(model.uiState.value.editor!!.canSave)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.SCAN))
        runCurrent()
        assertEquals(SignatureImageStep.CAMERA, model.uiState.value.editor!!.imageStep)
        assertNull(model.uiState.value.editor!!.processedImageUri)
        assertTrue(repository.discarded.contains("processed:staged:content://gallery"))
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.IMAGE))
        assertEquals(SignatureImageStep.SELECT, model.uiState.value.editor!!.imageStep)
        model.onEvent(SignaturesEvent.PhotoCaptured("file:///late-camera.jpg"))
        runCurrent()
        assertEquals(SignatureInput.IMAGE, model.uiState.value.editor!!.input)
        assertTrue(repository.discarded.contains("file:///late-camera.jpg"))
    }

    @Test fun colorAndTipSelectionsAffectFutureStrokesAndTypedTextWithoutRecoloringDrawing() = runTest(dispatcher) {
        val original = SignatureStroke(listOf(SignaturePoint(.1f, .2f), SignaturePoint(.5f, .7f)), 0xFF18212B.toInt(), .004f)
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.StrokeAdded(original))
        model.onEvent(SignaturesEvent.ColorChanged(0xFF183C9A.toInt()))
        model.onEvent(SignaturesEvent.WidthChanged(.010f))
        assertEquals(listOf(original), model.uiState.value.editor!!.strokes)
        assertEquals(.010f, model.uiState.value.editor!!.strokeWidth)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.TYPE))
        assertEquals(0xFF183C9A.toInt(), model.uiState.value.editor!!.colorArgb)
        model.onEvent(SignaturesEvent.EditorBack)
        assertNull(model.uiState.value.editor)
    }

    @Test fun failedProcessingBlocksSaveAndCanRetryWithoutLosingCrop() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.ImageSelected("content://signature"))
        runCurrent()
        val crop = PdfSignatureCrop(.15f, .2f, .85f, .8f)
        model.onEvent(SignaturesEvent.CropChanged(crop))
        repository.processFailure = IllegalStateException("Sem traços")
        model.onEvent(SignaturesEvent.ProcessImage)
        runCurrent()
        assertNotNull(model.uiState.value.editor!!.error)
        assertEquals(crop, model.uiState.value.editor!!.crop)
        assertNull(model.uiState.value.editor!!.processedImageUri)
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertEquals(0, repository.calls)
        repository.processFailure = null
        model.onEvent(SignaturesEvent.ProcessImage)
        runCurrent()
        assertEquals(SignatureImageStep.PREVIEW, model.uiState.value.editor!!.imageStep)
    }

    @Test fun changingCropInvalidatesPreviewAndPreventsStaleSave() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.ImageSelected("content://signature"))
        runCurrent()
        model.onEvent(SignaturesEvent.ProcessImage)
        runCurrent()
        model.onEvent(SignaturesEvent.AdjustCrop)
        model.onEvent(SignaturesEvent.CropChanged(PdfSignatureCrop(.1f, .1f, .9f, .9f)))
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertNull(model.uiState.value.editor!!.processedImageUri)
        assertEquals(0, repository.calls)
        assertTrue(repository.discarded.any { it.startsWith("processed:") })
    }

    @Test fun cancellingProcessingCannotReopenDismissedEditor() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.ImageSelected("content://signature"))
        runCurrent()
        repository.processGate = CompletableDeferred()
        model.onEvent(SignaturesEvent.ProcessImage)
        runCurrent()
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        assertTrue(model.uiState.value.editor!!.isProcessing)
        model.onEvent(SignaturesEvent.DismissEditor)
        repository.processGate!!.complete(Unit)
        runCurrent()
        assertNull(model.uiState.value.editor)
        assertEquals(0, repository.calls)
        assertTrue(repository.discarded.contains("staged:content://signature"))
    }

    @Test fun staleCropEventsCannotDiscardImageDuringSave() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.ImageSelected("content://signature"))
        runCurrent()
        model.onEvent(SignaturesEvent.ProcessImage)
        runCurrent()
        val preview = model.uiState.value.editor!!.processedImageUri!!
        repository.gate = CompletableDeferred()
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        model.onEvent(SignaturesEvent.CropChanged(PdfSignatureCrop(.1f, .1f, .9f, .9f)))
        runCurrent()
        assertFalse(repository.discarded.contains(preview))
        assertEquals(preview, model.uiState.value.editor!!.processedImageUri)
        repository.gate!!.complete(Unit)
        runCurrent()
        assertNull(model.uiState.value.editor)
    }

    @Test fun cameraCallbacksAfterDismissalAndBeforeStagingAreCleanedUp() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.PhotoCaptured("file:///capture-before-stage.jpg"))
        model.onEvent(SignaturesEvent.DismissEditor)
        runCurrent()
        model.onEvent(SignaturesEvent.PhotoCaptured("file:///late-capture.jpg"))
        runCurrent()
        assertTrue(repository.discarded.contains("file:///capture-before-stage.jpg"))
        assertTrue(repository.discarded.contains("file:///late-capture.jpg"))
        assertNull(model.uiState.value.editor)
    }

    @Test fun missingImageDoesNotCreateAnEmptySignature() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.Create)
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.IMAGE))
        model.onEvent(SignaturesEvent.RequestSave)
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
        model.onEvent(SignaturesEvent.RequestSave)
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
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertNull(model.uiState.value.pendingDocumentSelection)
        assertEquals(1, model.uiState.value.signatures.size)
    }

    @Test fun editingTypedSignatureKeepsItsIdAndPrefillsEditableSource() = runTest(dispatcher) {
        val signature = SignatureModel(
            "one", "Pessoal", "file:///one.png", 3f,
            source = SignatureSource(
                type = SignatureSourceType.TYPE,
                text = "Maria", font = SignatureFont.CLASSIC, colorArgb = 0xFF183C9A.toInt(),
            ),
        )
        repository.items.value = listOf(signature)
        model.uiState
        runCurrent()

        model.onEvent(SignaturesEvent.Edit("one"))
        val editor = model.uiState.value.editor!!
        assertEquals("one", editor.editingId)
        assertEquals("Pessoal", editor.name)
        assertEquals(SignatureInput.TYPE, editor.input)
        assertEquals("Maria", editor.text)
        assertEquals(SignatureFont.CLASSIC, editor.font)
        assertEquals(0xFF183C9A.toInt(), editor.colorArgb)

        model.onEvent(SignaturesEvent.TextChanged("Maria Silva"))
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()

        assertNull(model.uiState.value.editor)
        assertEquals(1, model.uiState.value.signatures.size)
        assertEquals("one", model.uiState.value.signatures.single().id)
        assertEquals(1, model.uiState.value.signatures.single().imageVersion)
        assertEquals("Maria Silva", repository.text)
        assertEquals(1, repository.updated)
        assertNull(model.uiState.value.pendingDocumentSelection)
    }

    @Test fun legacyPngOpensInImageModeAndKeepsEditorOnFailedUpdate() = runTest(dispatcher) {
        val signature = SignatureModel("legacy", "Antiga", "file:///legacy.png", 3f)
        repository.items.value = listOf(signature)
        model.uiState
        runCurrent()

        model.onEvent(SignaturesEvent.Edit("legacy"))
        assertEquals(SignatureInput.IMAGE, model.uiState.value.editor!!.input)
        runCurrent()
        assertEquals("staged:file:///legacy.png", model.uiState.value.editor!!.imageUri)
        model.onEvent(SignaturesEvent.ProcessImage)
        runCurrent()
        repository.failure = IllegalStateException("Falha de armazenamento")
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertNotNull(model.uiState.value.editor)
        assertEquals("legacy", model.uiState.value.editor!!.editingId)
        assertFalse(model.uiState.value.editor!!.isSaving)
        assertNotNull(model.uiState.value.editor!!.error)

        repository.failure = null
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        assertNull(model.uiState.value.editor)
        assertEquals(1, model.uiState.value.signatures.size)
        assertEquals("legacy", model.uiState.value.signatures.single().id)
        assertEquals("processed:staged:file:///legacy.png", repository.image)
        assertEquals(1, repository.updated)
    }

    @Test fun editingDoesNotConsumePendingDocumentSelection() = runTest(dispatcher) {
        model.onEvent(SignaturesEvent.CreateForDocument(42))
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.TYPE))
        model.onEvent(SignaturesEvent.TextChanged("Ana"))
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()
        val pending = model.uiState.value.pendingDocumentSelection
        assertEquals(SignatureDocumentSelection(42, "new"), pending)

        model.onEvent(SignaturesEvent.Edit("new"))
        model.onEvent(SignaturesEvent.InputChanged(SignatureInput.TYPE))
        model.onEvent(SignaturesEvent.TextChanged("Ana Silva"))
        model.onEvent(SignaturesEvent.RequestSave)
        model.onEvent(SignaturesEvent.Save)
        runCurrent()

        assertEquals(pending, model.uiState.value.pendingDocumentSelection)
        assertEquals(1, model.uiState.value.signatures.size)
    }

    /** Permite exercitar falhas e concorrência sem depender de bitmaps Android. */
    private class FakeSignatures : SignatureRepository {
        val items = MutableStateFlow<List<SignatureModel>>(emptyList())
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        var calls = 0
        var updated = 0
        var text = ""
        var font: SignatureFont? = null
        var color = 0
        var strokes = emptyList<SignatureStroke>()
        var image = ""
        var removeBackground = false
        var crop: PdfSignatureCrop? = null
        var processFailure: Exception? = null
        var processGate: CompletableDeferred<Unit>? = null
        val discarded = mutableListOf<String>()
        override suspend fun stageImage(uri: String) = PdfSignatureImage("staged:$uri", 3f)
        override suspend fun processImage(uri: String, crop: PdfSignatureCrop): PdfSignatureImage {
            this.crop = crop
            processGate?.await()
            processFailure?.let { throw it }
            return PdfSignatureImage("processed:$uri", 3f)
        }
        override suspend fun saveProcessedImage(name: String, uri: String, editingId: String?): SignatureModel {
            image = uri
            return if (editingId == null) create(name) else update(editingId, name, SignatureSource(SignatureSourceType.IMAGE))
        }
        override suspend fun discardStagedImage(uri: String) { discarded += uri }
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
        override suspend fun updateDrawing(id: String, name: String, strokes: List<SignatureStroke>): SignatureModel {
            this.strokes = strokes
            return update(id, name, SignatureSource(SignatureSourceType.DRAW, strokes = strokes))
        }
        override suspend fun updateTyped(
            id: String, name: String, text: String, font: SignatureFont, colorArgb: Int,
        ): SignatureModel {
            this.text = text; this.font = font; color = colorArgb
            return update(id, name, SignatureSource(SignatureSourceType.TYPE, text = text, font = font, colorArgb = colorArgb))
        }
        override suspend fun updateImage(
            id: String, name: String, uri: String, removeLightBackground: Boolean,
        ): SignatureModel {
            image = uri; removeBackground = removeLightBackground
            return update(id, name, SignatureSource(SignatureSourceType.IMAGE))
        }
        private suspend fun update(id: String, name: String, source: SignatureSource): SignatureModel {
            gate?.await()
            failure?.let { throw it }
            updated++
            val previous = items.value.first { it.id == id }
            val result = previous.copy(
                name = name, imageUri = "file:///${id}-${previous.imageVersion + 1}.png",
                imageVersion = previous.imageVersion + 1, source = source,
            )
            items.value = items.value.map { if (it.id == id) result else it }
            return result
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
