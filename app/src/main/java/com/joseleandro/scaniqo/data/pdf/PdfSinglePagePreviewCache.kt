package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A numeração é a única configuração da folha que depende das outras páginas do documento. */
internal data class PdfSinglePagePreviewKey(
    val source: PdfSourceStamp,
    val page: PdfPageSpec,
    val pageNumber: Int?,
    val pageCount: Int?,
) {
    companion object {
        fun from(workspace: PdfWorkspace, pageId: String, source: File): PdfSinglePagePreviewKey {
            val index = workspace.pages.indexOfFirst { it.id == pageId }
            require(index >= 0) { "A página não está mais disponível." }
            val page = workspace.pages[index]
            return PdfSinglePagePreviewKey(
                PdfSourceStamp.from(source),
                page.copy(signatures = page.signatures.toList()),
                (index + 1).takeIf { workspace.showPageNumbers },
                workspace.pages.size.takeIf { workspace.showPageNumbers },
            )
        }
    }
}

/** PDFs pequenos e independentes; prévias ainda visíveis nunca são removidas pelo limite do cache. */
internal class PdfSinglePagePreviewCache(
    private val directory: File,
    private val maxEntries: Int = 200,
    private val maxBytes: Long = 256L * 1024 * 1024,
) {
    private val entries = LinkedHashMap<PdfSinglePagePreviewKey, File>(16, .75f, true)

    suspend fun getOrCreate(
        key: PdfSinglePagePreviewKey,
        retainedFiles: Set<File>,
        create: suspend (File) -> Unit,
    ): File {
        entries.entries.removeAll { !it.value.isFile }
        entries[key]?.let { return it }
        val target = File(directory, "page_preview_${UUID.randomUUID()}.pdf")
        var completed = false
        try {
            create(target)
            currentCoroutineContext().ensureActive()
            require(target.isFile && target.length() > 0) { "Não foi possível criar a prévia da página." }
            val retained = retainedFiles.mapTo(mutableSetOf()) { it.canonicalFile }
            val incomingBytes = target.length()
            while (entries.size >= maxEntries ||
                entries.values.sumOf { it.length() } + incomingBytes > maxBytes
            ) {
                val oldest = entries.entries.firstOrNull { it.value.canonicalFile !in retained }
                    ?: throw IOException("Não há espaço para uma nova prévia. Feche a edição e tente novamente.")
                deleteOwned(oldest.value)
                entries.remove(oldest.key)
            }
            entries[key] = target
            completed = true
            return target
        } finally {
            if (!completed) deleteOwned(target)
        }
    }

    fun clear() {
        entries.values.forEach(::deleteOwned)
        entries.clear()
    }

    private fun deleteOwned(file: File) {
        if (file.canonicalFile.parentFile == directory.canonicalFile &&
            file.name.startsWith("page_preview_") && file.name.endsWith(".pdf") &&
            file.isFile && !file.delete() && file.exists()
        ) throw IOException("Não foi possível limpar a prévia temporária da página.")
    }
}
