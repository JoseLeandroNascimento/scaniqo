package com.joseleandro.scaniqo.data.repository

import android.content.Context
import android.net.Uri
import com.joseleandro.scaniqo.data.pdf.PdfTextMatch
import com.joseleandro.scaniqo.data.pdf.findPdfTextMatch
import com.joseleandro.scaniqo.data.pdf.normalizeSearchWhitespace
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.DocumentSearchResult
import com.joseleandro.scaniqo.domain.model.DocumentSearchUpdate
import com.joseleandro.scaniqo.domain.repository.DocumentSearchRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last

class AndroidDocumentSearchRepository internal constructor(
    private val openPdf: (String) -> PDDocument,
    private val ioDispatcher: CoroutineDispatcher,
    private val searchContent: suspend (PDDocument, String) -> PdfTextMatch? = ::findPdfTextMatch
) : DocumentSearchRepository {

    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(pdfOpener(context.applicationContext), ioDispatcher)

    override suspend fun search(
        documents: List<DocumentModel>,
        query: String,
    ): List<DocumentSearchResult> = searchUpdates(documents, query).last().results

    override fun searchUpdates(
        documents: List<DocumentModel>,
        query: String,
    ): Flow<DocumentSearchUpdate> = flow {
        val normalizedQuery = normalizeSearchWhitespace(query)
        if (normalizedQuery.isEmpty()) {
            emit(DocumentSearchUpdate(emptyList(), isComplete = true))
            return@flow
        }
        val results = mutableListOf<DocumentSearchResult>()
        val contentCandidates = mutableListOf<DocumentModel>()
        val seen = hashSetOf<Long>()
        var lastPublishedSize = 0
        var lastPublishedAt = System.nanoTime()
        suspend fun publish(complete: Boolean = false) {
            emit(DocumentSearchUpdate(
                results = results.toList(),
                isComplete = complete,
                resultLimitReached = results.size >= DocumentSearchRepository.MAX_RESULTS,
            ))
            lastPublishedSize = results.size
            lastPublishedAt = System.nanoTime()
        }

        for (document in documents) {
            currentCoroutineContext().ensureActive()
            if (!seen.add(document.id)) continue
            if (normalizeSearchWhitespace(document.name).contains(normalizedQuery, ignoreCase = true)) {
                results += DocumentSearchResult(document, matchesName = true)
                if (results.size >= DocumentSearchRepository.MAX_RESULTS) {
                    publish(complete = true)
                    return@flow
                }
                if (results.size - lastPublishedSize >= RESULT_BATCH_SIZE) publish()
            } else if (!document.isProtected) {
                contentCandidates += document
            }
        }
        // Publica os nomes antes de qualquer leitura de PDF: a pessoa já pode abrir o resultado.
        publish(complete = contentCandidates.isEmpty())
        if (contentCandidates.isEmpty()) return@flow
        for (document in contentCandidates) {
            currentCoroutineContext().ensureActive()
            val match = try {
                openPdf(document.pdfUri).use { pdf ->
                    currentCoroutineContext().ensureActive()
                    if (pdf.isEncrypted || !pdf.currentAccessPermission.canExtractContent()) null
                    else searchContent(pdf, normalizedQuery)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Um PDF removido, danificado ou protegido não impede a busca nos demais.
                null
            }
            if (match != null) {
                results += DocumentSearchResult(
                    document = document,
                    matchesName = false,
                    contentSnippet = match.snippet,
                    pageNumber = match.pageNumber,
                )
                if (results.size >= DocumentSearchRepository.MAX_RESULTS) {
                    publish(complete = true)
                    return@flow
                }
                if (results.size == 1 || results.size - lastPublishedSize >= RESULT_BATCH_SIZE ||
                    System.nanoTime() - lastPublishedAt >= PUBLISH_INTERVAL_NANOS
                ) publish()
            }
        }
        publish(complete = true)
    }.flowOn(ioDispatcher).buffer(0)

    private companion object {
        const val RESULT_BATCH_SIZE = 32
        const val PUBLISH_INTERVAL_NANOS = 100_000_000L

        fun pdfOpener(context: Context): (String) -> PDDocument = { source ->
            val uri = Uri.parse(source)
            val memory = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
            when (uri.scheme) {
                "file" -> PDDocument.load(File(requireNotNull(uri.path)), "", memory)
                "content" -> {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Não foi possível abrir o documento.")
                    stream.use { PDDocument.load(it, "", memory) }
                }
                else -> throw IOException("O endereço do documento é inválido.")
            }
        }
    }
}
