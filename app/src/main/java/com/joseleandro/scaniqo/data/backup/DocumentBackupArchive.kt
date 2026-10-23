package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.DOCUMENT_NAME_MAX_LENGTH
import com.joseleandro.scaniqo.domain.model.DocumentBackupLimits
import com.joseleandro.scaniqo.domain.model.DocumentBackupProgress
import com.joseleandro.scaniqo.domain.model.TAG_NAME_MAX_LENGTH
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class DocumentBackupManifest(
    val format: String = "scaniqo.document-backup",
    val version: Int = 1,
    val createdAt: Long,
    val documents: List<BackupDocument>,
    val tags: List<BackupTag>,
)

@Serializable
internal data class BackupDocument(
    val key: String,
    val name: String,
    val pageCount: Int,
    val createdAt: Long,
    val tagKey: String?,
    val protected: Boolean,
    val editableProject: Boolean,
    val files: List<BackupFile> = emptyList(),
)

@Serializable
internal data class BackupTag(val key: String, val name: String, val colorArgb: Int, val iconKey: String)

@Serializable
internal data class BackupFile(val name: String, val size: Long, val sha256: String)

internal data class BackupDocumentSource(val document: BackupDocument, val directory: File, val filenames: List<String>)

/** Versão 2: PDFs finais e somente os metadados necessários à restauração e integridade. */
@Serializable
private data class BackupPdfManifest(
    val format: String = "scaniqo.document-backup",
    val version: Int = 2,
    val createdAt: Long,
    val documents: List<BackupPdfDocument>,
)

@Serializable
private data class BackupPdfDocument(
    val filename: String,
    val name: String,
    val pageCount: Int,
    val createdAt: Long,
    val protected: Boolean,
    val size: Long,
    val sha256: String,
)

@Serializable
private data class BackupFormatHeader(val format: String, val version: Int)

private data class DecodedBackup(
    val manifest: DocumentBackupManifest,
    val archivePaths: Map<String, String>,
)

