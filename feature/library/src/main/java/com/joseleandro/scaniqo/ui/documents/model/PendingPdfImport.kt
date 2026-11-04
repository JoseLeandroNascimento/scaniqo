package com.joseleandro.scaniqo.ui.documents.model

import com.joseleandro.scaniqo.domain.model.ScannedDocument

data class PendingPdfImport(
    val scan: ScannedDocument,
    val name: String,
)
