package com.joseleandro.scaniqo.ui.signatures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.SignatureFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignatureSourceType
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignatureInput { DRAW, TYPE, IMAGE, SCAN;
    val isImage: Boolean get() = this == IMAGE || this == SCAN
}
enum class SignatureImageStep { SELECT, CAMERA, CROP, PREVIEW }

data class SignatureEditorState(
    val name: String = "",
    val editingId: String? = null,
    val input: SignatureInput = SignatureInput.DRAW,
    val strokes: List<SignatureStroke> = emptyList(),
    val text: String = "",
    val font: SignatureFont = SignatureFont.CURSIVE,
    val colorArgb: Int = 0xFF18212B.toInt(),
    val strokeWidth: Float = 0.004f,
    val imageUri: String? = null,
    val originalImageUri: String? = null,
    val imageStep: SignatureImageStep = SignatureImageStep.SELECT,
    val imageAspectRatio: Float = 3f,
    val crop: PdfSignatureCrop = PdfSignatureCrop(0f, 0f, 1f, 1f),
    val processedImageUri: String? = null,
    val fromCamera: Boolean = false,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val showSaveDialog: Boolean = false,
    val error: String? = null,
    val targetDocumentId: Long? = null,
) {
    val canSave: Boolean get() = !isSaving && !isProcessing && when (input) {
        SignatureInput.DRAW -> strokes.any { it.points.size > 1 }
        SignatureInput.TYPE -> text.isNotBlank()
        SignatureInput.IMAGE, SignatureInput.SCAN -> imageStep == SignatureImageStep.PREVIEW && processedImageUri != null
    }
}

data class SignaturesUiState(
    val signatures: List<SignatureModel> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val editor: SignatureEditorState? = null,
    val deleteCandidate: SignatureModel? = null,
    val renameCandidate: SignatureModel? = null,
    val renameText: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val pendingDocumentSelection: SignatureDocumentSelection? = null,
)

sealed interface SignaturesEvent {
    data object Create : SignaturesEvent
    data class Edit(val id: String) : SignaturesEvent
    data class CreateForDocument(val documentId: Long) : SignaturesEvent
    data class AcknowledgeDocumentSelection(val signatureId: String) : SignaturesEvent
    data object DismissEditor : SignaturesEvent
    data class NameChanged(val name: String) : SignaturesEvent
    data class InputChanged(val input: SignatureInput) : SignaturesEvent
    data class TextChanged(val text: String) : SignaturesEvent
    data class FontChanged(val font: SignatureFont) : SignaturesEvent
    data class ColorChanged(val argb: Int) : SignaturesEvent
    data class WidthChanged(val width: Float) : SignaturesEvent
    data class StrokeAdded(val stroke: SignatureStroke) : SignaturesEvent
    data object Undo : SignaturesEvent
    data object Clear : SignaturesEvent
    data class ImageSelected(val uri: String?) : SignaturesEvent
    data object OpenCamera : SignaturesEvent
    data class PhotoCaptured(val uri: String, val crop: PdfSignatureCrop = PdfSignatureCrop(0f, 0f, 1f, 1f)) : SignaturesEvent
    data class CropChanged(val crop: PdfSignatureCrop) : SignaturesEvent
    data object ProcessImage : SignaturesEvent
    data object AdjustCrop : SignaturesEvent
    data object EditorBack : SignaturesEvent
    data object RequestSave : SignaturesEvent
    data object CancelSave : SignaturesEvent
    data object Save : SignaturesEvent
    data class RequestDelete(val signature: SignatureModel) : SignaturesEvent
    data object ConfirmDelete : SignaturesEvent
    data object DismissDialog : SignaturesEvent
    data class RequestRename(val signature: SignatureModel) : SignaturesEvent
    data class RenameChanged(val name: String) : SignaturesEvent
    data object ConfirmRename : SignaturesEvent
    data object Retry : SignaturesEvent
    data object DismissMessage : SignaturesEvent
}

