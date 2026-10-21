package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.DocumentSearchResult
import com.joseleandro.scaniqo.domain.model.DocumentSearchUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DocumentSearchRepository {
    /** Pesquisa nomes e texto já presente no PDF, sem executar reconhecimento de imagens. */
    suspend fun search(documents: List<DocumentModel>, query: String): List<DocumentSearchResult>

    /** Publica nomes antes da leitura do conteúdo; implementações simples podem emitir uma vez. */
    fun searchUpdates(documents: List<DocumentModel>, query: String): Flow<DocumentSearchUpdate> = flow {
        emit(DocumentSearchUpdate(search(documents, query), isComplete = true))
    }

    companion object {
        const val MAX_RESULTS = 500
    }
}
