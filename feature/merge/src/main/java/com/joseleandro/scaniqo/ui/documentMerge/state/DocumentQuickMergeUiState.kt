package com.joseleandro.scaniqo.ui.documentMerge.state

import com.joseleandro.scaniqo.domain.model.ScannedDocument

data class MergePasswordRequest(val uri: String, val name: String, val incorrect: Boolean = false)

data class DocumentQuickMergeUiState(
    val isActive: Boolean = false,
    val isWorking: Boolean = false,
    val needsUnlock: Boolean = false,
    val passwordRequest: MergePasswordRequest? = null,
    val error: String? = null,
    val pending: ScannedDocument? = null,
    val savedDocumentId: Long? = null,
)
