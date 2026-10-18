package com.joseleandro.scaniqo.ui.documents.event

/** Intenções exclusivas da lista de documentos. */
sealed interface DocumentsEvent {
    data class SelectTag(val tagId: Long?) : DocumentsEvent
    data object RetryLoad : DocumentsEvent
}
