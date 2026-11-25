package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultRecoveryException
import com.joseleandro.scaniqo.core.security.PasswordVersionMissingException
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfPasswordRequiredException
import com.joseleandro.scaniqo.domain.model.ReadableDocument
import com.joseleandro.scaniqo.domain.repository.DocumentReaderRepository
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentReaderUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A sessão possui apenas o PDF salvo; não prepara ferramentas nem altera o documento. */
class DocumentReaderViewModel(
    private val repository: DocumentReaderRepository,
    private val passwords: PasswordAccess,
) : ViewModel() {
    private val state = MutableStateFlow(DocumentReaderUiState())
    val uiState = state.asStateFlow()
    private var readingJob: Job? = null
    private var recoveredKeyId: String? = null
    private var revision = 0L
    private val defaultOwner = Any()
    private var activeOwner: Any = defaultOwner

    init {
        viewModelScope.launch {
            passwords.state.collect { vault ->
                if (!vault.isUnlocked && state.value.document?.isProtected == true) {
                    lock(vault.recoveryRequired || state.value.recoveryRequired)
                }
            }
        }
    }

    fun load(document: DocumentModel, owner: Any = defaultOwner) {
        activeOwner = owner
        val current = state.value
        val sameContent = current.document?.let {
            it.id == document.id && it.pdfUri == document.pdfUri &&
                it.sizeBytes == document.sizeBytes && it.pageCount == document.pageCount &&
                it.protectionKeyId == document.protectionKeyId
        } == true
        if (sameContent && (current.readable != null || current.isLoading)) {
            state.update { it.copy(document = document) }
            return
        }
        if (!sameContent) recoveredKeyId = null
        state.value = DocumentReaderUiState(
            document = document, recoveryRequired = sameContent && current.recoveryRequired,
        )
        open(document)
    }

    fun retry() {
        state.value.document?.let(::open)
    }

    /** A senha fica no cofre; somente a referência à versão recuperada pertence à sessão. */
    fun recover(keyId: String) {
        recoveredKeyId = keyId
        retry()
    }

    fun deactivate(documentId: Long, owner: Any = defaultOwner) {
        if (activeOwner !== owner || state.value.document?.id != documentId) return
        revision++
        readingJob?.cancel()
        state.update {
            it.copy(readable = null, isLoading = false, isLocked = it.document?.isProtected == true)
        }
    }

    fun background(owner: Any = defaultOwner) {
        if (activeOwner !== owner) return
        if (state.value.document?.isProtected == true) {
            lock(state.value.recoveryRequired)
            passwords.lock()
        }
    }

    private fun lock(recoveryRequired: Boolean) {
        revision++
        readingJob?.cancel()
        state.update {
            it.copy(readable = null, isLoading = false, isLocked = true, recoveryRequired = recoveryRequired)
        }
    }

    private fun open(document: DocumentModel) {
        val previous = readingJob
        previous?.cancel()
        val request = ++revision
        if (document.isProtected && !passwords.state.value.isUnlocked) {
            state.value = DocumentReaderUiState(
                document = document,
                isLocked = true,
                recoveryRequired = passwords.state.value.recoveryRequired || state.value.recoveryRequired,
            )
            return
        }
        state.value = DocumentReaderUiState(document = document, isLoading = true)
        readingJob = viewModelScope.launch {
            var readable: ReadableDocument? = null
            try {
                previous?.cancelAndJoin()
                readable = if (document.isProtected) {
                    passwords.withPassword(recoveredKeyId ?: document.protectionKeyId) { password ->
                        repository.open(document, password)
                    }
                } else repository.open(document)
                currentCoroutineContext().ensureActive()
                if (request != revision || (document.isProtected && !passwords.state.value.isUnlocked)) {
                    return@launch
                }
                state.update {
                    it.copy(readable = readable, isLoading = false, isLocked = false, loadError = false)
                }
                // A coroutine é a única proprietária do temporário, inclusive durante cancelamento.
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (request == revision) {
                    val recovery = failure is PasswordVaultRecoveryException ||
                        failure is PasswordVersionMissingException || failure is PdfPasswordRequiredException
                    val locked = recovery || failure is PasswordVaultLockedException
                    state.update {
                        it.copy(
                            readable = null,
                            isLoading = false,
                            isLocked = locked,
                            recoveryRequired = recovery,
                            loadError = !locked,
                        )
                    }
                }
            } finally {
                readable?.let { result ->
                    withContext(NonCancellable) { repository.close(result) }
                }
            }
        }
    }
}
