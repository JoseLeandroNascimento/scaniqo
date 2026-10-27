package com.joseleandro.scaniqo.ui.pdfTools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.core.ads.ToolAdAccessPolicy
import com.joseleandro.scaniqo.core.ads.ToolAdPermit
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
import java.util.UUID

data class PdfPreparedOutput(
    val scan: ScannedDocument,
    val operationId: String,
    val suggestedName: String,
    val suggestedTagId: Long?,
)

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
    val preparedOutput: PdfPreparedOutput? = null,
    val awaitingSaveOperationId: String? = null,
    val savedDocumentId: Long? = null,
    val exportCompleted: Boolean = false,
    val receipt: ReceiptDraft? = null,
    val receiptProtected: Boolean = false,
)

private const val ROUTE_ID = "pdf_utility_route_id"
private const val PREPARED_OPERATION_ID = "pdf_utility_prepared_operation_id"
private const val PREPARED_URI = "pdf_utility_prepared_uri"
private const val PREPARED_PAGE_COUNT = "pdf_utility_prepared_page_count"
private const val PREPARED_CREATED_AT = "pdf_utility_prepared_created_at"
private const val PREPARED_SIZE_BYTES = "pdf_utility_prepared_size_bytes"
private const val PREPARED_THUMBNAIL = "pdf_utility_prepared_thumbnail"
private const val PREPARED_PROTECTION = "pdf_utility_prepared_protection"
private const val PREPARED_NAME = "pdf_utility_prepared_name"
private const val PREPARED_TAG = "pdf_utility_prepared_tag"

