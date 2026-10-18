package com.joseleandro.scaniqo.ui.documentCommon.state

import com.joseleandro.scaniqo.domain.model.ScannedDocument

data class DocumentMetadataUiState(
    val documentId: Long? = null,
    val draft: ScannedDocument? = null,
    val name: String,
    val tagId: Long? = null,
    val isSaving: Boolean = false,
    val nameError: DocumentNameError? = null,
    val saveError: Boolean = false,
)

