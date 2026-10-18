package com.joseleandro.scaniqo.ui.documentCommon.state

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel

/** Projeção imutável das ações compartilhadas; não mantém uma segunda fonte de estado. */
data class DocumentActionsUiState(
    val tags: List<TagModel> = emptyList(),
    val editor: DocumentMetadataUiState? = null,
    val pendingDeletion: DocumentModel? = null,
    val isDeleting: Boolean = false,
    val busyDocumentId: Long? = null,
    val isScanning: Boolean = false,
    val isPreparingScan: Boolean = false,
    val message: DocumentMessage? = null,
) {
    val isBusy: Boolean
        get() = isScanning || isPreparingScan || isDeleting ||
            busyDocumentId != null || editor?.isSaving == true
}
