package com.joseleandro.scaniqo.ui.documentEditor.coordinator

import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultRecoveryException
import com.joseleandro.scaniqo.core.security.PasswordVersionMissingException
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfMergeLimitException
import com.joseleandro.scaniqo.domain.model.PdfMergePasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.uniqueMergeItems
import com.joseleandro.scaniqo.ui.documentEditor.effect.DocumentToolsEffect
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentToolsMessage
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentMergeUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Dados da sessão consultados novamente antes de iniciar e publicar uma operação. */
internal data class DocumentMergeContext(
    val document: DocumentModel?,
    val workspace: PdfWorkspace?,
    val session: Long,
    val isBusy: Boolean,
    val isLocked: Boolean,
    val hasSignatureDraft: Boolean,
    val requiresAuthentication: Boolean,
)

/**
 * Coordena seleção, autenticação e mesclagem sem depender do ciclo de vida Android.
 * A sessão e a revisão impedem que resultados cancelados alterem outro documento.
 */
internal class DocumentMergeCoordinator(
    private val scope: CoroutineScope,
    private val passwords: PasswordAccess,
    private val ioMutex: Mutex,
    private val context: () -> DocumentMergeContext,
    private val append: suspend (PdfWorkspace, List<PdfMergeSource>) -> PdfWorkspace,
    private val appendOrdered: suspend (PdfWorkspace, List<PdfMergeSource>, Int) -> PdfWorkspace,
    private val onStateChanged: (DocumentMergeUiState) -> Unit,
    private val onEffect: (DocumentToolsEffect) -> Unit,
    private val onMessage: (DocumentToolsMessage) -> Unit,
    private val onWorkStarted: () -> Unit,
    private val onMerged: (previous: PdfWorkspace, updated: PdfWorkspace, firstAddedPageId: String) -> Unit,
    private val onWorkFinished: () -> Unit,
) {
    var state: DocumentMergeUiState = DocumentMergeUiState.Idle
        private set

    private var job: Job? = null
    private var revision = 0L
    private var pendingPicker = false
    private var insertionIndex: Int? = null
    private var currentDocumentIndex: Int? = null
    // Credenciais externas pertencem somente à operação e nunca ao UiState.
    private var sources: List<PdfMergeSource>? = null
    private var protectionKeys: Map<String, String> = emptyMap()
    private var documentNames: Map<String, String> = emptyMap()

    val hasProtectedSources: Boolean
        get() = protectionKeys.isNotEmpty()
    val hasSensitiveData: Boolean
        get() = state is DocumentMergeUiState.PasswordRequired || hasProtectedSources ||
            sources?.any { it.password != null } == true

    fun mergeDocuments(documents: List<DocumentModel>, targetIndex: Int) =
        mergeItems(documents.map(DocumentModel::toMergeItem), targetIndex)

    fun mergeItems(items: List<DocumentMergeItem>, targetIndex: Int) {
        val current = context()
        val workspace = current.workspace ?: return
        if (current.isBusy || current.hasSignatureDraft) return
        if (targetIndex !in 0..workspace.pages.size) {
            clear()
            onMessage(DocumentToolsMessage.MERGE_ERROR)
            return
        }
        val selected = uniqueMergeItems(items.filter {
            (it.documentId == null || it.documentId != current.document?.id) && it.uri != current.document?.pdfUri
        })
        if (selected.isEmpty()) return
        beginSelection(current, selected, targetIndex)
    }

    /** O item atual apenas marca a posição do workspace; nunca é importado outra vez. */
    fun mergeOrderedItems(items: List<DocumentMergeItem>) {
        val current = context()
        current.workspace ?: return
        val document = current.document ?: return
        if (current.isBusy || current.hasSignatureDraft) return
        val selected = uniqueMergeItems(items)
        fun isCurrent(item: DocumentMergeItem): Boolean =
            item.documentId == document.id || item.uri == document.pdfUri
        val currentItems = selected.withIndex().filter { isCurrent(it.value) }
        if (currentItems.size != 1 || selected.size < 2 ||
            selected.any { it.isCurrentDocument && !isCurrent(it) }
        ) {
            onMessage(DocumentToolsMessage.MERGE_ERROR)
            return
        }
        val index = currentItems.single().index
        beginSelection(current, selected.filterNot(::isCurrent), targetIndex = 0, currentIndex = index)
    }

    private fun beginSelection(
        current: DocumentMergeContext,
        selected: List<DocumentMergeItem>,
        targetIndex: Int,
        currentIndex: Int? = null,
    ) {
        clear()
        insertionIndex = targetIndex
        currentDocumentIndex = currentIndex
        sources = selected.map { PdfMergeSource(it.uri) }
        protectionKeys = selected.mapNotNull { item ->
            item.protectionKeyId?.let { item.uri to it }
        }.toMap()
        documentNames = selected.associate { it.uri to it.name }
        if (needsUnlock(current) || sourcesNeedUnlock()) requestUnlock() else appendSources()
    }

    fun launchPicker(targetIndex: Int) {
        val current = context()
        val workspace = current.workspace ?: return
        if (current.isBusy || current.hasSignatureDraft) return
        if (targetIndex !in 0..workspace.pages.size) {
            clear()
            onMessage(DocumentToolsMessage.MERGE_ERROR)
            return
        }
        protectionKeys = emptyMap()
        documentNames = emptyMap()
        insertionIndex = targetIndex
        currentDocumentIndex = null
        if (needsUnlock(current)) {
            pendingPicker = true
            requestUnlock()
            return
        }
        pendingPicker = false
        transition(DocumentMergeUiState.Picking)
        onEffect(DocumentToolsEffect.LaunchPdfMergePicker)
    }

    fun acceptSelection(uris: List<String>) {
        if (state != DocumentMergeUiState.Picking) return
        val current = context()
        val workspace = current.workspace
        val targetIndex = insertionIndex
        if (workspace == null || targetIndex == null || targetIndex !in 0..workspace.pages.size) {
            clear()
            onMessage(DocumentToolsMessage.MERGE_ERROR)
            return
        }
        transition(DocumentMergeUiState.Idle)
        val selected = uris.filter { it.isNotBlank() }.distinct().map { PdfMergeSource(it) }
        if (selected.isEmpty()) {
            clear()
            return
        }
        sources = selected
        if (needsUnlock(current)) requestUnlock() else appendSources()
    }

    fun submitPassword(password: String) {
        val prompt = state as? DocumentMergeUiState.PasswordRequired ?: return
        if (context().isLocked) return
        sources = sources?.map {
            if (it.uri == prompt.uri) it.copy(password = password) else it
        } ?: return
        transition(DocumentMergeUiState.Idle)
        appendSources()
    }

    /** Retorna true se uma seleção pendente consumiu a retomada da sessão. */
    fun resumeAfterUnlock(): Boolean {
        if (!passwords.state.value.isUnlocked) return false
        if (state == DocumentMergeUiState.AwaitingUnlock) transition(DocumentMergeUiState.Idle)
        return when {
            pendingPicker -> {
                insertionIndex?.let(::launchPicker)
                true
            }
            sources != null -> {
                appendSources()
                true
            }
            else -> false
        }
    }

    /** Mantém a seleção para retomar com novas credenciais, descartando senhas externas. */
    fun onWorkspaceLocked() {
        revision++
        job?.cancel()
        job = null
        sources = sources?.map { it.copy(password = null) }
        if (state != DocumentMergeUiState.Picking && state != DocumentMergeUiState.AwaitingUnlock) {
            transition(DocumentMergeUiState.Idle)
        }
    }

    fun clear() {
        revision++
        job?.cancel()
        job = null
        clearSelection()
        transition(DocumentMergeUiState.Idle)
    }

    private fun sourcesNeedUnlock(): Boolean = passwords.state.value.let {
        hasProtectedSources && it.isConfigured && !it.recoveryRequired && !it.isUnlocked
    }

    private fun needsUnlock(current: DocumentMergeContext): Boolean =
        current.isLocked || (current.requiresAuthentication && !passwords.state.value.isUnlocked)

    private fun requestUnlock() {
        transition(DocumentMergeUiState.AwaitingUnlock)
        onEffect(DocumentToolsEffect.NeedUnlock)
    }

    private fun appendSources() {
        val current = context()
        val workspace = current.workspace ?: return
        val selected = sources ?: return
        val targetIndex = insertionIndex
        if (current.isLocked || current.isBusy || current.hasSignatureDraft) return
        if (targetIndex == null || targetIndex !in 0..workspace.pages.size) {
            clear()
            onMessage(DocumentToolsMessage.MERGE_ERROR)
            return
        }
        onWorkStarted()
        val operationSession = current.session
        val operationRevision = ++revision
        val keys = protectionKeys
        val orderedIndex = currentDocumentIndex
        transition(DocumentMergeUiState.Merging)
        job = scope.launch {
            val attemptedPasswords = selected.filter { it.password != null }.mapTo(mutableSetOf()) { it.uri }
            try {
                val appended = ioMutex.withLock {
                    // Cópias com senhas resolvidas não escapam da tentativa.
                    val resolved = selected.map { source ->
                        val key = keys[source.uri]
                        if (key == null || source.password != null) source else {
                            if (!passwords.state.value.isConfigured || passwords.state.value.recoveryRequired) {
                                throw PdfMergePasswordRequiredException(source.uri)
                            }
                            try {
                                passwords.withPassword(key) { password ->
                                    attemptedPasswords += source.uri
                                    source.copy(password = password)
                                }
                            } catch (_: PasswordVersionMissingException) {
                                throw PdfMergePasswordRequiredException(source.uri)
                            } catch (_: PasswordVaultRecoveryException) {
                                throw PdfMergePasswordRequiredException(source.uri)
                            }
                        }
                    }
                    if (orderedIndex == null) append(workspace, resolved)
                    else appendOrdered(workspace, resolved, orderedIndex)
                }
                if (!isCurrent(operationSession, operationRevision)) return@launch
                val originalIds = workspace.pages.mapTo(mutableSetOf()) { it.id }
                val addedPages = appended.pages.filterNot { it.id in originalIds }
                check(addedPages.isNotEmpty())
                val updated = if (orderedIndex == null) {
                    check(appended.pages.take(workspace.pages.size) == workspace.pages)
                    appended.copy(
                        pages = workspace.pages.take(targetIndex) + addedPages + workspace.pages.drop(targetIndex),
                    )
                } else {
                    val currentStart = appended.pages.indexOfFirst { it.id == workspace.pages.first().id }
                    check(currentStart >= 0)
                    check(appended.pages.drop(currentStart).take(workspace.pages.size) == workspace.pages)
                    check(appended.pages.count { it.id in originalIds } == workspace.pages.size)
                    appended
                }
                clearSelection()
                onMerged(workspace, updated, addedPages.first().id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (required: PdfMergePasswordRequiredException) {
                if (isCurrent(operationSession, operationRevision)) {
                    protectionKeys = protectionKeys - required.uri
                    sources = selected.map {
                        if (it.uri == required.uri) it.copy(password = null) else it
                    }
                    transition(DocumentMergeUiState.PasswordRequired(
                        uri = required.uri,
                        name = documentNames[required.uri],
                        incorrect = required.uri in attemptedPasswords,
                    ))
                }
            } catch (_: PasswordVaultLockedException) {
                if (isCurrent(operationSession, operationRevision)) requestUnlock()
            } catch (failure: Exception) {
                if (isCurrent(operationSession, operationRevision)) {
                    clearSelection()
                    onMessage(if (failure is PdfMergeLimitException) DocumentToolsMessage.MERGE_LIMIT_ERROR else DocumentToolsMessage.MERGE_ERROR)
                }
            } finally {
                if (isCurrent(operationSession, operationRevision)) {
                    job = null
                    if (state == DocumentMergeUiState.Merging) transition(DocumentMergeUiState.Idle)
                    if (!context().isLocked) onWorkFinished()
                }
            }
        }
    }

    private fun isCurrent(operationSession: Long, operationRevision: Long): Boolean =
        context().session == operationSession && revision == operationRevision

    private fun transition(next: DocumentMergeUiState) {
        state = next
        onStateChanged(next)
    }

    private fun clearSelection() {
        pendingPicker = false
        sources = null
        insertionIndex = null
        currentDocumentIndex = null
        protectionKeys = emptyMap()
        documentNames = emptyMap()
    }
}
