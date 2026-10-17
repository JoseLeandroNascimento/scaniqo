package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File

/** Mapeia as fontes originais para o arquivo consolidado, independentemente dos ajustes visuais. */
internal class PdfSourceArchive private constructor(
    val file: File,
    private val pageCount: Int,
    private val pages: Map<String, Page>,
) {
    private data class Page(val source: PdfSourceStamp, val sourceIndex: Int, val archiveIndex: Int)

    fun indices(workspace: PdfWorkspace, sourceFiles: Map<String, File>): List<Int>? {
        if (!file.isFile) return null
        val stamps = sourceFiles.mapValues { PdfSourceStamp.from(it.value) }
        val indices = workspace.pages.map { page ->
            val previous = pages[page.id] ?: return null
            if (previous.sourceIndex != page.sourcePageIndex ||
                previous.source != stamps[page.sourceUri]
            ) return null
            previous.archiveIndex
        }
        // Não persiste material excluído: todas as folhas físicas precisam continuar referenciadas.
        return indices.takeIf { it.toSet() == (0 until pageCount).toSet() }
    }

    companion object {
        fun single(
            workspace: PdfWorkspace,
            sourceFiles: Map<String, File>,
            pageCount: Int,
        ): PdfSourceArchive? {
            val (uri, file) = sourceFiles.entries.singleOrNull() ?: return null
            if (workspace.pages.any { it.sourceUri != uri }) return null
            val stamp = PdfSourceStamp.from(file)
            val result = PdfSourceArchive(file, pageCount, workspace.pages.associate { page ->
                page.id to Page(stamp, page.sourcePageIndex, page.sourcePageIndex)
            })
            return result.takeIf { it.indices(workspace, sourceFiles) != null }
        }

        fun composed(
            workspace: PdfWorkspace,
            archive: File,
            sourceFiles: Map<String, File>,
        ): PdfSourceArchive {
            val stamps = sourceFiles.mapValues { PdfSourceStamp.from(it.value) }
            return PdfSourceArchive(archive, workspace.pages.size, workspace.pages.mapIndexed { index, page ->
                page.id to Page(requireNotNull(stamps[page.sourceUri]), page.sourcePageIndex, index)
            }.toMap())
        }
    }
}
