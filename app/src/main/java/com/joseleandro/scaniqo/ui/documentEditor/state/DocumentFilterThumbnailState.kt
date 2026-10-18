package com.joseleandro.scaniqo.ui.documentEditor.state

import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails
import com.joseleandro.scaniqo.domain.model.PdfImageFilter

data class DocumentFilterThumbnailState(
    val pageId: String? = null,
    val result: PdfFilterThumbnails? = null,
    val isLoading: Boolean = false,
    val error: Boolean = false,
) {
    val images: Map<PdfImageFilter, String>
        get() = result?.images.orEmpty()
    val requiresRasterization: Boolean
        get() = result?.requiresRasterization == true
}
