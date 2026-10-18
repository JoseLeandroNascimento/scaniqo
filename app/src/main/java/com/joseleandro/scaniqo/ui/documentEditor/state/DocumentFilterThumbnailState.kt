package com.joseleandro.scaniqo.ui.documentEditor.state

import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails

internal data class DocumentFilterThumbnailState(
    val pageId: String? = null,
    val result: PdfFilterThumbnails? = null,
    val isLoading: Boolean = false,
    val error: Boolean = false,
)
