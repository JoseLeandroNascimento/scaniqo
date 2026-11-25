package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ReadableDocument

interface DocumentReaderRepository {
    /** Lê o resultado salvo sem carregar fontes, ferramentas ou receitas de edição. */
    suspend fun open(document: DocumentModel, password: String? = null): ReadableDocument
    /** Remove somente o arquivo temporário privado; nunca modifica o documento salvo. */
    suspend fun close(document: ReadableDocument)
}
