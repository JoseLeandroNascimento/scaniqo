package com.joseleandro.scaniqo.ui.documentMerge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentToolsMessages
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentToolsMessage
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentEditor.DocumentToolsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerViewModel
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentMerge.model.DocumentMergeTarget
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState

/** Adapta as duas origens à mesma seleção, ordenação e tela de mesclagem. */
@Composable
fun DocumentMergeDestination(
    documentId: Long?,
    documentsState: DocumentsUiState,
    canStart: Boolean,
    viewerViewModel: DocumentViewerViewModel,
    toolsViewModel: DocumentToolsViewModel,
    actionsViewModel: DocumentActionsViewModel,
    mergeViewModel: DocumentQuickMergeViewModel,
    isActive: Boolean,
    onRetryDocuments: () -> Unit,
    onBack: () -> Unit,
    onOpenDocument: (Long) -> Unit,
) {
    val viewer by viewerViewModel.uiState.collectAsStateWithLifecycle()
    val tools by toolsViewModel.uiState.collectAsStateWithLifecycle()
    val quickMerge by mergeViewModel.uiState.collectAsStateWithLifecycle()
    val document = viewer.document?.takeIf { it.id == documentId }
    val sameSession = documentId != null && tools.document?.id == documentId
    val toastState = rememberAppToastState()
    if (isActive && sameSession && tools.message != DocumentToolsMessage.MERGED) {
        DocumentToolsMessages(tools, toolsViewModel::onEvent, toastState)
    }
    LaunchedEffect(documentId) { documentId?.let(viewerViewModel::load) }
    LaunchedEffect(document?.id, document?.pdfUri) { document?.let(toolsViewModel::load) }
    SecureWindow(enabled = documentId != null &&
        (document?.isProtected == true || (sameSession && tools.workspace?.isProtected == true)))
    DocumentMergeRoute(
        documents = if (documentId == null) documentsState.documents else viewer.availableDocuments,
        target = documentId?.let {
            DocumentMergeTarget(
                documentId = it,
                pageCount = if (sameSession) tools.workspace?.pages?.size ?: 0 else 0,
                selectedPageIndex = if (sameSession) tools.selectedPageIndex else 0,
                currentDocument = document?.toMergeItem()?.copy(
                    pageCount = if (sameSession) tools.workspace?.pages?.size ?: document.pageCount else document.pageCount,
                    isCurrentDocument = true,
                ),
                isProtected = document?.isProtected == true || (sameSession && tools.workspace?.isProtected == true),
            )
        },
        enabled = if (documentId == null) canStart && !quickMerge.isActive && quickMerge.savedDocumentId == null
            else sameSession && tools.canEdit,
        savedDocumentId = if (documentId == null) quickMerge.savedDocumentId else null,
        isActive = isActive,
        canNavigateBack = if (documentId == null) !quickMerge.isActive else !sameSession || !tools.isBusy,
        isLoading = if (documentId == null) documentsState.isLoading else
            viewer.isLoading || viewer.requestedDocumentId != documentId || (sameSession && tools.isLoading),
        loadError = if (documentId == null) documentsState.loadError else
            viewer.loadError || (sameSession && tools.loadError) ||
                (!viewer.isLoading && viewer.requestedDocumentId == documentId && document == null),
        snackbarHost = { AppToastHost(toastState) },
        onUnlock = if (isActive && sameSession && tools.isLocked) {
            { toolsViewModel.onEvent(if (tools.recoveryRequired)
                DocumentToolsEvent.RequestPasswordRecovery else DocumentToolsEvent.Unlock) }
        } else null,
        onRetry = {
            if (documentId == null) onRetryDocuments() else {
                viewerViewModel.retry()
                toolsViewModel.onEvent(DocumentToolsEvent.Retry)
            }
        },
        onBack = {
            if (documentId == null) mergeViewModel.clearSavedDocument()
            onBack()
        },
        onOpenDocument = { id ->
            if (documentId == null && id == quickMerge.savedDocumentId) onOpenDocument(id)
        },
        onConfirm = { items, adUnavailable ->
            if (isActive) {
                if (documentId == null) {
                    if (canStart) {
                        actionsViewModel.onDocumentAction(DocumentActionEvent.ImportStarted(documentsState.selectedTagId))
                        if (!actionsViewModel.uiState.value.isPreparingScan || !mergeViewModel.merge(items, adUnavailable)) {
                            actionsViewModel.onDocumentAction(DocumentActionEvent.ImportCancelled)
                        }
                    }
                } else if (toolsViewModel.uiState.value.document?.id == documentId) {
                    toolsViewModel.onEvent(DocumentToolsEvent.MergeOrderedItems(items))
                }
            }
        },
    )
}
