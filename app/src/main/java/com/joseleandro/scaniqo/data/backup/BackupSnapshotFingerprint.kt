package com.joseleandro.scaniqo.data.backup

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

/** Detecta mudanças nos PDFs finais; tags, miniaturas e projetos de edição não geram outra cópia. */
class BackupSnapshotFingerprint(
    private val documents: DocumentRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun current(): String? = withContext(ioDispatcher) {
        val snapshot = documents.observeDocuments().first()
            .filter { it.deletedAt == null }
            .sortedWith(compareBy({ it.pdfUri }, { it.name }))
        if (snapshot.isEmpty()) return@withContext null
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(1)
            output.writeInt(snapshot.size)
            snapshot.forEach { document ->
                currentCoroutineContext().ensureActive()
                val file = File(URI(document.pdfUri))
                require(file.isFile) { "Um dos PDFs não está disponível para backup." }
                output.writeUTF(document.pdfUri)
                output.writeUTF(document.name)
                output.writeLong(document.sizeBytes)
                output.writeLong(file.length())
                output.writeLong(file.lastModified())
                output.writeBoolean(document.isProtected)
            }
        }
        MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
