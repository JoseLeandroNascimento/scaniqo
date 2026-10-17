package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfPageSpec

/** Somente a folha e seu rodapé invalidam a prévia; OCR e compressão pertencem à exportação. */
internal data class DocumentPreviewContent(
    val workspaceId: String,
    val page: PdfPageSpec,
    val numbering: Pair<Int, Int>?,
)
