package com.joseleandro.scaniqo.ui.documentEditor.state

import com.joseleandro.scaniqo.domain.model.PdfPagePreview

data class DocumentToolPreviewState(
    val preview: PdfPagePreview? = null,
    val live: DocumentLivePreviewState = DocumentLivePreviewState(),
    val isRendering: Boolean = false,
    val error: Boolean = false,
    val showingOriginal: Boolean = false,
    val pageId: String? = null,
)
