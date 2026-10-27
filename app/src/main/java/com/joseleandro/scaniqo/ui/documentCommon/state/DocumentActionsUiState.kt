package com.joseleandro.scaniqo.ui.documentCommon.state

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel

/** Estado das operações compartilhadas, mantido exclusivamente por DocumentActionsViewModel. */
data class DocumentActionsUiState(
    val tags: List<TagModel> = emptyList(),
    val isLoadingTags: Boolean = true,
    val tagsLoadError: Boolean = false,
    val pendingCreation: DocumentCreationResult? = null,
    val generatedResult: DocumentGeneratedResult? = null,
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
