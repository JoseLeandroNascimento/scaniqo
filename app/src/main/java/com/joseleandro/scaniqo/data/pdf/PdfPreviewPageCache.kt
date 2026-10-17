package com.joseleandro.scaniqo.data.pdf

import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Cache privado limitado. Nunca guarda bitmaps em memória nem remove arquivos das fontes. */
internal class PdfPreviewPageCache(
    private val directory: File,
    private val maxBytes: Long = 128L * 1024 * 1024,
    private val maxEntries: Int = 200,
    private val filePrefix: String = "raster_",
) {
    init {
        require(filePrefix == "raster_" || filePrefix == "export_raster_")
    }
    data class Asset(val file: File, val width: Float, val height: Float)
    private val entries = LinkedHashMap<PdfRasterKey, Asset>(16, .75f, true)
    private var pinned = emptySet<PdfRasterKey>()

    fun beginPreview(keys: Set<PdfRasterKey>) {
        // Páginas usadas nesta composição não saem antes de serem reaproveitadas.
        pinned = keys
        entries.entries.removeAll { !it.value.file.isFile }
    }

    suspend fun <T> use(
        key: PdfRasterKey,
        extension: String,
        create: suspend (File) -> Pair<Float, Float>,
        consume: (Asset) -> T,
    ): T {
        entries[key]?.takeIf { it.file.isFile }?.let { return consume(it) }
        require(extension == "jpg" || extension == "png")
        val target = File(directory, "$filePrefix${UUID.randomUUID()}.$extension")
        var retained = false
        try {
            val (width, height) = create(target)
            currentCoroutineContext().ensureActive()
            require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
            val asset = Asset(target, width, height)
            val bytes = target.length()
            while (entries.isNotEmpty() &&
                (entries.size >= maxEntries || entries.values.sumOf { it.file.length() } + bytes > maxBytes)
            ) {
                val oldest = entries.entries.firstOrNull { it.key !in pinned } ?: break
                deleteOwned(oldest.value.file)
                entries.remove(oldest.key)
            }
            if (bytes <= maxBytes && entries.size < maxEntries &&
                entries.values.sumOf { it.file.length() } + bytes <= maxBytes
            ) {
                entries[key] = asset
                retained = true
            }
            return consume(asset)
        } finally {
            if (!retained) deleteOwned(target)
        }
    }

    fun clear() {
        entries.values.forEach { deleteOwned(it.file) }
        entries.clear()
        pinned = emptySet()
    }

    private fun deleteOwned(file: File) {
        if (file.canonicalFile.parentFile == directory.canonicalFile &&
            file.name.startsWith(filePrefix) && file.isFile &&
            !file.delete() && file.exists()
        ) throw IOException("Não foi possível limpar a prévia temporária da página.")
    }
}
