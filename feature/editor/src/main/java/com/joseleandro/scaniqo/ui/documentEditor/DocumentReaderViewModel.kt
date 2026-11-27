package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.isDocumentPasswordKey
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
                if (state.value.document?.isProtected == true &&
                    !(state.value.isLoading && state.value.needsPassword) &&
                    !passwords.isPasswordAvailable(recoveredKeyId ?: state.value.document?.protectionKeyId)) {
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
        state.value.document?.let { open(it) }
    }

    fun requestPassword() {
        state.update { it.copy(needsPassword = true, passwordIncorrect = false) }
    }

    fun cancelPassword() {
        if (!state.value.isLoading) state.update { it.copy(needsPassword = false, passwordIncorrect = false) }
    }

    fun submitPassword(password: String) {
        if (state.value.isLoading || password.isEmpty()) return
        state.value.document?.let { open(it, password) }
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
            it.copy(readable = null, isLoading = false, isLocked = true,
                recoveryRequired = recoveryRequired || isDocumentPasswordKey(it.document?.protectionKeyId) ||
                    !passwords.state.value.isConfigured)
        }
    }

    private fun open(document: DocumentModel, suppliedPassword: String? = null) {
        val previous = readingJob
        previous?.cancel()
        val request = ++revision
        if (suppliedPassword == null && document.isProtected &&
            !passwords.isPasswordAvailable(recoveredKeyId ?: document.protectionKeyId)) {
            state.value = DocumentReaderUiState(
                document = document,
                isLocked = true,
                recoveryRequired = isDocumentPasswordKey(document.protectionKeyId) ||
                    !passwords.state.value.isConfigured || passwords.state.value.recoveryRequired || state.value.recoveryRequired,
                needsPassword = isDocumentPasswordKey(document.protectionKeyId),
            )
            return
        }
        state.value = DocumentReaderUiState(document = document, isLoading = true, needsPassword = suppliedPassword != null)
        readingJob = viewModelScope.launch {
            var readable: ReadableDocument? = null
            try {
                previous?.cancelAndJoin()
                readable = if (suppliedPassword != null) repository.open(document, suppliedPassword)
                else if (document.isProtected) {
                    passwords.withPassword(recoveredKeyId ?: document.protectionKeyId) { password ->
                        repository.open(document, password)
                    }
                } else repository.open(document)
                currentCoroutineContext().ensureActive()
                if (request != revision) return@launch
                if (suppliedPassword != null) {
                    recoveredKeyId = passwords.rememberDocumentPassword(suppliedPassword, document.protectionKeyId)
                } else if (document.isProtected &&
                    !passwords.isPasswordAvailable(recoveredKeyId ?: document.protectionKeyId)) return@launch
                state.update {
                    it.copy(readable = readable, isLoading = false, isLocked = false, loadError = false,
                        needsPassword = false, passwordIncorrect = false)
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
                            needsPassword = recovery || suppliedPassword != null,
                            passwordIncorrect = suppliedPassword != null && failure is PdfPasswordRequiredException,
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
