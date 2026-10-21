package com.joseleandro.scaniqo.ui.signatures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignatureInput { DRAW, TYPE, IMAGE }

data class SignatureEditorState(
    val name: String = "",
    val input: SignatureInput = SignatureInput.DRAW,
    val strokes: List<SignatureStroke> = emptyList(),
    val text: String = "",
    val font: SignatureFont = SignatureFont.CURSIVE,
    val colorArgb: Int = 0xFF18212B.toInt(),
    val strokeWidth: Float = 0.004f,
    val imageUri: String? = null,
    val removeLightBackground: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val targetDocumentId: Long? = null,
)

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
    data class RemoveBackgroundChanged(val enabled: Boolean) : SignaturesEvent
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

    init { observe() }

    fun onEvent(event: SignaturesEvent) {
        when (event) {
            SignaturesEvent.Create -> if (!state.value.isBusy && state.value.editor == null) state.update { it.copy(editor = SignatureEditorState(name = defaultName())) }
            is SignaturesEvent.CreateForDocument -> if (!state.value.isBusy && state.value.editor == null) {
                state.update { it.copy(editor = SignatureEditorState(name = defaultName(), targetDocumentId = event.documentId)) }
            }
            is SignaturesEvent.AcknowledgeDocumentSelection -> state.update {
                if (it.pendingDocumentSelection?.signatureId == event.signatureId) it.copy(pendingDocumentSelection = null) else it
            }
            SignaturesEvent.DismissEditor -> edit { null }
            is SignaturesEvent.NameChanged -> edit { it.copy(name = event.name.take(80), error = null) }
            is SignaturesEvent.InputChanged -> edit { it.copy(input = event.input, error = null) }
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
            is SignaturesEvent.ImageSelected -> if (event.uri != null) edit { it.copy(imageUri = event.uri, error = null) }
            is SignaturesEvent.RemoveBackgroundChanged -> edit { it.copy(removeLightBackground = event.enabled) }
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
        if (editor.isSaving) return
        state.update { it.copy(editor = editor.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            try {
                val created = when (editor.input) {
                    SignatureInput.DRAW -> repository.createDrawing(editor.name, editor.strokes)
                    SignatureInput.TYPE -> repository.createTyped(editor.name, editor.text, editor.font, editor.colorArgb)
                    SignatureInput.IMAGE -> repository.importImage(
                        editor.name, requireNotNull(editor.imageUri) { "Escolha uma imagem da assinatura." },
                        editor.removeLightBackground,
                    )
                }
                state.update {
                    it.copy(
                        editor = null,
                        message = "Assinatura salva",
                        pendingDocumentSelection = editor.targetDocumentId?.let { documentId ->
                            SignatureDocumentSelection(documentId, created.id)
                        },
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                state.update { it.copy(editor = it.editor?.copy(error = error.message ?: "Não foi possível salvar a assinatura.")) }
            } finally { state.update { it.copy(editor = it.editor?.copy(isSaving = false)) } }
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
