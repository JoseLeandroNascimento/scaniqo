package com.joseleandro.scaniqo.ui.documentEditor.state

import com.joseleandro.scaniqo.domain.model.PdfCaptureSource

/** Fonte sem filtros ou margens da folha, separada do resultado das correções. */
data class DocumentCaptureSourceState(
    val pageId: String? = null,
    val source: PdfCaptureSource? = null,
    val isLoading: Boolean = false,
    val error: Boolean = false,
    val unsupported: Boolean = false,
)
