package com.joseleandro.scaniqo.ui.documentEditor.state

import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels

data class DocumentLivePreviewState(
    val pageId: String? = null,
    val pixels: PdfInteractivePixels? = null,
    val isRendering: Boolean = false,
    val error: Boolean = false,
    val unsupported: Boolean = false,
)