/** Formato portátil fechado: somente os arquivos declarados, com limites antes e durante a leitura. */
internal class DocumentBackupArchive(
    private val maxArchiveBytes: Long = DocumentBackupLimits.MAX_ARCHIVE_BYTES,
    private val maxUncompressedBytes: Long = DocumentBackupLimits.MAX_UNCOMPRESSED_BYTES,
    private val maxManifestBytes: Long = DocumentBackupLimits.MAX_MANIFEST_BYTES,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun write(
        target: File,
        sources: List<BackupDocumentSource>,
        onProgress: (DocumentBackupProgress) -> Unit = {},
    ): DocumentBackupManifest {
        require(sources.isNotEmpty() && sources.size <= DocumentBackupLimits.MAX_DOCUMENTS) {
            "Há documentos demais ou nenhum documento para este backup."
        }
        val createdAt = System.currentTimeMillis()
        var total = 0L
        val documents = mutableListOf<BackupPdfDocument>()
        try {
            ZipOutputStream(target.outputStream().buffered().let { BoundedOutput(it, maxArchiveBytes) }).use { zip ->
                sources.forEachIndexed { index, source ->
                    currentCoroutineContext().ensureActive()
                    val input = File(source.directory, "document.pdf").canonicalFile
                    require(input.parentFile == source.directory.canonicalFile && input.isFile)
                    val limit = fileLimit("document.pdf")
                    require(input.length() in 1..limit) { "Um PDF excede o limite do backup." }
                    val filename = readablePdfFilename(source.document.name, index, sources.size)
                    zip.putNextEntry(ZipEntry(filename))
                    val digest = MessageDigest.getInstance("SHA-256")
                    val size = input.inputStream().use { stream ->
                        transfer(stream, zip, limit, digest) { count ->
                            total += count
                            require(total <= maxUncompressedBytes) { "O backup excede o limite de tamanho." }
                        }
                    }
                    zip.closeEntry()
                    documents += BackupPdfDocument(
                        filename = filename, name = source.document.name, pageCount = source.document.pageCount,
                        createdAt = source.document.createdAt, protected = source.document.protected,
                        size = size, sha256 = digest.hex(),
                    )
                    onProgress(DocumentBackupProgress(index + 1, sources.size))
                }
                val manifest = BackupPdfManifest(createdAt = createdAt, documents = documents)
                validatePdfManifest(manifest)
                val bytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
                require(bytes.size.toLong() <= maxManifestBytes && total + bytes.size <= maxUncompressedBytes) {
                    "O manifesto do backup excede o limite."
                }
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(bytes)
                zip.closeEntry()
            }
            currentCoroutineContext().ensureActive()
            return normalizePdfManifest(BackupPdfManifest(createdAt = createdAt, documents = documents)).manifest
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }

    suspend fun extract(
        archive: File,
        destination: File,
        onProgress: (DocumentBackupProgress) -> Unit = {},
    ): DocumentBackupManifest {
        require(archive.isFile && archive.length() in 1..maxArchiveBytes) { "O arquivo de backup é inválido ou muito grande." }
        require(destination.isDirectory && destination.listFiles().orEmpty().isEmpty())
        try {
            return ZipFile(archive).use { zip ->
                val entries = linkedMapOf<String, ZipEntry>()
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val entry = iterator.nextElement()
                    require(entries.size < MAX_ENTRIES && !entry.isDirectory &&
                        entries.put(entry.name, entry) == null) { "O backup contém entradas inválidas ou duplicadas." }
                }
                val manifestEntry = entries[MANIFEST] ?: error("O arquivo não contém um backup do Scaniqo.")
                require(manifestEntry.size in 1..maxManifestBytes) { "O manifesto do backup é inválido." }
                val manifestBytes = ByteArrayOutputStream().use { output ->
                    zip.getInputStream(manifestEntry).use { transfer(it, output, maxManifestBytes) }
                    output.toByteArray()
                }
                val decoded = decodeManifest(manifestBytes.toString(Charsets.UTF_8))
                val manifest = decoded.manifest
                val expected = decoded.archivePaths.values.toSet() + MANIFEST
                require(entries.keys == expected) { "O backup contém arquivos ausentes ou não declarados." }
                var total = manifestBytes.size.toLong()
                manifest.documents.forEachIndexed { index, document ->
                    val directory = File(destination, document.key)
                    require(directory.mkdir())
                    document.files.forEach { file ->
                        currentCoroutineContext().ensureActive()
                        val entry = entries.getValue(decoded.archivePaths.getValue(path(document.key, file.name)))
                        require(entry.size == file.size) { "O tamanho de um arquivo não corresponde ao manifesto." }
                        val target = File(directory, file.name).canonicalFile
                        require(target.parentFile == directory.canonicalFile)
                        val digest = MessageDigest.getInstance("SHA-256")
                        val count = zip.getInputStream(entry).use { input ->
                            target.outputStream().buffered().use { output ->
                                transfer(input, output, file.size, digest) { copied ->
                                    total += copied
                                    require(total <= maxUncompressedBytes) { "O backup excede o limite de tamanho." }
                                }
                            }
                        }
                        require(count == file.size && digest.hex() == file.sha256) {
                            "O backup está incompleto ou foi alterado."
                        }
                    }
                    onProgress(DocumentBackupProgress(index + 1, manifest.documents.size))
                }
                currentCoroutineContext().ensureActive()
                manifest
            }
        } catch (failure: Throwable) {
            destination.listFiles().orEmpty().forEach { it.deleteRecursively() }
            throw failure
        }
    }

    private fun decodeManifest(text: String): DecodedBackup {
        val header = json.decodeFromString<BackupFormatHeader>(text)
        require(header.format == "scaniqo.document-backup") { "O arquivo não contém um backup do Scaniqo." }
        return when (header.version) {
            1 -> {
                val manifest = json.decodeFromString<DocumentBackupManifest>(text)
                validateLegacyManifest(manifest)
                val paths = manifest.documents.flatMap { document ->
                    document.files.map { path(document.key, it.name) }
                }.associateWith { it }
                DecodedBackup(manifest, paths)
            }
            2 -> {
                val manifest = json.decodeFromString<BackupPdfManifest>(text)
                validatePdfManifest(manifest)
                normalizePdfManifest(manifest)
            }
            else -> error("Esta versão do backup não é compatível.")
        }
    }

    private fun normalizePdfManifest(manifest: BackupPdfManifest): DecodedBackup {
        val documents = manifest.documents.mapIndexed { index, item ->
            BackupDocument(
                key = UUID(0L, index.toLong() + 1).toString(), name = item.name, pageCount = item.pageCount,
                createdAt = item.createdAt, tagKey = null, protected = item.protected, editableProject = false,
                files = listOf(BackupFile("document.pdf", item.size, item.sha256)),
            )
        }
        val paths = documents.zip(manifest.documents).associate { (document, pdf) ->
            path(document.key, "document.pdf") to pdf.filename
        }
        return DecodedBackup(
            DocumentBackupManifest(version = 2, createdAt = manifest.createdAt, documents = documents, tags = emptyList()),
            paths,
        )
    }

    private fun validatePdfManifest(manifest: BackupPdfManifest) {
        require(manifest.format == "scaniqo.document-backup" && manifest.version == 2)
        require(manifest.documents.isNotEmpty() && manifest.documents.size <= DocumentBackupLimits.MAX_DOCUMENTS)
        require(manifest.documents.map { it.filename }.distinct().size == manifest.documents.size)
        var total = 0L
        manifest.documents.forEach { document ->
            require(document.name.isNotBlank() && document.name.length <= DOCUMENT_NAME_MAX_LENGTH &&
                document.pageCount in 1..100)
            require(document.filename.length in 1..160 && document.filename.endsWith(".pdf") &&
                document.filename.none { it.isISOControl() || it in "/\\\\:*?\"<>|" }) {
                "Nome de PDF inválido no backup."
            }
            require(document.size in 1..fileLimit("document.pdf") && document.sha256.matches(Regex("[a-f0-9]{64}")))
            total += document.size
            require(total <= maxUncompressedBytes)
        }
    }

    private fun readablePdfFilename(name: String, index: Int, count: Int): String {
        var stem = name.trim().map { character ->
            if (character.isISOControl() || character in "/\\\\:*?\"<>|") '_' else character
        }.joinToString("").trim(' ', '.')
        while (stem.endsWith(".pdf", ignoreCase = true)) stem = stem.dropLast(4).trimEnd(' ', '.')
        stem = stem.take(100).trimEnd(' ', '.').ifBlank { "documento" }
        val ordinal = (index + 1).toString().padStart(count.toString().length.coerceAtLeast(3), '0')
        return "$ordinal - $stem.pdf"
    }

    private fun validateLegacyManifest(manifest: DocumentBackupManifest) {
        require(manifest.format == "scaniqo.document-backup" && manifest.version == 1) {
            "Esta versão do backup não é compatível."
        }
        require(manifest.documents.size <= DocumentBackupLimits.MAX_DOCUMENTS && manifest.tags.size <= 10_000)
        require(manifest.documents.map { it.key }.distinct().size == manifest.documents.size)
        require(manifest.tags.map { it.key }.distinct().size == manifest.tags.size)
        val tags = manifest.tags.map { it.key }.toSet()
        manifest.tags.forEach {
            require(validKey(it.key) && it.name.isNotBlank() && it.name.length <= TAG_NAME_MAX_LENGTH)
            require(it.iconKey.length in 1..100 && it.iconKey.none(Char::isISOControl))
        }
        var total = 0L
        var count = 1
        manifest.documents.forEach { document ->
            require(validKey(document.key) && document.name.isNotBlank() &&
                document.name.length <= DOCUMENT_NAME_MAX_LENGTH && document.pageCount in 1..100)
            require(document.tagKey == null || document.tagKey in tags)
            val names = document.files.map { it.name }
            require(names.size == names.distinct().size && "document.pdf" in names)
            require(!document.protected || names == listOf("document.pdf")) {
                "Documentos protegidos não podem incluir fontes, assinaturas ou miniaturas."
            }
            require(document.editableProject == ("sources.pdf" in names) &&
                ("recipe.json" !in names || "sources.pdf" in names))
            document.files.forEach {
                require(it.size in 1..fileLimit(it.name) && it.sha256.matches(Regex("[a-f0-9]{64}")))
                total += it.size
                require(total <= maxUncompressedBytes)
                require(++count <= MAX_ENTRIES)
            }
        }
    }

    private suspend fun transfer(
        input: InputStream,
        output: OutputStream,
        limit: Long,
        digest: MessageDigest? = null,
        copied: (Long) -> Unit = {},
    ): Long {
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= limit) { "Um arquivo do backup excede o limite permitido." }
            copied(count.toLong())
            digest?.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        return total
    }

    private class BoundedOutput(output: OutputStream, private val limit: Long) : FilterOutputStream(output) {
        private var written = 0L
        override fun write(value: Int) {
            require(++written <= limit) { "O backup excede o limite de tamanho." }
            out.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            written += length
            require(written <= limit) { "O backup excede o limite de tamanho." }
            out.write(bytes, offset, length)
        }
    }

    companion object {
        const val MANIFEST = "manifest.json"
        private const val MAX_ENTRIES = 100_000
        fun path(key: String, filename: String) = "documents/$key/$filename"
        fun validKey(key: String) = runCatching { UUID.fromString(key).toString() == key }.getOrDefault(false)
        fun fileLimit(name: String): Long = when {
            name == "document.pdf" || name == "sources.pdf" -> 50L * 1024 * 1024
            name == "recipe.json" -> 4L * 1024 * 1024
            name == "thumbnail.jpg" || name.matches(Regex("signature_[a-fA-F0-9-]+\\.png")) -> 8L * 1024 * 1024
            else -> error("Nome de arquivo inválido no backup.")
        }
        private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
