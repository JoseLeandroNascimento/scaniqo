package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfMergeLimitException
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Prepara o lote inteiro antes de publicar páginas; uma falha desfaz apenas os arquivos do lote. */
internal class PdfMergeBatch(
    private val directory: File,
    private val uriForFile: (File) -> String
) {
    private val ownedFiles = mutableListOf<File>()
    private var started = false

    suspend fun append(
        workspace: PdfWorkspace,
        sources: List<PdfMergeSource>,
        existingBytes: Long,
        prepare: suspend (Preparation) -> Int
    ): PdfWorkspace {
        check(!started)
        started = true
        require(sources.isNotEmpty() && sources.all { it.uri.isNotBlank() }) {
            "Selecione pelo menos um PDF válido."
        }
        checkPageCount(workspace.pages.size + sources.size)
        if (existingBytes !in 0..PdfWorkspaceFiles.MAX_BYTES) throw sizeLimit()
        val prepared = mutableMapOf<String, Prepared>()
        val appended = mutableListOf<PdfPageSpec>()
        var totalBytes = existingBytes
        try {
            sources.forEach { source ->
                currentCoroutineContext().ensureActive()
                val remainingPages = PdfWorkspaceFiles.MAX_PAGES - workspace.pages.size - appended.size
                if (remainingPages < 1) throw pageLimit()
                val item = prepared[source.uri] ?: run {
                    val remainingBytes = PdfWorkspaceFiles.MAX_BYTES - totalBytes
                    if (remainingBytes <= 0) throw sizeLimit()
                    val incoming = temporaryFile("merge_incoming")
                    val output = temporaryFile("source")
                    val pageCount = prepare(
                        Preparation(source, incoming, output, remainingPages, remainingBytes)
                    )
                    currentCoroutineContext().ensureActive()
                    if (pageCount !in 1..remainingPages) throw pageLimit()
                    check(output.isFile) { "A fonte preparada não está disponível." }
                    val length = output.length()
                    if (length <= 0 || length > remainingBytes) throw sizeLimit()
                    deleteChecked(incoming)
                    totalBytes += length
                    Prepared(output, pageCount).also { prepared[source.uri] = it }
                }
                checkPageCount(workspace.pages.size + appended.size + item.pageCount)
                repeat(item.pageCount) { index ->
                    appended += PdfPageSpec(
                        UUID.randomUUID().toString(), uriForFile(item.file), index
                    )
                }
            }
            currentCoroutineContext().ensureActive()
            return workspace.copy(pages = workspace.pages + appended)
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                try {
                    rollback()
                } catch (cleanupFailure: Exception) {
                    failure.addSuppressed(cleanupFailure)
                }
            }
            throw failure
        }
    }

    /** Também cobre cancelamento ao devolver o resultado para outro dispatcher. */
    fun rollback() {
        var failure: IOException? = null
        ownedFiles.forEach { file ->
            try {
                if (file.canonicalFile.parentFile == directory.canonicalFile) deleteChecked(file)
            } catch (error: IOException) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun temporaryFile(prefix: String): File =
        File(directory, "${prefix}_${UUID.randomUUID()}.pdf").also { ownedFiles += it }

    private fun deleteChecked(file: File) {
        if (file.exists() && !file.delete() && file.exists()) {
            throw IOException("Não foi possível limpar um arquivo temporário da mesclagem.")
        }
    }

    private fun checkPageCount(count: Int) {
        if (count !in 1..PdfWorkspaceFiles.MAX_PAGES) throw pageLimit()
    }

    private fun pageLimit() = PdfMergeLimitException(PdfMergeLimitException.Reason.PAGE_COUNT)
    private fun sizeLimit() = PdfMergeLimitException(PdfMergeLimitException.Reason.TOTAL_BYTES)

    private data class Prepared(val file: File, val pageCount: Int)

    data class Preparation(
        val source: PdfMergeSource,
        val incoming: File,
        val output: File,
        val remainingPages: Int,
        val remainingBytes: Long
    )
}
