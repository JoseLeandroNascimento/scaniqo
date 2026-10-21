package com.joseleandro.scaniqo.ui.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.domain.model.PdfPasswordRequiredException
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.ui.documents.effect.PdfImportEffect
import com.joseleandro.scaniqo.ui.documents.model.PendingPdfImport
import com.joseleandro.scaniqo.ui.documents.model.PdfImportDestination
import com.joseleandro.scaniqo.ui.documents.state.PdfImportUiState
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
import kotlinx.coroutines.withContext

class PdfImportViewModel(
    private val repository: PdfEditorRepository,
    private val documents: DocumentRepository,
    private val vault: PasswordVault,
    private val savedState: SavedStateHandle,
    private val defaultName: () -> String = { "Imported document" },
) : ViewModel() {
    private val restored = restorePending()
    private val restoredDestination = savedState.get<String>("tool_destination")?.let { name ->
        PdfImportDestination.entries.firstOrNull { it.name == name }
    }
    private val state = MutableStateFlow(PdfImportUiState(
        isActive = restored != null || savedState.get<Boolean>("awaiting_picker") == true ||
            savedState.get<Long>("tool_document_id") != null,
        pending = restored,
        destination = restoredDestination,
        importedDocumentId = savedState["tool_document_id"],
    ))
    val uiState = state.asStateFlow()
    private val channel = Channel<PdfImportEffect>(Channel.UNLIMITED)
    val effects = channel.receiveAsFlow()
    private var uri: String? = null
    private var name = defaultName()
    private var pendingPassword: String? = null
    private var job: Job? = null
    private var generation = 0

    fun chooseFile(destination: PdfImportDestination? = null) {
        if (state.value.isActive) return
        savedState["awaiting_picker"] = true
        savedState["tool_destination"] = destination?.name
        savedState.remove<Long>("tool_document_id")
        state.value = PdfImportUiState(isActive = true, destination = destination)
        channel.trySend(PdfImportEffect.PickFile)
    }

    /** O cadastro assume a cópia antes da navegação; uma retomada usa a URI idempotente do rascunho. */
    fun saveForTool() {
        val current = state.value
        val pending = current.pending ?: return
        val destination = current.destination ?: return
        if (current.isLoading || current.importedDocumentId != null || current.error != null) return
        state.update { it.copy(isLoading = true, isSavingToLibrary = true) }
        job = viewModelScope.launch {
            try {
                // Depois de iniciar o cadastro, finalizamos a entrega para não deixar um PDF órfão.
                withContext(NonCancellable) {
                    val id = documents.create(pending.scan, pending.name, null)
                    savedState["tool_document_id"] = id
                    clearPending()
                    state.value = PdfImportUiState(
                        isActive = true, destination = destination, importedDocumentId = id,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state.update { it.copy(
                    isLoading = false, isSavingToLibrary = false,
                    error = failure.message ?: "Não foi possível guardar o PDF importado.",
                ) }
            }
        }
    }

    fun acknowledgeToolDelivery(documentId: Long) {
        if (state.value.importedDocumentId != documentId) return
        savedState.remove<Long>("tool_document_id")
        savedState.remove<String>("tool_destination")
        uri = null
        pendingPassword = null
        state.value = PdfImportUiState()
    }

    fun fileSelected(uri: String?, filename: suspend () -> String = { defaultName() }) {
        if (!state.value.isActive || state.value.isLoading || state.value.pending != null ||
            state.value.importedDocumentId != null) return
        savedState["awaiting_picker"] = false
        if (uri == null) { cancel(); return }
        this.uri = uri
        import(resolveName = filename)
    }

    fun submitPassword(password: String) {
        if (state.value.isLoading || !state.value.needsPassword) return
        if (password.isEmpty()) {
            state.update { it.copy(error = "Informe a senha deste PDF.") }
            return
        }
        if (!vault.state.value.isConfigured) {
            state.update { it.copy(error = "Configure a senha geral para guardar a senha deste PDF com segurança.") }
            return
        }
        pendingPassword = password
        if (!vault.state.value.isUnlocked) state.update { it.copy(needsUnlock = true) }
        else import(password)
    }

    fun unlocked() {
        state.update { it.copy(needsUnlock = false) }
        pendingPassword?.let { import(it) }
    }

    fun unlockDismissed() {
        pendingPassword = null
        state.update { it.copy(needsUnlock = false) }
    }

    /** A entrega só termina depois que o cadastro assumiu e persistiu o rascunho. */
    fun completeDelivery(pdfUri: String) {
        if (state.value.destination != null || state.value.pending?.scan?.pdfUri != pdfUri) return
        clearPending()
        uri = null
        pendingPassword = null
        state.value = PdfImportUiState()
    }

    fun cancel() {
        if (state.value.isSavingToLibrary) return
        generation++
        job?.cancel()
        val pending = state.value.pending
        pendingPassword = null
        uri = null
        savedState["awaiting_picker"] = false
        savedState.remove<String>("tool_destination")
        savedState.remove<Long>("tool_document_id")
        clearPending()
        state.value = PdfImportUiState()
        if (pending != null) viewModelScope.launch {
            withContext(NonCancellable) { runCatching { documents.discardScan(pending.scan) } }
        }
        channel.trySend(PdfImportEffect.Cancelled)
    }

    private fun import(password: String? = null, resolveName: (suspend () -> String)? = null) {
        val source = uri ?: return
        val request = ++generation
        state.update { it.copy(isLoading = true, error = null, needsUnlock = false) }
        job = viewModelScope.launch {
            var prepared: ScannedDocument? = null
            try {
                if (resolveName != null) {
                    name = resolveName().trim().let { if (it.endsWith(".pdf", true)) it.dropLast(4) else it }
                        .ifBlank { defaultName() }.take(100)
                }
                currentCoroutineContext().ensureActive()
                val keyId = password?.let { vault.rememberDocumentPassword(it) }
                prepared = repository.importPdf(source, password, keyId)
                currentCoroutineContext().ensureActive()
                if (request != generation) return@launch
                val pending = PendingPdfImport(prepared, name)
                persistPending(pending)
                state.update { it.copy(pending = pending, needsPassword = false) }
                prepared = null
                pendingPassword = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (request == generation) {
                    if (error is PdfPasswordRequiredException) {
                        state.update { it.copy(needsPassword = true, error = if (password == null) null else "Senha incorreta. Tente novamente.") }
                    } else state.update { it.copy(error = error.message ?: "Não foi possível importar o PDF.") }
                }
            } finally {
                prepared?.let { scan ->
                    withContext(NonCancellable) { runCatching { documents.discardScan(scan) } }
                }
                if (request == generation) {
                    pendingPassword = null
                    state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun persistPending(pending: PendingPdfImport) {
        savedState["pending_name"] = pending.name
        savedState["pending_count"] = pending.scan.pageCount
        savedState["pending_date"] = pending.scan.createdAt
        savedState["pending_size"] = pending.scan.sizeBytes
        savedState["pending_thumbnail"] = pending.scan.thumbnailUri
        savedState["pending_key"] = pending.scan.protectionKeyId
        savedState["pending_pdf"] = pending.scan.pdfUri
    }

    private fun restorePending(): PendingPdfImport? {
        val pdf = savedState.get<String>("pending_pdf") ?: return null
        return PendingPdfImport(
            ScannedDocument(
                pdfUri = pdf,
                pageCount = savedState["pending_count"] ?: return null,
                createdAt = savedState["pending_date"] ?: return null,
                sizeBytes = savedState["pending_size"] ?: return null,
                thumbnailUri = savedState["pending_thumbnail"],
                protectionKeyId = savedState["pending_key"],
            ),
            savedState["pending_name"] ?: defaultName(),
        )
    }

    private fun clearPending() {
        listOf("pending_pdf", "pending_name", "pending_count", "pending_date", "pending_size", "pending_thumbnail", "pending_key")
            .forEach { savedState.remove<Any?>(it) }
    }
}
