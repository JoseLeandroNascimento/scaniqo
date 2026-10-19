package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Reaproveita o compressor do editor sem renderizar páginas nem alterar texto ou formulários. */
internal object PdfCompressionWriter {
    suspend fun write(
        source: PDDocument,
        target: File,
        level: PdfCompressionLevel,
        password: String?,
    ) {
        require(source.currentAccessPermission.canModify()) {
            "A senha deste PDF não permite modificar o documento."
        }
        require(source.signatureDictionaries.isEmpty() &&
            source.documentCatalog.cosObject.getDictionaryObject(COSName.PERMS) == null) {
            "Este PDF possui assinatura digital. Use uma cópia sem assinatura para comprimir."
        }
        val quality = when (level) {
            PdfCompressionLevel.BALANCED -> PdfExportQuality.BALANCED
            PdfCompressionLevel.STRONG -> PdfExportQuality.SMALL
        }
        PdfImageOptimizer.optimize(source, quality)
        currentCoroutineContext().ensureActive()
        if (password != null) PdfProtection.apply(source, password)
        val context = currentCoroutineContext()
        target.outputStream().buffered().use { stream ->
            PdfCancellableOutputStream(stream, context, 50L * 1024 * 1024) {
                java.io.IOException("O resultado excedeu o limite de 50 MB.")
            }.use(source::save)
        }
    }
}
