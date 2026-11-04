package com.joseleandro.scaniqo.data.pdf

import java.io.File

/** Guarda apenas indicadores, sem texto extraído, com limite independente do número de sessões. */
internal class PdfNativeContentCache(private val capacity: Int = 512) {
    private data class Key(val source: PdfSourceStamp, val pageIndex: Int)
    private val entries = LinkedHashMap<Key, PdfNativeContent>(16, .75f, true)

    suspend fun get(
        file: File,
        pageIndex: Int,
        inspect: suspend () -> PdfNativeContent,
    ): PdfNativeContent {
        val key = Key(PdfSourceStamp.from(file), pageIndex)
        entries[key]?.let { return it }
        val value = inspect()
        entries[key] = value
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return value
    }

    fun clear(directory: File) {
        val parent = directory.canonicalFile
        entries.keys.removeAll { File(it.source.path).parentFile == parent }
    }
}
