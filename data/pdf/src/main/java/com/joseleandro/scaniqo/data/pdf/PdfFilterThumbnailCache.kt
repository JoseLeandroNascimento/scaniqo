package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Intensidade e filtro selecionado não mudam as amostras, sempre calculadas a 100%. */
internal data class PdfFilterThumbnailKey(
    val page: PdfSinglePagePreviewKey,
    val signatures: List<PdfSourceStamp>,
) {
    companion object {
        fun from(
            workspace: PdfWorkspace,
            pageId: String,
            source: File,
            rasterizeText: Boolean,
            signatureFiles: List<File> = emptyList(),
        ): PdfFilterThumbnailKey {
            val page = workspace.pages.first { it.id == pageId }
            val normalized = workspace.copy(pages = workspace.pages.map {
                if (it.id != pageId) it else it.copy(layout = it.layout.copy(
                    imageFilter = PdfImageFilter.ORIGINAL,
                    filterIntensity = 1f,
                    rasterizeText = rasterizeText,
                ))
            })
            return PdfFilterThumbnailKey(
                PdfSinglePagePreviewKey.from(normalized, page.id, source),
                signatureFiles.map(PdfSourceStamp::from),
            )
        }
    }
}

internal data class PdfFilterThumbnailBatch(
    val images: Map<PdfImageFilter, File>,
    val requiresRasterization: Boolean,
)

/** Lotes completos são publicados juntos; cancelamentos nunca deixam amostras parciais. */
internal class PdfFilterThumbnailCache(
    private val directory: File,
    private val maxEntries: Int = 4,
    private val maxBytes: Long = 8L * 1024 * 1024,
) {
    private val entries = LinkedHashMap<PdfFilterThumbnailKey, PdfFilterThumbnailBatch>(4, .75f, true)

    init {
        require(maxEntries > 0 && maxBytes > 0)
    }

    suspend fun getOrCreate(
        key: PdfFilterThumbnailKey,
        force: Boolean = false,
        create: suspend (Map<PdfImageFilter, File>) -> PdfFilterThumbnailBatch,
    ): PdfFilterThumbnailBatch {
        currentCoroutineContext().ensureActive()
        val stale = entries.filterValues { batch ->
            batch.images.values.any { !it.isFile || it.length() == 0L }
        }.keys.toList()
        stale.forEach { entries.remove(it)?.let(::deleteBatch) }
        if (!force) entries[key]?.let { return it }
        val token = UUID.randomUUID()
        val targets = PdfImageFilter.entries.associateWith {
            File(directory, "filter_thumbnail_${token}_${it.name.lowercase()}.png")
        }
        var completed = false
        try {
            val batch = create(targets)
            currentCoroutineContext().ensureActive()
            require(batch.images.keys == PdfImageFilter.entries.toSet() &&
                batch.images.values.all { it in targets.values && it.isFile && it.length() > 0 }
            ) { "Não foi possível criar todas as amostras dos filtros." }
            val incomingBytes = batch.images.values.toSet().sumOf { it.length() }
            if (incomingBytes > maxBytes) throw IOException("As amostras dos filtros excederam o limite de espaço.")
            // Uma tentativa forçada só substitui o lote anterior depois de concluir com sucesso.
            entries.remove(key)?.let(::deleteBatch)
            while (entries.size >= maxEntries ||
                entries.values.sumOf(::batchBytes) + incomingBytes > maxBytes
            ) {
                val oldest = entries.entries.firstOrNull()
                    ?: throw IOException("Não há espaço para as amostras dos filtros.")
                deleteBatch(oldest.value)
                entries.remove(oldest.key)
            }
            entries[key] = batch
            completed = true
            return batch
        } finally {
            if (!completed) targets.values.forEach(::deleteOwned)
        }
    }

    fun clear() {
        entries.values.forEach(::deleteBatch)
        entries.clear()
        // Inclui arquivos de uma sessão anterior cujo processo terminou durante a geração.
        directory.listFiles().orEmpty().forEach(::deleteOwned)
    }

    private fun batchBytes(batch: PdfFilterThumbnailBatch): Long =
        batch.images.values.toSet().sumOf { it.length() }

    private fun deleteBatch(batch: PdfFilterThumbnailBatch) =
        batch.images.values.toSet().forEach(::deleteOwned)

    private fun deleteOwned(file: File) {
        if (file.canonicalFile.parentFile == directory.canonicalFile &&
            file.name.startsWith("filter_thumbnail_") && file.name.endsWith(".png") &&
            file.isFile && !file.delete() && file.exists()
        ) throw IOException("Não foi possível limpar uma amostra temporária dos filtros.")
    }
}
