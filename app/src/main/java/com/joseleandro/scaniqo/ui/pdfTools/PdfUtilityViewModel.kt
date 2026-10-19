package com.joseleandro.scaniqo.ui.pdfTools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.joseleandro.scaniqo.domain.model.PdfImageFormat
import com.joseleandro.scaniqo.domain.model.PdfPageSelection
import com.joseleandro.scaniqo.domain.model.ReceiptDraft
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.PdfUtilityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfCompressionSummary(
    val originalBytes: Long,
    val resultBytes: Long,
    val documentId: Long?,
)

data class PdfUtilityUiState(
    val operationId: String? = null,
    val documents: List<DocumentModel> = emptyList(),
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val createdDocumentId: Long? = null,
    val compressionResult: PdfCompressionSummary? = null,
    val receipt: ReceiptDraft? = null,
    val receiptProtected: Boolean = false,
)

class PdfUtilityViewModel(
    private val repository: PdfUtilityRepository,
    private val documents: DocumentRepository,
) : ViewModel() {
    private val state = MutableStateFlow(PdfUtilityUiState())
    val uiState = state.asStateFlow()
    private var operation: Job? = null
    private var operationGeneration = 0L
    private var sensitiveReceipt = false
    private var receiptExportSequence = 0L
    private var preparedReceiptExport: Pair<Long, ReceiptDraft>? = null

    private var observation: Job? = null

    init { retryDocuments() }

    /** Nova entrada limpa o rascunho; rotação e retorno do seletor conservam a mesma operação. */
    fun beginOperation(id: String) {
        if (state.value.operationId == id) return
        operationGeneration++
        operation?.cancel()
        operation = null
        sensitiveReceipt = false
        preparedReceiptExport = null
        receiptExportSequence++
        state.update { PdfUtilityUiState(
            operationId = id, documents = it.documents, loading = it.loading, loadError = it.loadError,
        ) }
    }

    fun retryDocuments() {
        observation?.cancel()
        state.update { it.copy(loading = true, loadError = false) }
        observation = viewModelScope.launch {
            documents.observeDocuments().catch {
                state.update { it.copy(loading = false, loadError = true) }
            }.collect { values -> state.update { it.copy(documents = values, loading = false, loadError = false) } }
        }
    }

    fun compress(id: Long, name: String, level: PdfCompressionLevel, password: String?) = runOperation { request ->
        val document = document(id)
        validateName(name)
        state.update { it.copy(compressionResult = null) }
        val result = repository.compress(document, level, password)
        val scan = result.scan
        val id = if (scan == null) null else commitPrepared(scan, name, document.tagId)
        if (request == operationGeneration) state.update {
            it.copy(compressionResult = PdfCompressionSummary(result.originalBytes, result.resultBytes, id))
        }
    }

    fun clearCompressionResult() {
        if (!state.value.busy) state.update { it.copy(compressionResult = null, error = null) }
    }

    fun extract(id: Long, pages: String, name: String, password: String?) = runOperation { request ->
        val document = document(id)
        validateName(name)
        create(name, document.tagId, request) {
            repository.extractPages(document, PdfPageSelection.parse(pages, document.pageCount), password)
        }
    }

    fun createFromImages(uris: List<String>, name: String, idCard: Boolean) = runOperation { request ->
        validateName(name)
        create(name, null, request) { repository.imagesToPdf(uris, idCard) }
    }

    fun exportImages(id: Long, pages: String, format: PdfImageFormat, target: String, password: String?) = runOperation { request ->
        val document = document(id)
        repository.exportImages(document, PdfPageSelection.parse(pages, document.pageCount), format, target, password)
        if (request == operationGeneration) state.update { it.copy(message = "Imagens exportadas em um arquivo ZIP.") }
    }

    fun readReceipt(id: Long, pageText: String, password: String?) = runOperation { request ->
        val document = document(id)
        val page = pageText.trim().toIntOrNull()
        require(page != null && page in 1..document.pageCount) { "Informe uma página válida do comprovante." }
        val receipt = repository.readReceipt(document, page - 1, password)
        if (request != operationGeneration) return@runOperation
        sensitiveReceipt = document.isProtected
        state.update { it.copy(receipt = receipt, receiptProtected = document.isProtected) }
    }

    fun updateReceipt(value: ReceiptDraft) {
        if (!state.value.busy) {
            preparedReceiptExport = null
            state.update { it.copy(receipt = value, message = null) }
        }
    }

    fun clearReceipt() {
        if (!state.value.busy) {
            sensitiveReceipt = false
            preparedReceiptExport = null
            state.update { it.copy(receipt = null, receiptProtected = false, message = null, error = null) }
        }
    }

    fun exportReceipt(target: String) {
        val receipt = state.value.receipt ?: return
        exportReceiptSnapshot(receipt, target)
    }

    /** Retém somente os campos aprovados para o seletor externo, sem manter o texto sensível na tela. */
    fun prepareReceiptExport(): Long? {
        if (state.value.busy || preparedReceiptExport != null) return null
        val receipt = state.value.receipt ?: return null
        val token = ++receiptExportSequence
        preparedReceiptExport = token to receipt.copy(sourceText = "")
        return token
    }

    fun completeReceiptExport(target: String?, token: Long?) {
        val pending = preparedReceiptExport
        if (pending == null || token != pending.first) {
            if (target != null) reportError("Revise os dados novamente antes de exportar.")
            return
        }
        if (state.value.busy) return
        preparedReceiptExport = null
        if (target != null) exportReceiptSnapshot(pending.second, target)
    }

    private fun exportReceiptSnapshot(receipt: ReceiptDraft, target: String) = runOperation { request ->
        repository.exportReceipt(receipt, target)
        if (request == operationGeneration) state.update { it.copy(message = "Dados revisados exportados em CSV.") }
    }

    fun reportError(message: String) { state.update { it.copy(error = message) } }
    fun consumedDocument(id: Long) {
        if (state.value.createdDocumentId == id) state.update { it.copy(createdDocumentId = null) }
    }

    /** Credenciais nunca entram em SavedStateHandle; tarefas sensíveis param ao sair do app. */
    fun backgrounded() {
        operation?.cancel()
        if (sensitiveReceipt) state.update { it.copy(receipt = null, receiptProtected = false) }
        sensitiveReceipt = false
    }

    private fun runOperation(block: suspend (Long) -> Unit) {
        if (state.value.busy) return
        val request = operationGeneration
        state.update { it.copy(busy = true, error = null, message = null) }
        operation = viewModelScope.launch {
            try { block(request) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (request == operationGeneration) state.update {
                    it.copy(error = failure.message ?: "Não foi possível concluir a ferramenta.")
                }
            } finally {
                if (request == operationGeneration) state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun document(id: Long): DocumentModel {
        val found = requireNotNull(documents.findById(id)) { "O documento não está mais disponível." }
        currentCoroutineContext().ensureActive()
        return found
    }

    private suspend fun create(name: String, tagId: Long?, request: Long, prepare: suspend () -> ScannedDocument) {
        val id = commitPrepared(prepare(), name, tagId)
        if (request == operationGeneration) state.update { it.copy(createdDocumentId = id) }
    }

    private suspend fun commitPrepared(scan: ScannedDocument, name: String, tagId: Long?): Long {
        var committed = false
        try {
            currentCoroutineContext().ensureActive()
            // A confirmação termina junto com a entrega, mesmo que a tela seja encerrada.
            return withContext(NonCancellable) {
                documents.create(scan, name.trim(), tagId).also { committed = true }
            }
        } finally {
            if (!committed) withContext(NonCancellable) { runCatching { documents.discardScan(scan) } }
        }
    }

    private fun validateName(name: String) {
        require(name.trim().length in 1..DOCUMENT_NAME_MAX_LENGTH) {
            "Informe um nome de até $DOCUMENT_NAME_MAX_LENGTH caracteres."
        }
    }
}
