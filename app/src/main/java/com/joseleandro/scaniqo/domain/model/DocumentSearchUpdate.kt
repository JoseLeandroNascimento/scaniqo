package com.joseleandro.scaniqo.domain.model

/** Snapshot acumulado, sem IDs duplicados, de uma consulta ainda em execução ou concluída. */
data class DocumentSearchUpdate(
    val results: List<DocumentSearchResult>,
    val isComplete: Boolean,
    val resultLimitReached: Boolean = false,
)
