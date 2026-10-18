package com.joseleandro.scaniqo.ui.documentEditor.state

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState

/** Dados necessários para visualizar um documento e escolher PDFs para mesclar. */
data class DocumentViewerUiState(
    val document: DocumentModel? = null,
    val availableDocuments: List<DocumentModel> = emptyList(),
    val actions: DocumentActionsUiState = DocumentActionsUiState(),
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
)
