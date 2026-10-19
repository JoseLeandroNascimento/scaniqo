package com.joseleandro.scaniqo.domain.model

data class DocumentSearchResult(
    val document: DocumentModel,
    val matchesName: Boolean,
    val contentSnippet: String? = null,
    /** Página do primeiro trecho encontrado, começando em 1. */
    val pageNumber: Int? = null
)
