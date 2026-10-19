package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max

/** Reduz apenas imagens opacas. Máscaras, assinaturas transparentes e vetores ficam intactos. */
internal object PdfImageOptimizer {
    suspend fun optimize(
        document: PDDocument,
        quality: PdfExportQuality,
        pageIndices: List<Int> = (0 until document.numberOfPages).toList(),
    ) {
        if (quality == PdfExportQuality.HIGH_QUALITY) return
        val settings = PdfExportSettings.forQuality(quality)
        val visited = mutableSetOf<COSStream>()
        val optimized = mutableMapOf<COSStream, PDImageXObject>()
        suspend fun visit(resources: PDResources?, depth: Int) {
            if (resources == null || depth > 16) return
            for (name in resources.xObjectNames.toList()) {
                currentCoroutineContext().ensureActive()
                when (val item = resources.getXObject(name)) {
                    is PDFormXObject -> if (visited.add(item.cosObject)) visit(item.resources, depth + 1)
                    is PDImageXObject -> {
                        val replacement = optimized[item.cosObject] ?: shrink(document, item, settings)
                            .also { optimized[item.cosObject] = it }
                        if (replacement !== item) resources.put(name, replacement)
                    }
                }
            }
        }
        pageIndices.forEach { index -> visit(document.getPage(index).resources, 0) }
    }

    private suspend fun shrink(
        document: PDDocument,
        image: PDImageXObject,
        settings: PdfExportSettings,
    ): PDImageXObject {
        if (image.isStencil || image.bitsPerComponent <= 1 ||
            image.cosObject.containsKey(COSName.SMASK) ||
            image.cosObject.containsKey(COSName.MASK) ||
            max(image.width, image.height) < 500
        ) return image
        // JPEG usa amostragem nativa; o caminho amostrado do PDFBox Android
        // interpreta incorretamente esse formato como pixels RGB brutos.
        val decoded = PdfCompressionImageDecoder.decode(image, settings.maxImageSide)
        var scaled: Bitmap? = null
        try {
            currentCoroutineContext().ensureActive()
            val (width, height) = settings.dimensions(decoded.width, decoded.height)
            val bitmap = if (width == decoded.width && height == decoded.height) decoded else {
                Bitmap.createScaledBitmap(decoded, width, height, true).also { scaled = it }
            }
            val replacement = JPEGFactory.createFromImage(document, bitmap, settings.jpegQuality)
            currentCoroutineContext().ensureActive()
            // Evita aumentar PDFs que já usam uma compressão mais eficiente.
            return if (replacement.cosObject.length < image.cosObject.length) replacement else image
        } finally {
            scaled?.recycle()
            decoded.recycle()
        }
    }
}
