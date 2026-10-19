package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.DocumentSearchResult

interface DocumentSearchRepository {
    /** Pesquisa nomes e texto já presente no PDF, sem executar reconhecimento de imagens. */
    suspend fun search(documents: List<DocumentModel>, query: String): List<DocumentSearchResult>
}
