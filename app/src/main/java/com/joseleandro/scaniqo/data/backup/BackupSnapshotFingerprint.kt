package com.joseleandro.scaniqo.data.backup

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Compara o conteúdo e os metadados exportados; tags, miniaturas e datas do arquivo não geram cópias. */
class BackupSnapshotFingerprint(
    private val documents: DocumentRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun current(): String? = withContext(ioDispatcher) {
        val snapshot = snapshot()
        if (snapshot.isEmpty()) return@withContext null
        val files = snapshot.map { document ->
            currentCoroutineContext().ensureActive()
            FileSnapshot.capture(File(URI(document.pdfUri)))
        }
        val buffer = ByteArray(64 * 1024)
        val hashes = snapshot.zip(files).map { (document, source) ->
            currentCoroutineContext().ensureActive()
            val digest = MessageDigest.getInstance("SHA-256")
            var readBytes = 0L
            source.file.inputStream().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    readBytes += count
                    check(readBytes <= source.length) { SNAPSHOT_CHANGED }
                    digest.update(buffer, 0, count)
                }
            }
            check(readBytes == source.length && source.isUnchanged()) { SNAPSHOT_CHANGED }
            documentHash(
                document.name, document.pageCount, document.createdAt, document.protected,
                source.length, digest.digest().toHex(),
            )
        }
        // A biblioteca e os arquivos precisam continuar iguais após ler todos os PDFs.
        check(snapshot == snapshot()) { SNAPSHOT_CHANGED }
        files.forEach { source ->
            currentCoroutineContext().ensureActive()
            check(source.isUnchanged()) { SNAPSHOT_CHANGED }
        }
        aggregate(hashes)
    }

    suspend fun fromArchive(file: File): String = withContext(ioDispatcher) {
        val manifest = DocumentBackupArchive().inspect(file)
        aggregate(manifest.documents.map { document ->
            currentCoroutineContext().ensureActive()
            val pdf = document.files.single { it.name == "document.pdf" }
            documentHash(
                document.name, document.pageCount, document.createdAt, document.protected,
                pdf.size, pdf.sha256,
            )
        })
    }

    private fun documentHash(
        name: String,
        pageCount: Int,
        createdAt: Long,
        protected: Boolean,
        size: Long,
        pdfSha256: String,
    ): String {
        val metadata = ByteArrayOutputStream()
        DataOutputStream(metadata).use { output ->
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            output.writeInt(nameBytes.size)
            output.write(nameBytes)
            output.writeInt(pageCount)
            output.writeLong(createdAt)
            output.writeBoolean(protected)
            output.writeLong(size)
            output.write(pdfSha256.toByteArray(Charsets.US_ASCII))
        }
        return MessageDigest.getInstance("SHA-256").digest(metadata.toByteArray()).toHex()
    }

    private fun aggregate(hashes: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(PREFIX.toByteArray(Charsets.US_ASCII))
        hashes.sorted().forEach { hash -> digest.update(hash.toByteArray(Charsets.US_ASCII)) }
        return PREFIX + digest.digest().toHex()
    }

    private suspend fun snapshot(): List<SnapshotDocument> =
        documents.observeDocuments().first()
            .filter { it.deletedAt == null }
            .map { document -> SnapshotDocument(document) }
            .sortedWith(compareBy(
                { it.pdfUri }, { it.name }, { it.pageCount }, { it.createdAt }, { it.protected },
            ))

    private data class SnapshotDocument(
        val pdfUri: String,
        val name: String,
        val pageCount: Int,
        val createdAt: Long,
        val protected: Boolean,
    ) {
        constructor(document: DocumentModel) : this(
            document.pdfUri, document.name, document.pageCount, document.createdAt, document.isProtected,
        )
    }

    private data class FileSnapshot(val file: File, val length: Long, val modifiedAt: Long) {
        fun isUnchanged(): Boolean = file.isFile && file.length() == length && file.lastModified() == modifiedAt

        companion object {
            fun capture(file: File): FileSnapshot {
                require(file.isFile) { "Um dos PDFs não está disponível para backup." }
                return FileSnapshot(file, file.length(), file.lastModified())
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val PREFIX = "pdf-content-v2:"
        private const val SNAPSHOT_CHANGED = "A biblioteca foi alterada durante a verificação do backup. Tente novamente."

        fun recognizes(value: String?): Boolean = value != null &&
            value.startsWith(PREFIX) && value.length == PREFIX.length + 64 &&
            value.drop(PREFIX.length).all { it in '0'..'9' || it in 'a'..'f' }
    }
}
