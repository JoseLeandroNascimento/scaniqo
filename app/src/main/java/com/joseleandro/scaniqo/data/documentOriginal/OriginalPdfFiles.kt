package com.joseleandro.scaniqo.data.documentOriginal

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Isolated originals for operations that must retain forms or signature bytes. */
internal class OriginalPdfFiles(context: Context, feature: String) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.cacheDir, feature).apply { mkdirs() }
    private val origins = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun create(): File = File(directory, UUID.randomUUID().toString() + ".pdf")
    fun createDerived(source: File): File = create().also { target ->
        origins[source.name]?.let { origins[target.name] = it }
    }
    fun discard(file: File) {
        origins.remove(file.name)
        file.delete()
    }
    fun file(id: String): File {
        require(id.matches(Regex("[a-fA-F0-9-]{36}\\.pdf"))) { "Arquivo temporário inválido." }
        return File(directory, id)
    }
    suspend fun copyOriginal(uri: String): Pair<File, String> {
        // These sessions live only in ViewModels, so prior-process files cannot be resumed.
        synchronized(initializedDirectories) {
            if (initializedDirectories.add(directory.absolutePath)) {
                directory.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
        val target = create()
        try {
            resolver.openInputStream(uri.toUri())?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IOException("Selecione um PDF de até 50 MB.")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Não foi possível abrir o arquivo selecionado.")
            val name = runCatching {
                resolver.query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.take(160) ?: "Documento.pdf"
            origins[target.name] = uri
            return target to name
        } catch (failure: Throwable) {
            discard(target)
            throw failure
        }
    }

    fun load(file: File): PDDocument {
        if (!file.isFile || file.length() > MAX_BYTES) throw IOException("O PDF não está mais disponível ou excede 50 MB.")
        val document = try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly().setTempDir(directory))
        } catch (_: InvalidPasswordException) {
            throw IOException("Abra uma cópia sem senha para usar esta ferramenta.")
        }
        try {
            require(document.numberOfPages in 1..100) { "Selecione um PDF com 1 a 100 páginas." }
            require(!document.isEncrypted) { "Abra uma cópia sem senha para usar esta ferramenta." }
            return document
        } catch (failure: Throwable) {
            document.close()
            throw failure
        }
    }

    suspend fun export(file: File, uri: String) {
        require(file.isFile && file.length() <= MAX_BYTES) { "O resultado não está mais disponível ou excede 50 MB." }
        require(uri != origins[file.name]) { "Escolha um arquivo novo para preservar o PDF original." }
        resolver.openOutputStream(uri.toUri(), "wt")?.use { output ->
            file.inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw IOException("Não foi possível salvar no local selecionado.")
    }

    companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
        private val initializedDirectories = mutableSetOf<String>()
    }
}
