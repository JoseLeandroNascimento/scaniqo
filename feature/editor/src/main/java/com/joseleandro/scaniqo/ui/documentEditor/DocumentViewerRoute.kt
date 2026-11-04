package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.ads.rememberToolAdAction

/** O visualizador carrega seus dados e compartilha as ações sem depender da lista. */
@Composable
fun DocumentViewerRoute(
    documentId: Long,
    viewModel: DocumentViewerViewModel,
    actionsViewModel: DocumentActionsViewModel,
    toolsViewModel: DocumentToolsViewModel,
    onNavigationBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPageNumber: Int? = null,
    banner: @Composable () -> Unit = {},
    isActive: Boolean = true,
    saveAdOrigin: RewardedTool? = null,
) {
    val viewerState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionsState by actionsViewModel.uiState.collectAsStateWithLifecycle()
    val toolsState by toolsViewModel.uiState.collectAsStateWithLifecycle()
    // Ao trocar a rota, o frame anterior não pode apresentar outro documento.
    val document = viewerState.document?.takeIf { it.id == documentId }
    LaunchedEffect(documentId, isActive) {
        if (isActive) viewModel.load(documentId)
    }
    LaunchedEffect(document?.id, document?.pdfUri, isActive, saveAdOrigin) {
        if (isActive && document != null) {
            toolsViewModel.load(document)
            toolsViewModel.setSaveAdOrigin(documentId, saveAdOrigin)
        }
    }
    val sameOrigin = toolsState.saveAdOrigin == saveAdOrigin
    val saveTool = if (sameOrigin) toolsViewModel.pendingRewardedSaveTool() else null
    val saveAction = rememberToolAdAction(
        tool = saveTool,
        contextKey = listOf(documentId, toolsState.workspace?.workspaceId, saveAdOrigin),
        active = isActive && sameOrigin && document != null && toolsState.document?.id == documentId && toolsState.canEdit &&
            toolsState.hasChanges && toolsState.signatureDraft == null && !actionsState.isBusy &&
            actionsState.editor == null && actionsState.pendingDeletion == null,
        onApply = { adUnavailable ->
            val current = toolsViewModel.uiState.value
            if (isActive && current.document?.id == documentId && current.saveAdOrigin == saveAdOrigin &&
                current.canEdit && current.signatureDraft == null && toolsViewModel.pendingRewardedSaveTool() == saveTool
            ) toolsViewModel.saveWithAd(adUnavailable)
        },
    )
    DocumentViewerScreen(
        documentId = documentId,
        initialPageNumber = initialPageNumber,
        uiState = viewerState.copy(
            document = document,
            actions = actionsState,
            isLoading = viewerState.isLoading || viewerState.requestedDocumentId != documentId,
            loadError = viewerState.loadError && viewerState.requestedDocumentId == documentId,
        ),
        onDocumentAction = actionsViewModel::onDocumentAction,
        onRetryLoad = {
            viewModel.retry()
            actionsViewModel.retryLoadingTags()
        },
        toolsState = toolsState,
        onToolsEvent = toolsViewModel::onEvent,
        onNavigationBack = onNavigationBack,
        modifier = modifier,
        banner = banner,
        onSave = saveAction.onClick,
        saveShowsVideo = saveAction.showsVideo,
        saveInProgress = saveAction.inProgress,
    )
}
