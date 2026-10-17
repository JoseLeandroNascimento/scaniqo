package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File

/** Reutiliza prévias prontas ao comparar/desfazer, sem manter documentos PDF abertos. */
internal class PdfPreviewSnapshotCache {
    private val sessions = mutableMapOf<String, LinkedHashMap<PdfPreviewKey, File>>()

    fun find(workspace: PdfWorkspace): File? {
        val entries = sessions[workspace.workspaceId] ?: return null
        val key = PdfPreviewKey.from(workspace)
        val file = entries[key] ?: return null
        if (file.isFile) return file
        entries.remove(key)
        return null
    }

    /** Uma folha inalterada aponta direto para o PDF salvo; não é necessário copiá-la. */
    fun findPage(workspace: PdfWorkspace, pageId: String): Pair<File, Int>? {
        val index = workspace.pages.indexOfFirst { it.id == pageId }
        if (index < 0) return null
        val entries = sessions[workspace.workspaceId] ?: return null
        return entries.entries.toList().asReversed().firstNotNullOfOrNull { (key, file) ->
            if (!file.isFile) return@firstNotNullOfOrNull null
            PdfReusablePreview(file, key).matchingPageIndices(workspace)[index]?.let { file to it }
        }
    }

    fun reusable(workspace: PdfWorkspace): PdfReusablePreview? {
        val entries = sessions[workspace.workspaceId] ?: return null
        // Até três snapshots; escolhemos aquele que evita mais trabalho na composição atual.
        return entries.entries.asSequence()
            .filter { it.value.isFile }
            .map { PdfReusablePreview(it.value, it.key) }
            .map { it to it.matchingPageIndices(workspace).count { index -> index != null } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first
    }

    fun remember(workspace: PdfWorkspace, file: File) {
        val entries = sessions.getOrPut(workspace.workspaceId) { LinkedHashMap(4, .75f, true) }
        entries[PdfPreviewKey.from(workspace)] = file
        while (entries.size > 3) entries.remove(entries.keys.first())
    }

    fun clear(workspaceId: String) {
        sessions.remove(workspaceId)
    }
}
