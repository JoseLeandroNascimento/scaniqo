package com.joseleandro.scaniqo.ui.documents.state

import com.joseleandro.scaniqo.ui.documents.model.PendingPdfImport
import com.joseleandro.scaniqo.ui.documents.model.PdfImportDestination

data class PdfImportUiState(
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val needsPassword: Boolean = false,
    val needsUnlock: Boolean = false,
    val error: String? = null,
    val pending: PendingPdfImport? = null,
    val destination: PdfImportDestination? = null,
    val importedDocumentId: Long? = null,
    val isSavingToLibrary: Boolean = false,
)
