package com.joseleandro.scaniqo.ui.documentSearch.state

import com.joseleandro.scaniqo.domain.model.DocumentSearchResult

data class DocumentSearchUiState(
    val query: String = "",
    val results: List<DocumentSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val loadError: Boolean = false,
    val resultLimitReached: Boolean = false,
)
