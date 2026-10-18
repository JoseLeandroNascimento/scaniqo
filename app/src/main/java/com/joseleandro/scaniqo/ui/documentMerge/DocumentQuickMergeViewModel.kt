package com.joseleandro.scaniqo.ui.documentMerge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultRecoveryException
import com.joseleandro.scaniqo.core.security.PasswordVersionMissingException
import com.joseleandro.scaniqo.domain.model.PdfMergePasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentMerge.state.DocumentQuickMergeUiState
import com.joseleandro.scaniqo.ui.documentMerge.state.MergePasswordRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** O lote produz um novo rascunho; a confirmação de nome/tag pertence às ações compartilhadas. */
class DocumentQuickMergeViewModel(
    private val repository: PdfEditorRepository,
    private val documents: DocumentRepository,
    private val passwords: PasswordAccess,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restored = restorePending()
    private val state = MutableStateFlow(DocumentQuickMergeUiState(
        isActive = restored != null, pending = restored,
    ))
    val uiState = state.asStateFlow()
    private val cancelledChannel = Channel<Unit>(Channel.UNLIMITED)
    val cancellations = cancelledChannel.receiveAsFlow()
    private var items = emptyList<DocumentMergeItem>()
    // Credenciais ficam somente em memória e são apagadas ao sair do aplicativo.
    private val suppliedPasswords = mutableMapOf<String, String>()
    private val manualPasswordUris = mutableSetOf<String>()
    private var generation = 0L
    private var job: Job? = null
    private val operationMutex = Mutex()

    fun merge(selection: List<DocumentMergeItem>): Boolean {
        if (state.value.isActive) return false
        val selected = selection.filter { it.uri.isNotBlank() }.distinctBy { it.uri }
        if (selected.size < 2) return false
        items = selected
        suppliedPasswords.clear()
        manualPasswordUris.clear()
        state.value = DocumentQuickMergeUiState(isActive = true)
        attempt()
        return true
    }

    fun retry() {
        if (state.value.isActive && !state.value.isWorking && state.value.pending == null) attempt()
    }

    fun submitPassword(password: String) {
        val prompt = state.value.passwordRequest ?: return
        if (state.value.isWorking) return
        if (password.isEmpty()) {
            state.update { it.copy(error = "Informe a senha do PDF.") }
            return
        }
        suppliedPasswords[prompt.uri] = password
        manualPasswordUris += prompt.uri
        attempt()
    }

    fun unlocked() {
        if (state.value.needsUnlock && passwords.state.value.isUnlocked) attempt()
    }

    fun unlockDismissed() {
        state.update { it.copy(needsUnlock = false, error = "Desbloqueie para acessar os PDFs protegidos.") }
    }

    fun backgrounded() {
        if (!state.value.isActive || state.value.pending != null) return
        generation++
        job?.cancel()
        suppliedPasswords.clear()
        if (items.any { it.protectionKeyId != null }) passwords.lock()
        state.update { it.copy(
            isWorking = false, needsUnlock = false, passwordRequest = null,
            error = "A mesclagem foi pausada. Toque em tentar novamente para continuar.",
        ) }
    }

    /** Só libera a referência depois que DocumentActionsViewModel persistiu o rascunho. */
    fun completeDelivery(uri: String) {
        if (state.value.pending?.pdfUri != uri) return
        clearPending()
        items = emptyList()
        suppliedPasswords.clear()
        manualPasswordUris.clear()
        state.value = DocumentQuickMergeUiState()
    }

    fun cancel() {
        generation++
        job?.cancel()
        val pending = state.value.pending
        clearPending()
        suppliedPasswords.clear()
        manualPasswordUris.clear()
        items = emptyList()
        state.value = DocumentQuickMergeUiState()
        if (pending != null) viewModelScope.launch {
            withContext(NonCancellable) { runCatching { documents.discardScan(pending) } }
        }
        cancelledChannel.trySend(Unit)
    }

    private fun attempt() {
        if (items.size < 2 || state.value.isWorking || state.value.pending != null) return
        val selected = items
        val request = ++generation
        val credentials = suppliedPasswords.toMap()
        val manualUris = manualPasswordUris.toSet()
        state.update { it.copy(isWorking = true, error = null, needsUnlock = false, passwordRequest = null) }
        job = viewModelScope.launch {
            var prepared: ScannedDocument? = null
            val attempted = credentials.keys.toMutableSet()
            try {
                prepared = operationMutex.withLock {
                    val sources = selected.map { item ->
                        val supplied = credentials[item.uri]
                        val key = item.protectionKeyId
                        val password = when {
                            supplied != null -> supplied
                            item.uri in manualUris -> throw PdfMergePasswordRequiredException(item.uri)
                            key == null -> null
                            !passwords.state.value.isConfigured || passwords.state.value.recoveryRequired ->
                                throw PdfMergePasswordRequiredException(item.uri)
                            else -> try {
                                passwords.withPassword(key) { it }
                            } catch (_: PasswordVersionMissingException) {
                                throw PdfMergePasswordRequiredException(item.uri)
                            } catch (_: PasswordVaultRecoveryException) {
                                throw PdfMergePasswordRequiredException(item.uri)
                            }
                        }
                        if (password != null) attempted += item.uri
                        PdfMergeSource(item.uri, password)
                    }
                    repository.mergePdfs(sources)
                }
                currentCoroutineContext().ensureActive()
                if (request != generation) return@launch
                val result = checkNotNull(prepared)
                persistPending(result)
                state.update { it.copy(pending = result) }
                prepared = null
                suppliedPasswords.clear()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: PasswordVaultLockedException) {
                if (request == generation) state.update { it.copy(needsUnlock = true) }
            } catch (required: PdfMergePasswordRequiredException) {
                if (request == generation) {
                    suppliedPasswords.remove(required.uri)
                    manualPasswordUris += required.uri
                    state.update { it.copy(passwordRequest = MergePasswordRequest(
                        required.uri,
                        selected.firstOrNull { item -> item.uri == required.uri }?.name ?: "PDF selecionado",
                        incorrect = required.uri in attempted,
                    )) }
                }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (request == generation) state.update { it.copy(error = failure.message ?: "Não foi possível mesclar os PDFs.") }
            } finally {
                prepared?.let { scan ->
                    withContext(NonCancellable) { runCatching { documents.discardScan(scan) } }
                }
                if (request == generation) state.update { it.copy(isWorking = false) }
            }
        }
    }

    private fun persistPending(scan: ScannedDocument) {
        savedState["merge.count"] = scan.pageCount
        savedState["merge.date"] = scan.createdAt
        savedState["merge.size"] = scan.sizeBytes
        savedState["merge.thumbnail"] = scan.thumbnailUri
        savedState["merge.pdf"] = scan.pdfUri
    }

    private fun restorePending(): ScannedDocument? {
        val uri = savedState.get<String>("merge.pdf") ?: return null
        return ScannedDocument(
            pdfUri = uri,
            pageCount = savedState["merge.count"] ?: return null,
            createdAt = savedState["merge.date"] ?: return null,
            sizeBytes = savedState["merge.size"] ?: return null,
            thumbnailUri = savedState["merge.thumbnail"],
        )
    }

    private fun clearPending() {
        listOf("merge.pdf", "merge.count", "merge.date", "merge.size", "merge.thumbnail")
            .forEach { savedState.remove<Any?>(it) }
    }
}
