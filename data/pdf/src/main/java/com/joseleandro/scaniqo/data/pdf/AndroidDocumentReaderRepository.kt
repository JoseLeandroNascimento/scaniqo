package com.joseleandro.scaniqo.data.pdf

import android.content.Context
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfImportException
import com.joseleandro.scaniqo.domain.model.PdfPasswordRequiredException
import com.joseleandro.scaniqo.domain.model.ReadableDocument
import com.joseleandro.scaniqo.domain.repository.DocumentReaderRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidDocumentReaderRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentReaderRepository {
    private val files = PdfWorkspaceFiles(context)
    private val composer = PdfBoxComposer(files)
    private val ownedSessions = Collections.synchronizedSet(mutableSetOf<String>())

    override suspend fun open(document: DocumentModel, password: String?): ReadableDocument {
        if (!document.isProtected) return ReadableDocument(document.pdfUri, document.pageCount)
        if (password.isNullOrEmpty()) throw PdfPasswordRequiredException()
        var directory: File? = null
        try {
            return withContext(ioDispatcher) {
                files.initialize()
                val session = files.createWorkspace().also { directory = it }
                val input = File(session, "encrypted.pdf")
                val output = File(session, "readable.pdf")
                files.copy(document.pdfUri, input)
                val pageCount = try {
                    val memory = MemoryUsageSetting.setupMixed(8L * 1024 * 1024, 200L * 1024 * 1024)
                        .setTempDir(session)
                    val loaded = try {
                        PDDocument.load(input, password, memory)
                    } catch (_: InvalidPasswordException) {
                        throw PdfPasswordRequiredException()
                    }
                    loaded.use { pdf ->
                        if (!pdf.isEncrypted) {
                            throw PdfImportException("A proteção do arquivo não corresponde ao cadastro.")
                        }
                        if (pdf.numberOfPages !in 1..PdfWorkspaceFiles.MAX_PAGES) {
                            throw PdfImportException("O documento deve conter entre 1 e 100 páginas.")
                        }
                        composer.flattenVisualAnnotations(pdf)
                        pdf.isAllSecurityToBeRemoved = true
                        val context = currentCoroutineContext()
                        output.outputStream().use { stream ->
                            pdf.save(PdfCancellableOutputStream(stream, context, PdfWorkspaceFiles.MAX_BYTES) {
                                PdfImportException("O PDF excede 50 MB.")
                            })
                        }
                        pdf.numberOfPages
                    }
                } finally {
                    input.delete()
                }
                files.checkSize(output)
                currentCoroutineContext().ensureActive()
                ownedSessions.add(session.name)
                ReadableDocument(files.uri(output), pageCount, session.name)
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + ioDispatcher) {
                directory?.let { session ->
                    ownedSessions.remove(session.name)
                    files.deleteWorkspace(session)
                }
            }
            throw failure
        }
    }

    override suspend fun close(document: ReadableDocument) {
        val sessionId = document.sessionId ?: return
        if (!ownedSessions.remove(sessionId)) return
        withContext(NonCancellable + ioDispatcher) {
            files.deleteWorkspace(File(files.workspaceRoot, sessionId))
        }
    }
}
