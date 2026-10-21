package com.joseleandro.scaniqo.data.repository

import com.joseleandro.scaniqo.data.datasource.DocumentDatasource
import com.joseleandro.scaniqo.data.local.document.DocumentEntity
import com.joseleandro.scaniqo.data.local.document.DocumentFileStore
import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DocumentRepositoryImpl(
    private val documentDatasource: DocumentDatasource,
    private val fileStore: DocumentFileStore,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : DocumentRepository {

    // Evita que o descarte do formulário concorra com a confirmação do mesmo scan.
    private val mutationMutex = Mutex()

    override fun observeDocuments(): Flow<List<DocumentModel>> =
        documentDatasource.documentsObserver.map { documents -> documents.map { it.toModel() } }

    override fun observeTrashedDocuments(): Flow<List<DocumentModel>> =
        documentDatasource.trashedDocumentsObserver.map { documents -> documents.map { it.toModel() } }

    override suspend fun findById(id: Long): DocumentModel? =
        documentDatasource.findById(id)?.toModel()

    override suspend fun prepareScan(pageUris: List<String>): ScannedDocument {
        require(pageUris.isNotEmpty()) { "Digitalize pelo menos uma página." }
        return fileStore.prepareScan(pageUris)
    }

    override suspend fun discardScan(scan: ScannedDocument) = mutationMutex.withLock {
        // Um cancelamento tardio da interface não pode apagar um documento já salvo.
        if (documentDatasource.findByPdfUri(scan.pdfUri) == null) {
            fileStore.deleteOwnedDocument(scan.pdfUri)
        }
    }

    override suspend fun create(
        scan: ScannedDocument,
        name: String,
        tagId: Long?
    ): Long = mutationMutex.withLock {
        val normalizedName = validateName(name)
        require(scan.pageCount > 0 && scan.pdfUri.isNotBlank()) {
            "O documento digitalizado é inválido."
        }
        // A confirmação pode ter sido gravada antes da restauração de um formulário antigo.
        val existing = documentDatasource.findByPdfUri(scan.pdfUri)
        if (existing != null) {
            check(existing.deletedAt == null) { "Restaure o documento da lixeira antes de editá-lo." }
            if (existing.name != normalizedName || existing.tagId != tagId) {
                check(documentDatasource.updateMetadata(existing.id, normalizedName, tagId) == 1) {
                    "O documento não foi encontrado para atualização."
                }
            }
            return@withLock existing.id
        }
        documentDatasource.insert(
            DocumentEntity(
                name = normalizedName,
                pdfUri = scan.pdfUri,
                pageCount = scan.pageCount,
                createdAt = scan.createdAt,
                sizeBytes = scan.sizeBytes,
                thumbnailUri = scan.thumbnailUri,
                tagId = tagId,
                protectionKeyId = scan.protectionKeyId
            )
        )
    }

    override suspend fun update(id: Long, name: String, tagId: Long?) {
        val affectedRows = documentDatasource.updateMetadata(id, validateName(name), tagId)
        check(affectedRows == 1) { "O documento não foi encontrado para atualização." }
    }

    override suspend fun replaceContent(
        id: Long,
        scan: ScannedDocument,
        allowProtectionRemoval: Boolean
    ): DocumentModel =
        mutationMutex.withLock {
            val stored = checkNotNull(documentDatasource.findById(id)) {
                "O documento não foi encontrado para atualização."
            }
            require(scan.pageCount > 0 && scan.pdfUri.isNotBlank()) {
                "O documento editado é inválido."
            }
            require(allowProtectionRemoval || stored.protectionKeyId == null ||
                scan.protectionKeyId != null) {
                "A edição não pode remover a proteção do documento."
            }
            val replacement = stored.copy(
                pdfUri = scan.pdfUri,
                pageCount = scan.pageCount,
                sizeBytes = scan.sizeBytes,
                thumbnailUri = scan.thumbnailUri,
                protectionKeyId = scan.protectionKeyId
            )
            // A confirmação e sua limpeza terminam juntas mesmo se a tela for encerrada.
            withContext(NonCancellable) {
                check(documentDatasource.replaceContent(replacement) == 1) {
                    "O documento não foi encontrado para atualização."
                }
                if (stored.pdfUri != replacement.pdfUri) {
                    try {
                        if (documentDatasource.findByPdfUri(stored.pdfUri) == null) {
                            fileStore.deleteOwnedDocument(stored.pdfUri)
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        // O arquivo novo já está confirmado; limpeza pendente não desfaz a edição.
                    }
                }
                checkNotNull(documentDatasource.findById(id)).toModel()
            }
        }

    override suspend fun moveToTrash(id: Long) = mutationMutex.withLock {
        check(documentDatasource.moveToTrash(id, nowMillis()) == 1) {
            "O documento não foi encontrado para mover à lixeira."
        }
    }

    override suspend fun restoreFromTrash(id: Long) = mutationMutex.withLock {
        // A chave estrangeira de tags já remove a associação quando uma tag é excluída.
        check(documentDatasource.restoreFromTrash(id) == 1) {
            "O documento não foi encontrado na lixeira."
        }
    }

    override suspend fun deletePermanently(id: Long) = mutationMutex.withLock {
        val stored = checkNotNull(documentDatasource.findTrashedById(id)) {
            "O documento não foi encontrado na lixeira."
        }
        check(documentDatasource.deleteTrashed(id) == 1) {
            "O documento não foi encontrado na lixeira."
        }
        deleteUnreferencedFiles(stored.pdfUri)
    }

    override suspend fun delete(document: DocumentModel) = mutationMutex.withLock {
        val stored = checkNotNull(documentDatasource.findById(document.id)) {
            "O documento não foi encontrado para exclusão."
        }
        check(documentDatasource.delete(stored) == 1) {
            "O documento não foi encontrado para exclusão."
        }
        deleteUnreferencedFiles(stored.pdfUri)
    }

    private suspend fun deleteUnreferencedFiles(pdfUri: String) {
        try {
            // Primeiro removemos o registro; falhas de limpeza não restauram um item inválido.
            // A consulta inclui a lixeira para proteger arquivos ainda recuperáveis.
            if (documentDatasource.findByPdfUri(pdfUri) == null) {
                fileStore.deleteOwnedDocument(pdfUri)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // O arquivo privado poderá permanecer órfão caso o armazenamento esteja indisponível.
        }
    }

    override suspend fun export(document: DocumentModel, targetUri: String) =
        fileStore.export(document, targetUri)

    override suspend fun prepareShare(document: DocumentModel): String =
        fileStore.prepareShare(document)

    private fun validateName(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Informe o nome do documento." }
        require(normalized.length <= DOCUMENT_NAME_MAX_LENGTH) {
            "O nome do documento deve ter até $DOCUMENT_NAME_MAX_LENGTH caracteres."
        }
        return normalized
    }

    private fun DocumentEntity.toModel() = DocumentModel(
        id = id,
        name = name,
        pdfUri = pdfUri,
        pageCount = pageCount,
        createdAt = createdAt,
        sizeBytes = sizeBytes,
        thumbnailUri = thumbnailUri,
        tagId = tagId,
        protectionKeyId = protectionKeyId,
        deletedAt = deletedAt
    )
}