class PdfUtilityViewModel(
    private val repository: PdfUtilityRepository,
    private val documents: DocumentRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val adAccess: ToolAdAccessPolicy? = null,
) : ViewModel() {
    private val restoredOutput = restorePreparedOutput()
    private val state = MutableStateFlow(PdfUtilityUiState(
        operationId = savedStateHandle[ROUTE_ID],
        preparedOutput = restoredOutput,
        awaitingSaveOperationId = restoredOutput?.operationId,
    ))
    val uiState = state.asStateFlow()
    private var operation: Job? = null
    private var operationGeneration = 0L
    private var sensitiveReceipt = false
    private var receiptExportSequence = 0L
    private var preparedReceiptExport: Pair<Long, ReceiptDraft>? = null

    private var observation: Job? = null
    private var toolPermit: ToolAdPermit? = null

    private fun finishToolPermit(permit: ToolAdPermit?, success: Boolean) {
        if (permit == null || toolPermit !== permit) return
        toolPermit = null
        adAccess?.finish(permit, success)
    }

    override fun onCleared() {
        finishToolPermit(toolPermit, success = false)
        super.onCleared()
    }

    init { retryDocuments() }

    private fun restorePreparedOutput(): PdfPreparedOutput? {
        val operationId = savedStateHandle.get<String>(PREPARED_OPERATION_ID) ?: return null
        val uri = savedStateHandle.get<String>(PREPARED_URI) ?: return null
        val pageCount = savedStateHandle.get<Int>(PREPARED_PAGE_COUNT) ?: return null
        val createdAt = savedStateHandle.get<Long>(PREPARED_CREATED_AT) ?: return null
        val sizeBytes = savedStateHandle.get<Long>(PREPARED_SIZE_BYTES) ?: return null
        val name = savedStateHandle.get<String>(PREPARED_NAME) ?: return null
        if (pageCount <= 0 || uri.isBlank()) return null
        return PdfPreparedOutput(
            scan = ScannedDocument(
                pdfUri = uri,
                pageCount = pageCount,
                createdAt = createdAt,
                sizeBytes = sizeBytes,
                thumbnailUri = savedStateHandle[PREPARED_THUMBNAIL],
                protectionKeyId = savedStateHandle[PREPARED_PROTECTION],
            ),
            operationId = operationId,
            suggestedName = name,
            suggestedTagId = savedStateHandle[PREPARED_TAG],
        )
    }

    private fun savePreparedState(output: PdfPreparedOutput) {
        savedStateHandle[PREPARED_OPERATION_ID] = output.operationId
        savedStateHandle[PREPARED_URI] = output.scan.pdfUri
        savedStateHandle[PREPARED_PAGE_COUNT] = output.scan.pageCount
        savedStateHandle[PREPARED_CREATED_AT] = output.scan.createdAt
        savedStateHandle[PREPARED_SIZE_BYTES] = output.scan.sizeBytes
        savedStateHandle[PREPARED_THUMBNAIL] = output.scan.thumbnailUri
        savedStateHandle[PREPARED_PROTECTION] = output.scan.protectionKeyId
        savedStateHandle[PREPARED_NAME] = output.suggestedName
        savedStateHandle[PREPARED_TAG] = output.suggestedTagId
    }

    private fun clearPreparedState() {
        listOf(PREPARED_OPERATION_ID, PREPARED_URI, PREPARED_PAGE_COUNT, PREPARED_CREATED_AT,
            PREPARED_SIZE_BYTES, PREPARED_THUMBNAIL, PREPARED_PROTECTION, PREPARED_NAME,
            PREPARED_TAG).forEach { savedStateHandle.remove<Any>(it) }
    }

    /** Nova entrada limpa o rascunho; rotação e retorno do seletor conservam a mesma operação. */
    fun beginOperation(id: String) {
        if (state.value.operationId == id) return
        operationGeneration++
        operation?.cancel()
        finishToolPermit(toolPermit, success = false)
        // Um temporário não entregue ainda pertence a esta ferramenta.
        state.value.preparedOutput?.let { output ->
            viewModelScope.launch { withContext(NonCancellable) { runCatching { documents.discardScan(output.scan) } } }
        }
        operation = null
        clearPreparedState()
        savedStateHandle[ROUTE_ID] = id
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

    fun compress(id: Long, name: String, level: PdfCompressionLevel, password: String?): Boolean {
        if (state.value.busy || state.value.awaitingSaveOperationId != null) return false
        val permit = if (adAccess != null) {
            adAccess.acquire(RewardedTool.COMPRESSION, UUID.randomUUID().toString()) ?: return false
        } else null
        toolPermit = permit
        runOperation { request ->
            var saved = false
            try {
                val document = document(id)
                validateName(name)
                state.update { it.copy(compressionResult = null) }
                val result = repository.compress(document, level, password)
                val scan = result.scan
                val outputId = if (scan == null) null else commitPrepared(scan, name, document.tagId)
                saved = outputId != null
                if (request == operationGeneration) state.update {
                    it.copy(compressionResult = PdfCompressionSummary(result.originalBytes, result.resultBytes, outputId))
                }
            } finally {
                finishToolPermit(permit, success = saved)
            }
        }
        return true
    }

    /** Gera a saída sem cadastrá-la; nome e tag são confirmados no formulário comum. */
    fun prepareCompression(
        id: Long,
        level: PdfCompressionLevel,
        password: String?,
        suggestedName: String,
        adUnavailable: Boolean = false,
    ): Boolean {
        if (state.value.busy || state.value.awaitingSaveOperationId != null) return false
        val permit = if (adAccess != null) {
            adAccess.acquire(RewardedTool.COMPRESSION, UUID.randomUUID().toString(), adUnavailable) ?: return false
        } else null
        toolPermit = permit
        runOperation { request ->
            var awaitingSave = false
            try {
                val document = document(id)
                state.update { it.copy(compressionResult = null, savedDocumentId = null) }
                val result = repository.compress(document, level, password)
                val scan = result.scan
                if (scan != null) {
                    prepareGenerated(scan, document.tagId, suggestedName, request)
                    awaitingSave = request == operationGeneration && state.value.awaitingSaveOperationId != null
                }
                if (request == operationGeneration) state.update {
                    it.copy(compressionResult = PdfCompressionSummary(result.originalBytes, result.resultBytes, null))
                }
            } finally {
                // A failed/no-gain attempt never spends the earned use.
                if (!awaitingSave) finishToolPermit(permit, success = false)
            }
        }
        return true
    }

    fun prepareExtraction(
        id: Long, pages: String, password: String?, suggestedName: String,
        adUnavailable: Boolean = false,
    ): Boolean {
        if (state.value.busy || state.value.awaitingSaveOperationId != null) return false
        val permit = adAccess?.acquire(
            RewardedTool.EXTRACT_PAGES, UUID.randomUUID().toString(), adUnavailable,
        )
        if (adAccess != null && permit == null) return false
        toolPermit = permit
        runOperation { request ->
            var awaitingSave = false
            try {
                val document = document(id)
                val scan = repository.extractPages(
                    document, PdfPageSelection.parse(pages, document.pageCount), password,
                )
                prepareGenerated(scan, document.tagId, suggestedName, request)
                awaitingSave = request == operationGeneration && state.value.awaitingSaveOperationId != null
            } finally {
                if (!awaitingSave) finishToolPermit(permit, success = false)
            }
        }
        return true
    }

    /** Chamar somente depois que o controlador comum aceitou o rascunho. */
    fun confirmPreparedDelivery(operationId: String) {
        if (state.value.preparedOutput?.operationId != operationId) return
        state.update { it.copy(preparedOutput = null) }
        clearPreparedState()
    }

    /** Reassocia o formulário compartilhado após recriação do ViewModel da ferramenta. */
    fun restoreAwaitingSave(operationId: String) {
        val routeId = state.value.operationId ?: return
        if (operationId.startsWith("$routeId:") && state.value.awaitingSaveOperationId == null) {
            state.update { it.copy(awaitingSaveOperationId = operationId) }
        }
    }

    /** Resultado salvo ou descartado, correlacionado com a tentativa que criou o PDF. */
    fun finishGenerated(operationId: String, documentId: Long?) {
        if (state.value.awaitingSaveOperationId != operationId) return
        finishToolPermit(toolPermit, success = documentId != null)
        clearPreparedState()
        state.update {
            it.copy(
                awaitingSaveOperationId = null,
                preparedOutput = null,
                savedDocumentId = documentId,
                compressionResult = it.compressionResult?.copy(documentId = documentId)
                    ?.takeIf { _ -> documentId != null },
            )
        }
    }

    private suspend fun prepareGenerated(
        scan: ScannedDocument, tagId: Long?, suggestedName: String, request: Long,
    ) {
        var accepted = false
        try {
            currentCoroutineContext().ensureActive()
            if (request != operationGeneration) return
            val operationId = "${state.value.operationId ?: "utility"}:${UUID.randomUUID()}"
            val output = PdfPreparedOutput(
                scan, operationId, suggestedName.take(DOCUMENT_NAME_MAX_LENGTH), tagId,
            )
            savePreparedState(output)
            state.update {
                it.copy(
                    preparedOutput = output,
                    awaitingSaveOperationId = operationId,
                    savedDocumentId = null,
                )
            }
            accepted = true
        } finally {
            if (!accepted) withContext(NonCancellable) {
                if (request == operationGeneration) clearPreparedState()
                runCatching { documents.discardScan(scan) }
            }
        }
    }

    fun clearCompressionResult() {
        if (!state.value.busy) state.update { it.copy(compressionResult = null, error = null) }
    }

    fun clearGeneratedCompletion() {
        if (!state.value.busy && state.value.awaitingSaveOperationId == null) state.update {
            it.copy(savedDocumentId = null, compressionResult = null, exportCompleted = false, error = null, message = null)
        }
    }

    fun extract(id: Long, pages: String, name: String, password: String?): Boolean {
        if (state.value.busy || state.value.awaitingSaveOperationId != null) return false
        val permit = adAccess?.acquire(RewardedTool.EXTRACT_PAGES, UUID.randomUUID().toString())
        if (adAccess != null && permit == null) return false
        toolPermit = permit
        runOperation { request ->
            var saved = false
            try {
                val document = document(id)
                validateName(name)
                create(name, document.tagId, request) {
                    repository.extractPages(document, PdfPageSelection.parse(pages, document.pageCount), password)
                }
                saved = true
            } finally {
                finishToolPermit(permit, success = saved)
            }
        }
        return true
    }

    fun createFromImages(uris: List<String>, name: String, idCard: Boolean) = runOperation { request ->
        validateName(name)
        create(name, null, request) { repository.imagesToPdf(uris, idCard) }
    }

    fun exportImages(
        id: Long, pages: String, format: PdfImageFormat, target: String, password: String?,
        adUnavailable: Boolean = false,
    ): Boolean {
        if (state.value.busy || state.value.awaitingSaveOperationId != null) return false
        val permit = adAccess?.acquire(
            RewardedTool.PDF_TO_IMAGES, UUID.randomUUID().toString(), adUnavailable,
        )
        if (adAccess != null && permit == null) return false
        toolPermit = permit
        runOperation { request ->
            var exported = false
            try {
                val document = document(id)
                repository.exportImages(
                    document, PdfPageSelection.parse(pages, document.pageCount), format, target, password,
                )
                currentCoroutineContext().ensureActive()
                exported = request == operationGeneration
                if (exported) state.update {
                    it.copy(message = "Imagens exportadas em um arquivo ZIP.", exportCompleted = true)
                }
            } finally {
                finishToolPermit(permit, success = exported)
            }
        }
        return true
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
        if (state.value.busy || state.value.awaitingSaveOperationId != null) return
        val request = operationGeneration
        state.update { it.copy(busy = true, error = null, message = null) }
        var started = false
        val permitAtStart = toolPermit
        val job = viewModelScope.launch {
            started = true
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
        operation = job
        job.invokeOnCompletion {
            // Cancellation before dispatch never enters the coroutine's try/finally.
            if (!started && request == operationGeneration && operation === job) {
                finishToolPermit(permitAtStart, success = false)
                state.update { it.copy(busy = false) }
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
