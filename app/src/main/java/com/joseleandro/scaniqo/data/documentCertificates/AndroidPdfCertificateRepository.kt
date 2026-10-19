package com.joseleandro.scaniqo.data.documentCertificates

import android.content.Context
import android.security.KeyChain
import com.joseleandro.scaniqo.data.documentOriginal.OriginalPdfFiles
import com.joseleandro.scaniqo.domain.model.PdfCertificateDocument
import com.joseleandro.scaniqo.domain.model.PdfSignedExport
import com.joseleandro.scaniqo.domain.model.PdfSignatureIntegrity
import com.joseleandro.scaniqo.domain.repository.PdfCertificateRepository
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import java.io.File
import java.io.IOException
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidPdfCertificateRepository(context: Context) : PdfCertificateRepository {
    private val application = context.applicationContext
    private val files = OriginalPdfFiles(application, "certificate-tools")

    override suspend fun open(uri: String): PdfCertificateDocument {
        var owned: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val (file, name) = files.copyOriginal(uri)
                owned = file
                files.load(file).use { pdf ->
                    val signatures = PdfCertificateCrypto.inspect(file, pdf)
                    currentCoroutineContext().ensureActive()
                    PdfCertificateDocument(file.name, name, signatures,
                        canSign = signatures.isEmpty() && pdf.currentAccessPermission.canModify() &&
                            !pdf.documentCatalog.cosObject.containsKey(COSName.PERMS))
                }
            }
        } catch (failure: Throwable) {
            owned?.let(files::discard)
            throw failure
        }
    }

    override suspend fun sign(document: PdfCertificateDocument, certificateAlias: String): PdfSignedExport {
        var owned: File? = null
        try {
            return withContext(Dispatchers.IO) {
                require(document.canSign) { "Este documento não permite uma nova assinatura nesta ferramenta." }
                val key = KeyChain.getPrivateKey(application, certificateAlias)
                    ?: throw IOException("Selecione novamente o certificado no Android.")
                val chain = KeyChain.getCertificateChain(application, certificateAlias)?.toList()
                    ?.takeIf { it.isNotEmpty() } ?: throw IOException("A cadeia do certificado não está disponível.")
                val source = files.file(document.sessionId)
                val output = files.createDerived(source)
                owned = output
                val coroutineContext = currentCoroutineContext()
                files.load(source).use { pdf ->
                    require(pdf.signatureDictionaries.isEmpty() &&
                        !pdf.documentCatalog.cosObject.containsKey(COSName.PERMS) &&
                        pdf.currentAccessPermission.canModify()) {
                        "Este PDF já foi assinado ou restringe novas assinaturas."
                    }
                    val signature = PDSignature().apply {
                        setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                        setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                        name = chain.first().subjectX500Principal.name
                        signDate = Calendar.getInstance()
                    }
                    SignatureOptions().use { options ->
                        options.preferredSignatureSize = 64 * 1024
                        pdf.addSignature(signature, { content ->
                            coroutineContext.ensureActive()
                            try { PdfCertificateCrypto.sign(content, key, chain) }
                            catch (failure: Exception) { throw IOException("Não foi possível usar este certificado para assinar.", failure) }
                        }, options)
                        output.outputStream().use(pdf::saveIncremental)
                    }
                }
                currentCoroutineContext().ensureActive()
                require(output.length() <= OriginalPdfFiles.MAX_BYTES) { "O PDF assinado excede 50 MB." }
                files.load(output).use { signed ->
                    val signatures = PdfCertificateCrypto.inspect(output, signed)
                    require(signatures.size == 1 && signatures.single().integrity == PdfSignatureIntegrity.VERIFIED &&
                        signatures.single().coversWholeDocument) { "A verificação do arquivo assinado falhou." }
                }
                PdfSignedExport(output.name, document.name.substringBeforeLast('.').take(120) + " - assinado.pdf")
            }
        } catch (failure: Throwable) {
            owned?.let(files::discard)
            throw failure
        }
    }

    override suspend fun export(result: PdfSignedExport, destinationUri: String) = withContext(Dispatchers.IO) {
        files.export(files.file(result.id), destinationUri)
    }
    override fun discard(document: PdfCertificateDocument) { files.discard(files.file(document.sessionId)) }
    override fun discard(result: PdfSignedExport) { files.discard(files.file(result.id)) }
}
