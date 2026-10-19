package com.joseleandro.scaniqo.ui.documentCertificates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.PdfCertificateDocument
import com.joseleandro.scaniqo.domain.model.PdfSignedExport
import com.joseleandro.scaniqo.domain.repository.PdfCertificateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentCertificatesUiState(
    val operationId: String? = null,
    val document: PdfCertificateDocument? = null,
    val alias: String? = null,
    val pendingExport: PdfSignedExport? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class DocumentCertificatesViewModel(private val repository: PdfCertificateRepository) : ViewModel() {
    private val state = MutableStateFlow(DocumentCertificatesUiState())
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
        state.value = DocumentCertificatesUiState(operationId = id)
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
        state.value = DocumentCertificatesUiState(operationId = state.value.operationId, document = document, busy = true)
    }

    fun selectCertificate(alias: String?, sessionId: String?) {
        if (alias.isNullOrBlank() || sessionId == null || state.value.busy || state.value.document?.sessionId != sessionId) return
        state.value.pendingExport?.let(repository::discard)
        state.update { it.copy(alias = alias, pendingExport = null, error = null, message = null) }
    }

    fun sign() {
        val document = state.value.document ?: return
        val alias = state.value.alias ?: return
        if (!document.canSign) return
        perform { request ->
            val result = repository.sign(document, alias)
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
            if (request == generation) state.update { it.copy(pendingExport = null, message = "PDF assinado salvo.") }
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
