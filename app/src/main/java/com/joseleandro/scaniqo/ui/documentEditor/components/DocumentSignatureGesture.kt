package com.joseleandro.scaniqo.ui.documentEditor.components

import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement

internal enum class DocumentSignatureGestureKind { MOVE, RESIZE, ROTATE }

/** O ponteiro usa coordenadas normalizadas da folha, mesmo quando a alça sai dela. */
internal data class DocumentSignatureGesture(
    val placement: PdfSignaturePlacement,
    val pointer: PdfPoint,
    val kind: DocumentSignatureGestureKind,
    val cornerIndex: Int? = null,
)
