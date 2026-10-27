package com.joseleandro.scaniqo.ui.documentEditor.state

import com.joseleandro.scaniqo.domain.model.PdfPagePreview

data class DocumentPagePreviewState(
    val preview: PdfPagePreview? = null,
    val isRendering: Boolean = false,
    val error: Boolean = false,
    val showingOriginal: Boolean = false,
    val renderedOverlayIds: Set<String>? = null,
)
