package com.joseleandro.scaniqo.data.backup

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.joseleandro.scaniqo.data.pdf.PdfProjectRecipe
import com.joseleandro.scaniqo.domain.model.DocumentBackupProgress
import com.joseleandro.scaniqo.domain.model.DocumentBackupRestoreResult
import com.joseleandro.scaniqo.domain.model.PreparedDocumentBackup
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.domain.repository.DocumentBackupRepository
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import com.joseleandro.scaniqo.domain.repository.TagRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Restaura cópias novas; nunca substitui documentos, senhas ou a base SQLite do aparelho. */
class AndroidDocumentBackupRepository internal constructor(
    private val documentsRoot: File,
    private val temporaryRoot: File,
    private val documents: DocumentRepository,
    private val tags: TagRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentBackupRepository {
    constructor(
        context: Context,
        documents: DocumentRepository,
        tags: TagRepository,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        File(context.applicationContext.filesDir, "documents"),
        File(context.applicationContext.cacheDir, "document_backups"),
        documents, tags, ioDispatcher,
    )

    private val mutex = Mutex()
    private val archive = DocumentBackupArchive()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createBackup(onProgress: (DocumentBackupProgress) -> Unit): PreparedDocumentBackup {
        var target: File? = null
        try {
            return withContext(ioDispatcher) {
                mutex.withLock {
                    ensureRoots()
                    val stored = documents.observeDocuments().first()
                    require(stored.isNotEmpty()) { "Não há documentos salvos para incluir no backup." }
                    val storedTags = tags.observeTags().first()
                    val tagKeys = storedTags.associate { it.id to UUID.randomUUID().toString() }
                    val sources = stored.map { document ->
                        currentCoroutineContext().ensureActive()
                        val directory = ownedDocumentDirectory(document.pdfUri)
                        val filenames = if (document.isProtected) listOf("document.pdf") else {
                            directory.listFiles().orEmpty().filter {
                                it.isFile && (it.name in setOf("document.pdf", "sources.pdf", "recipe.json", "thumbnail.jpg") ||
                                    it.name.matches(Regex("signature_[a-fA-F0-9-]+\\.png")))
                            }.map { it.name }.sorted()
                        }
                        require(document.tagId == null || document.tagId in tagKeys) {
                            "As tags foram alteradas durante o backup. Tente novamente."
                        }
                        val metadata = BackupDocument(
                            UUID.randomUUID().toString(), document.name, document.pageCount, document.createdAt,
                            document.tagId?.let(tagKeys::getValue), document.isProtected,
                            editableProject = "sources.pdf" in filenames,
                        )
                        validateProject(directory, metadata, filenames)
                        BackupDocumentSource(metadata, directory, filenames)
                    }
                    val output = File.createTempFile("backup_", ".scaniqo", temporaryRoot).also { target = it }
                    onProgress(DocumentBackupProgress(0, sources.size))
                    val manifest = archive.write(output, sources, storedTags.map {
                        BackupTag(tagKeys.getValue(it.id), it.name, it.color.toArgb(), it.iconKey)
                    }, onProgress)
                    // Arquivos permanentes são imutáveis. Se trocaram durante a cópia, não publicamos o snapshot.
                    check(documents.observeDocuments().first() == stored && tags.observeTags().first() == storedTags) {
                        "A biblioteca foi alterada durante o backup. Tente novamente."
                    }
                    PreparedDocumentBackup(output, manifest.documents.size,
                        manifest.documents.count { it.protected }, manifest.documents.count { it.editableProject })
                }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + ioDispatcher) { target?.delete() }
            throw failure
        }
    }

    override suspend fun createRestoreFile(): File {
        var target: File? = null
        try {
            return withContext(ioDispatcher) {
                mutex.withLock {
                    ensureRoots()
                    File.createTempFile("restore_", ".scaniqo", temporaryRoot).also { target = it }
                }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + ioDispatcher) { target?.delete() }
            throw failure
        }
    }

    override suspend fun restoreBackup(
        file: File,
        onProgress: (DocumentBackupProgress) -> Unit,
    ): DocumentBackupRestoreResult {
        val createdScans = mutableListOf<ScannedDocument>()
        val createdTags = mutableListOf<Long>()
        try {
            return withContext(ioDispatcher) {
                mutex.withLock {
                    ensureRoots()
                    requireOwnedTemporary(file)
                    val stage = File(temporaryRoot, "stage_" + UUID.randomUUID())
                    check(stage.mkdir()) { "Não foi possível preparar a restauração." }
                    try {
                        val manifest = archive.extract(file, stage)
                        // Valida todos os projetos antes de alterar a biblioteca.
                        manifest.documents.forEach {
                            currentCoroutineContext().ensureActive()
                            validateProject(File(stage, it.key), it, it.files.map(BackupFile::name))
                        }
                        onProgress(DocumentBackupProgress(0, manifest.documents.size))
                        val existingTags = tags.observeTags().first()
                        val mappedTags = linkedMapOf<String, Long>()
                        for (tag in manifest.tags) {
                            currentCoroutineContext().ensureActive()
                            val match = existingTags.firstOrNull {
                                it.name == tag.name && it.color.toArgb() == tag.colorArgb && it.iconKey == tag.iconKey
                            }
                            val id = match?.id ?: withContext(NonCancellable) {
                                tags.create(TagModel(name = tag.name, color = Color(tag.colorArgb), iconKey = tag.iconKey))
                                    .also(createdTags::add)
                            }
                            mappedTags[tag.key] = id
                        }
                        manifest.documents.forEachIndexed { index, item ->
                            currentCoroutineContext().ensureActive()
                            val directory = File(documentsRoot, UUID.randomUUID().toString())
                            check(directory.mkdir()) { "Não foi possível preparar o documento restaurado." }
                            val pdf = File(directory, "document.pdf")
                            val scan = ScannedDocument(
                                pdfUri = pdf.toURI().toString(), pageCount = item.pageCount, createdAt = item.createdAt,
                                sizeBytes = item.files.single { it.name == "document.pdf" }.size,
                                thumbnailUri = if (!item.protected && item.files.any { it.name == "thumbnail.jpg" })
                                    File(directory, "thumbnail.jpg").toURI().toString() else null,
                                // IDs locais do cofre não viajam entre aparelhos; o fluxo existente pedirá a senha original.
                                protectionKeyId = if (item.protected) "restored:" + UUID.randomUUID() else null,
                            ).also(createdScans::add)
                            item.files.forEach { entry ->
                                copy(File(File(stage, item.key), entry.name), File(directory, entry.name))
                            }
                            withContext(NonCancellable) {
                                documents.create(scan, item.name, item.tagKey?.let(mappedTags::getValue))
                            }
                            onProgress(DocumentBackupProgress(index + 1, manifest.documents.size))
                        }
                        currentCoroutineContext().ensureActive()
                        DocumentBackupRestoreResult(manifest.documents.size,
                            manifest.documents.count { it.protected }, manifest.documents.count { it.editableProject })
                    } finally {
                        withContext(NonCancellable) { stage.deleteRecursively() }
                    }
                }
            }
        } catch (failure: Throwable) {
            // Inclui cancelamento na entrega do resultado ao dispatcher chamador.
            withContext(NonCancellable + ioDispatcher) {
                mutex.withLock { rollback(createdScans, createdTags, failure) }
            }
            throw failure
        }
    }

    override suspend fun discard(file: File) = withContext(ioDispatcher) {
        mutex.withLock {
            requireOwnedTemporary(file)
            check(!file.exists() || file.delete()) { "Não foi possível remover o arquivo temporário." }
        }
    }

    private suspend fun rollback(scans: List<ScannedDocument>, tagIds: List<Long>, failure: Throwable) {
        var documentsRemoved = true
        for (scan in scans.asReversed()) {
            try {
                val stored = documents.observeDocuments().first().firstOrNull { it.pdfUri == scan.pdfUri }
                if (stored != null) documents.delete(stored)
                documents.discardScan(scan)
                ownedDocumentDirectory(scan.pdfUri).deleteRecursively()
            } catch (cleanup: Exception) {
                documentsRemoved = false
                failure.addSuppressed(cleanup)
            }
        }
        if (documentsRemoved) for (id in tagIds.asReversed()) {
            try { tags.findById(id)?.let { tags.delete(it) } }
            catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        }
    }

    private suspend fun validateProject(directory: File, document: BackupDocument, names: List<String>) {
        require("document.pdf" in names && names.distinct().size == names.size)
        names.forEach { name ->
            val file = File(directory, name).canonicalFile
            require(file.parentFile == directory.canonicalFile && file.isFile &&
                file.length() in 1..DocumentBackupArchive.fileLimit(name)) { "Um arquivo do documento é inválido." }
        }
        require(!document.protected || names == listOf("document.pdf")) {
            "Documentos protegidos só podem incluir o PDF cifrado."
        }
        val pages = inspectPdf(File(directory, "document.pdf"), document.protected)
        require(pages == null || pages == document.pageCount) { "A quantidade de páginas não corresponde ao backup." }
        if (document.protected) return
        val sourcePages = if ("sources.pdf" in names) inspectPdf(File(directory, "sources.pdf"), false) else null
        require("recipe.json" !in names || sourcePages != null) { "As fontes do projeto estão ausentes." }
        if ("recipe.json" in names) {
            val recipe = json.decodeFromString<PdfProjectRecipe>(File(directory, "recipe.json").readText())
            recipe.validate()
            require(recipe.pages.size == document.pageCount && recipe.pages.map { it.id }.distinct().size == recipe.pages.size)
            recipe.pages.forEach { page ->
                require(page.sourcePageIndex in 0 until requireNotNull(sourcePages) && page.signatures.size <= 20)
                page.signatures.forEach { signature ->
                    require(signature.filename in names && signature.filename.matches(Regex("signature_[a-fA-F0-9-]+\\.png")))
                    require(listOf(signature.x, signature.y, signature.width, signature.height, signature.rotationDegrees)
                        .all { it.isFinite() })
                    require(signature.x >= 0f && signature.y >= 0f && signature.width > 0f && signature.height > 0f &&
                        signature.x + signature.width <= 1.001f && signature.y + signature.height <= 1.001f)
                }
            }
        } else if (sourcePages != null) {
            require(sourcePages == document.pageCount)
        }
    }

    private suspend fun inspectPdf(file: File, protected: Boolean): Int? {
        currentCoroutineContext().ensureActive()
        return try {
            PDDocument.load(file, "", MemoryUsageSetting.setupTempFileOnly()).use { pdf ->
                require(pdf.isEncrypted == protected) { "A proteção do PDF não corresponde ao backup." }
                require(pdf.numberOfPages in 1..100) { "O PDF possui uma quantidade inválida de páginas." }
                pdf.numberOfPages
            }
        } catch (password: InvalidPasswordException) {
            require(protected) { "Um projeto sem proteção contém um PDF que exige senha." }
            null
        }
    }

    private suspend fun copy(source: File, target: File) {
        source.inputStream().use { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) output.write(buffer, 0, count)
                }
            }
        }
    }

    private fun ownedDocumentDirectory(uri: String): File {
        val pdf = File(URI(uri)).canonicalFile
        val directory = requireNotNull(pdf.parentFile)
        require(pdf.name == "document.pdf" && directory.parentFile == documentsRoot.canonicalFile &&
            DocumentBackupArchive.validKey(directory.name)) { "O documento não está no armazenamento privado." }
        return directory
    }

    private fun requireOwnedTemporary(file: File) {
        require(file.canonicalFile.parentFile == temporaryRoot.canonicalFile &&
            file.name.matches(Regex("(backup|restore)_[a-zA-Z0-9-]+\\.scaniqo"))) {
            "O arquivo não pertence a esta operação de backup."
        }
    }

    private fun ensureRoots() {
        check(documentsRoot.isDirectory || documentsRoot.mkdirs())
        check(temporaryRoot.isDirectory || temporaryRoot.mkdirs())
    }
}
