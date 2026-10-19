package com.joseleandro.scaniqo.data.documentForms

import android.content.Context
import com.joseleandro.scaniqo.data.documentOriginal.OriginalPdfFiles
import com.joseleandro.scaniqo.domain.model.PdfFormDocument
import com.joseleandro.scaniqo.domain.model.PdfFormExport
import com.joseleandro.scaniqo.domain.repository.PdfFormRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidPdfFormRepository(context: Context) : PdfFormRepository {
    private val files = OriginalPdfFiles(context.applicationContext, "form-tools")

    override suspend fun open(uri: String): PdfFormDocument {
        var owned: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val (file, name) = files.copyOriginal(uri)
                owned = file
                files.load(file).use { pdf ->
                    val (fields, unsupported) = PdfFormFields.inspect(pdf)
                    currentCoroutineContext().ensureActive()
                    PdfFormDocument(file.name, name, fields, unsupported)
                }
            }
        } catch (failure: Throwable) {
            owned?.let(files::discard)
            throw failure
        }
    }

    override suspend fun fill(document: PdfFormDocument, values: Map<String, List<String>>): PdfFormExport {
        var owned: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val source = files.file(document.sessionId)
                val result = files.createDerived(source)
                owned = result
                files.load(source).use { pdf ->
                    PdfFormFields.fill(pdf, values)
                    currentCoroutineContext().ensureActive()
                    pdf.save(result)
                }
                require(result.length() <= OriginalPdfFiles.MAX_BYTES) { "O formulário preenchido excede 50 MB." }
                currentCoroutineContext().ensureActive()
                PdfFormExport(result.name, document.name.substringBeforeLast('.').take(120) + " - preenchido.pdf")
            }
        } catch (failure: Throwable) {
            owned?.let(files::discard)
            throw failure
        }
    }

    override suspend fun export(result: PdfFormExport, destinationUri: String) = withContext(Dispatchers.IO) {
        files.export(files.file(result.id), destinationUri)
    }
    override fun discard(document: PdfFormDocument) { files.discard(files.file(document.sessionId)) }
    override fun discard(result: PdfFormExport) { files.discard(files.file(result.id)) }
}
