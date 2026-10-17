package com.joseleandro.scaniqo.data.pdf

import android.content.Context
import android.net.Uri
import com.joseleandro.scaniqo.domain.model.PdfImportException
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class PdfWorkspaceFiles(context: Context) {
    private val app = context.applicationContext
    val documentsRoot = File(app.filesDir, "documents")
    val workspaceRoot = File(app.cacheDir, "pdf_workspaces")
    val resolver = app.contentResolver

    suspend fun initialize() = PdfWorkspaceCache.initialize(workspaceRoot)

    fun createWorkspace(): File = createDirectory(workspaceRoot)
    fun createDocument(): File = createDirectory(documentsRoot)

    fun directory(workspace: PdfWorkspace): File {
        val directory = file(workspace.directoryUri)
        require(directory.parentFile == workspaceRoot.canonicalFile &&
            directory.name == workspace.workspaceId && isUuid(directory.name) &&
            directory.isDirectory) { "A sessão de edição expirou. Abra o documento novamente." }
        return directory
    }

    fun source(workspace: PdfWorkspace, uri: String): File {
        val file = file(uri)
        require(file.parentFile == directory(workspace) && file.isFile) {
            "Uma página da sessão não está disponível."
        }
        checkSize(file)
        return file
    }

    fun ownedDocumentDirectory(pdfUri: String): File? {
        val uri = Uri.parse(pdfUri)
        if (uri.scheme != "file") return null
        val pdf = file(pdfUri)
        val directory = pdf.parentFile ?: return null
        return directory.takeIf {
            pdf.name == "document.pdf" && it.parentFile == documentsRoot.canonicalFile &&
                isUuid(it.name)
        }
    }

    fun file(uri: String): File {
        val parsed = Uri.parse(uri)
        require(parsed.scheme == "file") { "O arquivo precisa estar no armazenamento privado." }
        return File(requireNotNull(parsed.path)).canonicalFile
    }

    fun uri(file: File): String = Uri.fromFile(file).toString()

    suspend fun copy(
        uri: String,
        target: File,
        maxBytes: Long = MAX_BYTES,
        limitFailure: () -> IOException = {
            PdfImportException("O arquivo deve ter no máximo ${maxBytes / (1024 * 1024)} MB.")
        }
    ) {
        resolver.openInputStream(Uri.parse(uri))?.use { input ->
            target.outputStream().use { output ->
                val bytes = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(bytes)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) {
                        throw limitFailure()
                    }
                    output.write(bytes, 0, count)
                }
            }
        } ?: throw IOException("Não foi possível abrir o arquivo selecionado.")
    }

    fun checkSize(file: File) {
        if (file.length() > MAX_BYTES) throw PdfImportException("O PDF deve ter no máximo 50 MB.")
    }

    /** Só remove os arquivos diretos da sessão, sem seguir diretórios ou links externos. */
    fun deleteWorkspace(directory: File) {
        if (directory.canonicalFile.parentFile != workspaceRoot.canonicalFile ||
            !isUuid(directory.name)) return
        directory.listFiles().orEmpty().forEach { file ->
            if (file.canonicalFile.parentFile == directory.canonicalFile && file.isFile) file.delete()
        }
        directory.delete()
    }

    fun clearPreviews(workspace: PdfWorkspace) {
        val directory = directory(workspace)
        directory.listFiles().orEmpty().forEach { file ->
            val generatedPreview = (file.name.startsWith("preview_") ||
                file.name.startsWith("page_preview_") || file.name.startsWith("final_preview_")) &&
                file.name.endsWith(".pdf")
            val generatedRaster = (file.name.startsWith("raster_") ||
                file.name.startsWith("export_raster_")) &&
                (file.name.endsWith(".jpg") || file.name.endsWith(".png"))
            if ((generatedPreview || generatedRaster) &&
                file.canonicalFile.parentFile == directory && file.isFile) file.delete()
        }
    }

    /** Preserva a prévia exibida até que a interface tenha adotado a nova URI. */
    fun retainRecentPreviews(
        workspace: PdfWorkspace,
        current: File,
        retainedUri: String?,
        additionalRetainedUris: Set<String> = emptySet(),
    ) {
        val directory = directory(workspace)
        val candidates = directory.listFiles().orEmpty().filter {
            (it.name.startsWith("preview_") || it.name.startsWith("final_preview_")) &&
                it.name.endsWith(".pdf") &&
                it.canonicalFile.parentFile == directory && it.isFile
        }
        val retained = (additionalRetainedUris + listOfNotNull(retainedUri))
            .mapNotNull { runCatching { file(it) }.getOrNull() }.toSet()
        val keep = mutableSetOf(current.canonicalFile)
        keep += candidates.map { it.canonicalFile }.filter { it in retained }
        candidates.filter { it.canonicalFile !in keep }
            .maxByOrNull { it.lastModified() }?.let { keep += it.canonicalFile }
        candidates.filter { it.canonicalFile !in keep }.forEach { it.delete() }
    }

    private fun createDirectory(root: File): File {
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Armazenamento indisponível.")
        val directory = File(root, UUID.randomUUID().toString())
        if (!directory.mkdir()) throw IOException("Não foi possível iniciar a edição.")
        return directory.canonicalFile
    }

    private fun isUuid(value: String) = runCatching { UUID.fromString(value) }.isSuccess

    companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
        const val MAX_PAGES = 100
    }
}
