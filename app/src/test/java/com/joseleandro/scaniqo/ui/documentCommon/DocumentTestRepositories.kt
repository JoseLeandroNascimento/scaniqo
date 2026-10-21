package com.joseleandro.scaniqo.ui.documentCommon

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.TagRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal data class CreateRequest(val draft: ScannedDocument, val name: String, val tagId: Long?)
internal data class UpdateRequest(val id: Long, val name: String, val tagId: Long?)

/** Simula operações e falhas sem abrir arquivos, banco de dados ou contratos Android. */
internal class FakeDocumentRepository : DocumentRepository {
    val documents = MutableStateFlow<List<DocumentModel>>(emptyList())
    val trashedDocuments = MutableStateFlow<List<DocumentModel>>(emptyList())
    val movedToTrash = mutableListOf<Long>()
    val restored = mutableListOf<Long>()
    val permanentlyDeleted = mutableListOf<Long>()
    val scan = ScannedDocument(
        pdfUri = "file:///documents/draft.pdf",
        pageCount = 2,
        createdAt = 1_750_000_000_000,
        sizeBytes = 2048,
        thumbnailUri = "file:///documents/draft.jpg",
    )
    val preparedPages = mutableListOf<List<String>>()
    val discarded = mutableListOf<ScannedDocument>()
    val created = mutableListOf<CreateRequest>()
    val updated = mutableListOf<UpdateRequest>()
    val deleted = mutableListOf<DocumentModel>()
    val exported = mutableListOf<Pair<DocumentModel, String>>()
    val shared = mutableListOf<DocumentModel>()
    var loadFailure: Throwable? = null
    var prepareFailure: Throwable? = null
    var writeFailure: Throwable? = null
    var discardFailure: Throwable? = null
    var exportFailure: Throwable? = null
    var shareFailure: Throwable? = null
    var prepareGate: CompletableDeferred<Unit>? = null
    var writeGate: CompletableDeferred<Unit>? = null
    var observationCount = 0
    var activeObservers = 0
    var maximumActiveObservers = 0
    private var nextId = 20L

    override fun observeDocuments(): Flow<List<DocumentModel>> = flow {
        observationCount++
        activeObservers++
        maximumActiveObservers = maxOf(maximumActiveObservers, activeObservers)
        try {
            loadFailure?.let { throw it }
            emitAll(documents)
        } finally {
            activeObservers--
        }
    }

    override fun observeTrashedDocuments(): Flow<List<DocumentModel>> = flow {
        loadFailure?.let { throw it }
        emitAll(trashedDocuments)
    }

    override suspend fun moveToTrash(id: Long) {
        movedToTrash += id
        awaitWrite()
        val document = checkNotNull(documents.value.firstOrNull { it.id == id })
        documents.value = documents.value.filterNot { it.id == id }
        trashedDocuments.value += document.copy(deletedAt = 1_790_000_000_000L)
    }

    override suspend fun restoreFromTrash(id: Long) {
        restored += id
        awaitWrite()
        val document = checkNotNull(trashedDocuments.value.firstOrNull { it.id == id })
        trashedDocuments.value = trashedDocuments.value.filterNot { it.id == id }
        documents.value += document.copy(deletedAt = null)
    }

    override suspend fun deletePermanently(id: Long) {
        permanentlyDeleted += id
        awaitWrite()
        check(trashedDocuments.value.any { it.id == id })
        trashedDocuments.value = trashedDocuments.value.filterNot { it.id == id }
    }

    override suspend fun findById(id: Long): DocumentModel? =
        documents.value.firstOrNull { it.id == id }

    override suspend fun prepareScan(pageUris: List<String>): ScannedDocument {
        preparedPages += pageUris
        prepareGate?.await()
        prepareFailure?.let { throw it }
        return scan
    }

    override suspend fun discardScan(scan: ScannedDocument) {
        discarded += scan
        discardFailure?.let { throw it }
    }

    override suspend fun create(scan: ScannedDocument, name: String, tagId: Long?): Long {
        created += CreateRequest(scan, name, tagId)
        awaitWrite()
        val id = nextId++
        documents.value += DocumentModel(
            id = id,
            name = name,
            pdfUri = scan.pdfUri,
            pageCount = scan.pageCount,
            createdAt = scan.createdAt,
            sizeBytes = scan.sizeBytes,
            thumbnailUri = scan.thumbnailUri,
            tagId = tagId,
        )
        return id
    }

    override suspend fun update(id: Long, name: String, tagId: Long?) {
        updated += UpdateRequest(id, name, tagId)
        awaitWrite()
        documents.value = documents.value.map {
            if (it.id == id) it.copy(name = name, tagId = tagId) else it
        }
    }

    override suspend fun replaceContent(
        id: Long,
        scan: ScannedDocument,
        allowProtectionRemoval: Boolean,
    ): DocumentModel = error("A edição de páginas pertence ao editor de PDF")

    override suspend fun delete(document: DocumentModel) {
        deleted += document
        awaitWrite()
        documents.value = documents.value.filterNot { it.id == document.id }
    }

    override suspend fun export(document: DocumentModel, targetUri: String) {
        exported += document to targetUri
        writeGate?.await()
        exportFailure?.let { throw it }
    }

    override suspend fun prepareShare(document: DocumentModel): String {
        shared += document
        writeGate?.await()
        shareFailure?.let { throw it }
        return "content://shared/" + document.id + ".pdf"
    }

    private suspend fun awaitWrite() {
        writeGate?.await()
        writeFailure?.let { throw it }
    }
}

internal class FakeTagRepository : TagRepository {
    val tags = MutableStateFlow<List<TagModel>>(emptyList())

    var loadFailure: Throwable? = null
    var loadGate: CompletableDeferred<Unit>? = null
    var observationCount = 0
    var activeObservers = 0
    var maximumActiveObservers = 0

    override fun observeTags(): Flow<List<TagModel>> = flow {
        observationCount++
        activeObservers++
        maximumActiveObservers = maxOf(maximumActiveObservers, activeObservers)
        try {
            loadGate?.await()
            loadFailure?.let { throw it }
            emitAll(tags)
        } finally {
            activeObservers--
        }
    }

    override suspend fun findById(id: Long): TagModel? =
        tags.value.firstOrNull { it.id == id }

    override suspend fun create(tag: TagModel): Long = error("Fora do escopo desta tela")

    override suspend fun update(tag: TagModel) = error("Fora do escopo desta tela")

    override suspend fun delete(tag: TagModel) = error("Fora do escopo desta tela")
}