class SignaturesViewModel(
    private val repository: SignatureRepository,
    private val defaultName: () -> String = { "Signature" },
) : ViewModel() {
    private val state = MutableStateFlow(SignaturesUiState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    private var imageJob: Job? = null
    private var imageGeneration = 0
    private val cameraSources = mutableSetOf<String>()

    init { observe() }

    fun onEvent(event: SignaturesEvent) {
        when (event) {
            SignaturesEvent.Create -> if (!state.value.isBusy && state.value.editor == null) state.update { it.copy(editor = SignatureEditorState(name = defaultName())) }
            is SignaturesEvent.Edit -> if (!state.value.isBusy && state.value.editor == null) {
                state.value.signatures.firstOrNull { it.id == event.id }?.let { signature ->
                    state.update { it.copy(editor = editorFor(signature)) }
                    if (state.value.editor?.input == SignatureInput.IMAGE) selectImage(signature.imageUri, false)
                }
            }
            is SignaturesEvent.CreateForDocument -> if (!state.value.isBusy && state.value.editor == null) {
                state.update { it.copy(editor = SignatureEditorState(name = defaultName(), targetDocumentId = event.documentId)) }
            }
            is SignaturesEvent.AcknowledgeDocumentSelection -> state.update {
                if (it.pendingDocumentSelection?.signatureId == event.signatureId) it.copy(pendingDocumentSelection = null) else it
            }
            SignaturesEvent.DismissEditor -> dismissEditor()
            SignaturesEvent.EditorBack -> editorBack()
            is SignaturesEvent.NameChanged -> edit { it.copy(name = event.name.take(80), error = null) }
            is SignaturesEvent.InputChanged -> changeInput(event.input)
            is SignaturesEvent.TextChanged -> edit { it.copy(text = event.text.take(80), error = null) }
            is SignaturesEvent.FontChanged -> edit { it.copy(font = event.font) }
            is SignaturesEvent.ColorChanged -> edit { it.copy(colorArgb = event.argb) }
            is SignaturesEvent.WidthChanged -> edit { it.copy(strokeWidth = event.width.coerceIn(0.002f, 0.014f)) }
            is SignaturesEvent.StrokeAdded -> edit {
                if (it.strokes.size >= 500) it.copy(error = "Limite de traços atingido. Desfaça um traço para continuar.")
                else it.copy(strokes = it.strokes + event.stroke, error = null)
            }
            SignaturesEvent.Undo -> edit { it.copy(strokes = it.strokes.dropLast(1)) }
            SignaturesEvent.Clear -> edit { it.copy(strokes = emptyList()) }
            is SignaturesEvent.ImageSelected -> event.uri?.let { selectImage(it, false) }
            is SignaturesEvent.PhotoCaptured -> selectImage(event.uri, true, event.crop)
            SignaturesEvent.OpenCamera -> if (state.value.editor?.isProcessing != true) edit {
                it.copy(input = SignatureInput.SCAN, imageStep = SignatureImageStep.CAMERA, error = null)
            }
            is SignaturesEvent.CropChanged -> if (event.crop.isValid && state.value.editor?.input == SignatureInput.IMAGE && state.value.editor?.isProcessing != true && state.value.editor?.isSaving != true) {
                val old = state.value.editor?.processedImageUri
                edit { it.copy(crop = event.crop, processedImageUri = null, error = null) }
                discard(old)
            }
            SignaturesEvent.ProcessImage -> processImage()
            SignaturesEvent.AdjustCrop -> if (state.value.editor?.input == SignatureInput.IMAGE && state.value.editor?.isProcessing != true) edit { it.copy(imageStep = SignatureImageStep.CROP, error = null) }
            SignaturesEvent.RequestSave -> edit {
                if (it.canSave) it.copy(showSaveDialog = true, error = null)
                else it.copy(error = "Crie uma assinatura antes de salvar.")
            }
            SignaturesEvent.CancelSave -> edit { it.copy(showSaveDialog = false, error = null) }
            SignaturesEvent.Save -> save()
            is SignaturesEvent.RequestDelete -> if (!state.value.isBusy) state.update { it.copy(deleteCandidate = event.signature, error = null) }
            is SignaturesEvent.RequestRename -> if (!state.value.isBusy) state.update {
                it.copy(renameCandidate = event.signature, renameText = event.signature.name, error = null)
            }
            is SignaturesEvent.RenameChanged -> if (!state.value.isBusy) state.update { it.copy(renameText = event.name.take(80)) }
            SignaturesEvent.ConfirmDelete -> mutate(
                action = { state.value.deleteCandidate?.let { repository.delete(it.id) } },
                message = "Assinatura excluída",
            )
            SignaturesEvent.ConfirmRename -> mutate(
                action = { state.value.renameCandidate?.let { repository.rename(it.id, state.value.renameText) } },
                message = "Assinatura renomeada",
            )
            SignaturesEvent.DismissDialog -> if (!state.value.isBusy) state.update { it.copy(deleteCandidate = null, renameCandidate = null, error = null) }
            SignaturesEvent.Retry -> observe()
            SignaturesEvent.DismissMessage -> state.update { it.copy(message = null) }
        }
    }

    private fun edit(update: (SignatureEditorState) -> SignatureEditorState?) {
        val editor = state.value.editor ?: return
        if (!editor.isSaving) state.update { it.copy(editor = update(editor)) }
    }

    private fun editorFor(signature: SignatureModel): SignatureEditorState {
        val source = signature.source
        val input = when (source?.type) {
            SignatureSourceType.DRAW -> SignatureInput.DRAW
            SignatureSourceType.TYPE -> SignatureInput.TYPE
            SignatureSourceType.IMAGE, null -> SignatureInput.IMAGE
        }
        val lastStroke = source?.strokes?.lastOrNull()
        return SignatureEditorState(
            name = signature.name,
            editingId = signature.id,
            input = input,
            strokes = source?.strokes.orEmpty(),
            text = source?.text.orEmpty(),
            font = source?.font ?: SignatureFont.CURSIVE,
            colorArgb = lastStroke?.colorArgb ?: source?.colorArgb ?: 0xFF18212B.toInt(),
            strokeWidth = lastStroke?.width ?: 0.004f,
            imageUri = signature.imageUri,
            originalImageUri = signature.imageUri,
        )
    }

    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            state.update { it.copy(isLoading = true, loadError = false) }
            try {
                repository.observeSignatures().collect { signatures ->
                    state.update { it.copy(signatures = signatures, isLoading = false) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.update { it.copy(isLoading = false, loadError = true) } }
        }
    }

    private fun save() {
        val editor = state.value.editor ?: return
        if (!editor.showSaveDialog || !editor.canSave) return
        if (editor.name.isBlank()) {
            edit { it.copy(error = "Informe um nome para a assinatura.") }
            return
        }
        state.update { it.copy(editor = editor.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            try {
                val saved = when (editor.input) {
                    SignatureInput.DRAW -> editor.editingId?.let { id ->
                        repository.updateDrawing(id, editor.name, editor.strokes)
                    } ?: repository.createDrawing(editor.name, editor.strokes)
                    SignatureInput.TYPE -> editor.editingId?.let { id ->
                        repository.updateTyped(id, editor.name, editor.text, editor.font, editor.colorArgb)
                    } ?: repository.createTyped(editor.name, editor.text, editor.font, editor.colorArgb)
                    SignatureInput.IMAGE, SignatureInput.SCAN -> {
                        repository.saveProcessedImage(
                            editor.name, requireNotNull(editor.processedImageUri), editor.editingId,
                        )
                    }
                }
                discard(editor.imageUri, editor.processedImageUri)
                state.update {
                    it.copy(
                        editor = null,
                        message = if (editor.editingId == null) "Assinatura salva" else "Assinatura atualizada",
                        pendingDocumentSelection = if (editor.editingId == null) {
                            editor.targetDocumentId?.let { documentId ->
                                SignatureDocumentSelection(documentId, saved.id)
                            }
                        } else it.pendingDocumentSelection,
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                state.update { it.copy(editor = it.editor?.copy(error = error.message ?: "Não foi possível salvar a assinatura.")) }
            } finally { state.update { it.copy(editor = it.editor?.copy(isSaving = false)) } }
        }
    }

    private fun changeInput(input: SignatureInput) {
        val editor = state.value.editor ?: return
        if (editor.isSaving || editor.isProcessing || editor.showSaveDialog || editor.input == input) return
        val differentSource = input.isImage && editor.fromCamera != (input == SignatureInput.SCAN)
        if (differentSource) discard(editor.imageUri, editor.processedImageUri)
        edit {
            val updated = if (differentSource) it.copy(
                imageUri = null, processedImageUri = null, imageStep = SignatureImageStep.SELECT,
                fromCamera = input == SignatureInput.SCAN, crop = PdfSignatureCrop(0f, 0f, 1f, 1f),
            ) else it
            updated.copy(
                input = input, error = null,
                imageStep = if (input == SignatureInput.SCAN && updated.imageStep == SignatureImageStep.SELECT)
                    SignatureImageStep.CAMERA else updated.imageStep,
            )
        }
    }

    private fun selectImage(
        uri: String,
        fromCamera: Boolean,
        crop: PdfSignatureCrop = PdfSignatureCrop(0f, 0f, 1f, 1f),
    ) {
        val editor = state.value.editor
        if (editor == null || editor.isSaving ||
            (fromCamera && (editor.input != SignatureInput.SCAN || editor.imageStep != SignatureImageStep.CAMERA))) {
            if (fromCamera) discard(uri)
            return
        }
        if (fromCamera) cameraSources += uri
        imageJob?.cancel()
        val generation = ++imageGeneration
        discard(editor.imageUri, editor.processedImageUri)
        state.update { it.copy(editor = editor.copy(
            input = if (fromCamera) SignatureInput.SCAN else SignatureInput.IMAGE,
            imageUri = null, processedImageUri = null, showSaveDialog = false,
            imageStep = if (fromCamera) SignatureImageStep.PREVIEW else SignatureImageStep.CROP,
            fromCamera = fromCamera, crop = crop, isProcessing = true, error = null,
        )) }
        imageJob = viewModelScope.launch {
            var staged: String? = null
            var processed: String? = null
            var retained = false
            try {
                val source = repository.stageImage(uri)
                staged = source.imageUri
                val result = if (fromCamera) repository.processImage(source.imageUri, crop) else null
                processed = result?.imageUri
                if (generation == imageGeneration && state.value.editor != null) {
                    edit { it.copy(
                        imageUri = source.imageUri, imageAspectRatio = source.aspectRatio,
                        processedImageUri = result?.imageUri, isProcessing = false,
                    ) }
                    retained = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == imageGeneration) edit { it.copy(
                    isProcessing = false,
                    imageStep = if (fromCamera) SignatureImageStep.PREVIEW else SignatureImageStep.SELECT,
                    error = error.message ?: "Não foi possível processar a assinatura. Tente outra imagem.",
                ) }
            } finally {
                withContext(NonCancellable) {
                    if (!retained) {
                        staged?.let { repository.discardStagedImage(it) }
                        processed?.let { repository.discardStagedImage(it) }
                    }
                    if (fromCamera) repository.discardStagedImage(uri)
                }
                cameraSources -= uri
            }
        }
    }

    private fun processImage() {
        val editor = state.value.editor ?: return
        val uri = editor.imageUri ?: return
        if (editor.isSaving || editor.isProcessing || editor.input != SignatureInput.IMAGE || editor.imageStep != SignatureImageStep.CROP) return
        val generation = ++imageGeneration
        discard(editor.processedImageUri)
        edit { it.copy(isProcessing = true, processedImageUri = null, error = null) }
        imageJob = viewModelScope.launch {
            var processed: String? = null
            var retained = false
            try {
                val result = repository.processImage(uri, editor.crop)
                processed = result.imageUri
                if (generation == imageGeneration && state.value.editor != null) {
                    edit { it.copy(
                        processedImageUri = result.imageUri, isProcessing = false,
                        imageStep = SignatureImageStep.PREVIEW,
                    ) }
                    retained = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == imageGeneration) edit { it.copy(
                    isProcessing = false,
                    error = error.message ?: "Não foi possível processar a assinatura. Ajuste o recorte e tente novamente.",
                ) }
            } finally {
                if (!retained) withContext(NonCancellable) {
                    processed?.let { repository.discardStagedImage(it) }
                }
            }
        }
    }

    private fun editorBack() {
        val editor = state.value.editor ?: return
        if (editor.isSaving) return
        if (editor.showSaveDialog) {
            edit { it.copy(showSaveDialog = false, error = null) }
            return
        }
        if (!editor.input.isImage) {
            dismissEditor()
            return
        }
        imageJob?.cancel()
        imageGeneration++
        discard(*cameraSources.toTypedArray())
        cameraSources.clear()
        when (editor.imageStep) {
            SignatureImageStep.PREVIEW -> {
                if (editor.fromCamera) {
                    discard(editor.imageUri, editor.processedImageUri)
                    edit { it.copy(imageStep = SignatureImageStep.CAMERA, imageUri = null,
                        processedImageUri = null, isProcessing = false, error = null) }
                } else edit { it.copy(imageStep = SignatureImageStep.CROP, isProcessing = false, error = null) }
            }
            SignatureImageStep.CAMERA -> edit { it.copy(
                imageStep = if (it.processedImageUri != null) SignatureImageStep.PREVIEW else SignatureImageStep.SELECT,
                isProcessing = false, error = null,
            ) }
            SignatureImageStep.CROP -> {
                discard(editor.imageUri, editor.processedImageUri)
                edit { it.copy(imageStep = SignatureImageStep.SELECT, imageUri = null,
                    processedImageUri = null, isProcessing = false, error = null) }
            }
            SignatureImageStep.SELECT -> dismissEditor()
        }
    }

    private fun dismissEditor() {
        val editor = state.value.editor ?: return
        if (editor.isSaving) return
        imageJob?.cancel()
        imageGeneration++
        state.update { it.copy(editor = null) }
        discard(editor.imageUri, editor.processedImageUri, *cameraSources.toTypedArray())
        cameraSources.clear()
    }

    private fun discard(vararg uris: String?) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                uris.filterNotNull().distinct().forEach { repository.discardStagedImage(it) }
            }
        }
    }

    override fun onCleared() {
        val editor = state.value.editor
        imageGeneration++
        imageJob?.cancel()
        val drafts = (listOfNotNull(editor?.imageUri, editor?.processedImageUri) + cameraSources).distinct()
        cameraSources.clear()
        if (drafts.isNotEmpty()) CoroutineScope(Dispatchers.IO).launch {
            drafts.forEach { repository.discardStagedImage(it) }
        }
    }

    private fun mutate(action: suspend () -> Unit, message: String) {
        if (state.value.isBusy) return
        state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                action()
                state.update { it.copy(deleteCandidate = null, renameCandidate = null, message = message) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { state.update { it.copy(error = error.message ?: "Não foi possível concluir a ação.") } }
            finally { state.update { it.copy(isBusy = false) } }
        }
    }
}
