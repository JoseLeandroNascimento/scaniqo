package com.joseleandro.scaniqo.ui.documentEditor.state

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ReadableDocument

data class DocumentReaderUiState(
    val document: DocumentModel? = null,
    val readable: ReadableDocument? = null,
    val isLoading: Boolean = false,
    val isLocked: Boolean = false,
    val recoveryRequired: Boolean = false,
    val loadError: Boolean = false,
)
