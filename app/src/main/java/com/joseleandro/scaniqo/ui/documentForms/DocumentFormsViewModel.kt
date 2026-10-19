package com.joseleandro.scaniqo.ui.documentForms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.PdfFormDocument
import com.joseleandro.scaniqo.domain.model.PdfFormExport
import com.joseleandro.scaniqo.domain.repository.PdfFormRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentFormsUiState(
    val operationId: String? = null,
    val document: PdfFormDocument? = null,
    val values: Map<String, List<String>> = emptyMap(),
    val pendingExport: PdfFormExport? = null,
    val busy: Boolean = false,
    val dirty: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class DocumentFormsViewModel(private val repository: PdfFormRepository) : ViewModel() {
    private val state = MutableStateFlow(DocumentFormsUiState())
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    /** A identidade da entrada diferencia uma nova tarefa de rotação ou retorno de um seletor. */
    fun beginOperation(id: String) {
        if (state.value.operationId == id) return
        generation++
        val previous = state.value
        val previousJob = job
        job = null
        previousJob?.cancel()
        state.value = DocumentFormsUiState(operationId = id)
        val discardPrevious = {
            previous.document?.let(repository::discard)
            previous.pendingExport?.let(repository::discard)
        }
        previousJob?.invokeOnCompletion { discardPrevious() } ?: discardPrevious()
    }

    fun open(uri: String) = perform { request ->
        val document = repository.open(uri)
        if (request != generation || !currentCoroutineContext().isActive) {
            repository.discard(document)
            return@perform
        }
        state.value.document?.let(repository::discard)
        state.value.pendingExport?.let(repository::discard)
        state.value = DocumentFormsUiState(
            operationId = state.value.operationId,
            document = document, values = document.fields.associate { it.id to it.values }, busy = true,
        )
    }

    fun change(id: String, values: List<String>) {
        val field = state.value.document?.fields?.firstOrNull { it.id == id } ?: return
        if (state.value.busy || field.readOnly) return
        state.value.pendingExport?.let(repository::discard)
        state.update { it.copy(values = it.values + (id to values), pendingExport = null, dirty = true, message = null, error = null) }
    }

    fun prepareExport() {
        val document = state.value.document ?: return
        val values = state.value.values
        perform { request ->
            val result = repository.fill(document, values)
            if (request != generation || !currentCoroutineContext().isActive) {
                repository.discard(result)
                return@perform
            }
            state.value.pendingExport?.let(repository::discard)
            state.update { it.copy(pendingExport = result) }
        }
    }

    fun export(uri: String, expectedResultId: String? = state.value.pendingExport?.id) {
        val result = state.value.pendingExport ?: return
        if (result.id != expectedResultId) return
        perform { request ->
            repository.export(result, uri)
            repository.discard(result)
            if (request == generation) state.update { it.copy(pendingExport = null, dirty = false, message = "Formulário salvo com os campos interativos.") }
        }
    }

    fun reportError(message: String) { state.update { it.copy(error = message) } }

    private fun perform(block: suspend (Long) -> Unit) {
        if (state.value.busy) return
        val request = generation
        state.update { it.copy(busy = true, error = null, message = null) }
        job = viewModelScope.launch {
            try { block(request) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (request == generation) state.update { it.copy(error = failure.message ?: "Não foi possível concluir a operação.") }
            }
            finally { if (request == generation) state.update { it.copy(busy = false) } }
        }
    }

    override fun onCleared() {
        job?.invokeOnCompletion { discardState() } ?: discardState()
    }

    private fun discardState() {
        state.value.document?.let(repository::discard)
        state.value.pendingExport?.let(repository::discard)
    }
}
