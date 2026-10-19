package com.joseleandro.scaniqo.data.repository

import android.content.Context
import android.net.Uri
import com.joseleandro.scaniqo.data.pdf.PdfTextMatch
import com.joseleandro.scaniqo.data.pdf.findPdfTextMatch
import com.joseleandro.scaniqo.data.pdf.normalizeSearchWhitespace
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.DocumentSearchResult
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
import kotlinx.coroutines.withContext

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
        query: String
    ): List<DocumentSearchResult> = withContext(ioDispatcher) {
        val normalizedQuery = normalizeSearchWhitespace(query)
        if (normalizedQuery.isEmpty()) return@withContext emptyList()
        val results = mutableListOf<DocumentSearchResult>()
        val contentCandidates = mutableListOf<DocumentModel>()
        documents.forEach { document ->
            currentCoroutineContext().ensureActive()
            if (normalizeSearchWhitespace(document.name).contains(normalizedQuery, ignoreCase = true)) {
                results += DocumentSearchResult(document, matchesName = true)
            } else if (!document.isProtected) {
                contentCandidates += document
            }
        }
        // Os nomes têm prioridade e continuam pesquisáveis mesmo se o arquivo estiver inacessível.
        contentCandidates.forEach { document ->
            currentCoroutineContext().ensureActive()
            val match = try {
                openPdf(document.pdfUri).use { pdf ->
                    currentCoroutineContext().ensureActive()
                    if (pdf.isEncrypted || !pdf.currentAccessPermission.canExtractContent()) {
                        null
                    } else {
                        searchContent(pdf, normalizedQuery)
                    }
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
                    pageNumber = match.pageNumber
                )
            }
        }
        results
    }

    private companion object {
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
