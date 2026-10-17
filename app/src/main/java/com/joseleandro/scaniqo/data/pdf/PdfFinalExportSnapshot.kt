package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File

/** Contém exclusivamente uma cópia autenticada do PDF final; nunca uma prévia reduzida. */
internal class PdfFinalExportSnapshot private constructor(
    val file: File,
    val visual: PdfPreviewKey,
    val quality: PdfExportQuality,
    val ocrEnabled: Boolean,
    val sources: Map<String, PdfSourceStamp>,
) {
    fun matchingPageIndices(workspace: PdfWorkspace, sourceFiles: Map<String, File>): List<Int?> =
        matching(workspace, sourceFiles, ignoreNumbering = false)

    /** O compositor confirma a existência da camada de rodapé antes de removê-la. */
    fun matchingContentPageIndices(workspace: PdfWorkspace, sourceFiles: Map<String, File>): List<Int?> =
        matching(workspace, sourceFiles, ignoreNumbering = true)

    private fun matching(
        workspace: PdfWorkspace,
        sourceFiles: Map<String, File>,
        ignoreNumbering: Boolean,
    ): List<Int?> {
        if (!file.isFile || quality != workspace.exportQuality || ocrEnabled != workspace.ocrEnabled) {
            return List(workspace.pages.size) { null }
        }
        val current = sourceFiles.mapValues { PdfSourceStamp.from(it.value) }
        val key = if (ignoreNumbering) visual.copy(showPageNumbers = false) else visual
        val currentWorkspace = if (ignoreNumbering) workspace.copy(showPageNumbers = false) else workspace
        return PdfReusablePreview(file, key).matchingPageIndices(currentWorkspace).mapIndexed { index, previous ->
            previous?.takeIf {
                val uri = workspace.pages[index].sourceUri
                sources[uri] == current[uri] && current[uri] != null
            }
        }
    }

    companion object {
        fun capture(
            workspace: PdfWorkspace,
            finalFile: File,
            sourceFiles: Map<String, File>,
        ): PdfFinalExportSnapshot {
            require(finalFile.name.startsWith("final_preview_") && finalFile.name.endsWith(".pdf")) {
                "Somente o PDF final pode servir como origem da próxima exportação."
            }
            return PdfFinalExportSnapshot(
                finalFile, PdfPreviewKey.from(workspace), workspace.exportQuality,
                workspace.ocrEnabled, sourceFiles.mapValues { PdfSourceStamp.from(it.value) },
            )
        }
    }
}
