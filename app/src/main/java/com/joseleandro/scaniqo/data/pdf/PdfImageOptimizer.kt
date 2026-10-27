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
import java.io.IOException
import kotlinx.coroutines.CancellationException
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
                // Até construir um XObject pode exigir um codec opcional (por exemplo JPX).
                // Manter o recurso original permite otimizar o restante do documento.
                when (val item = readImageResource { resources.getXObject(name) }) {
                    is PDFormXObject -> if (visited.add(item.cosObject)) visit(item.resources, depth + 1)
                    is PDImageXObject -> {
                        val replacement = optimized[item.cosObject] ?: shrink(document, item, settings)
                            .also { optimized[item.cosObject] = it }
                        if (replacement !== item) resources.put(name, replacement)
                    }
                }
            }
        }
        val pages = PdfPageIndex(document, currentCoroutineContext())
        pageIndices.forEach { index ->
            currentCoroutineContext().ensureActive()
            visit(pages[index].resources, 0)
        }
    }

    private suspend fun shrink(
        document: PDDocument,
        image: PDImageXObject,
        settings: PdfExportSettings,
    ): PDImageXObject {
        if (image.isStencil || image.bitsPerComponent <= 1 ||
            image.cosObject.containsKey(COSName.SMASK) ||
            image.cosObject.containsKey(COSName.MASK) ||
            image.cosObject.getInt(COSName.getPDFName("SMaskInData"), 0) != 0 ||
            max(image.width, image.height) < 500
        ) return image
        val context = currentCoroutineContext()
        val decoded = readImageResource {
            if (PdfCompressionImageDecoder.canDecodeSafely(image)) {
                PdfCompressionImageDecoder.decode(image, settings.maxImageSide, context::ensureActive)
            } else null
        } ?: return image
        var scaled: Bitmap? = null
        try {
            currentCoroutineContext().ensureActive()
            val (width, height) = settings.dimensions(decoded.width, decoded.height)
            val bitmap = if (width == decoded.width && height == decoded.height) decoded else {
                Bitmap.createScaledBitmap(decoded, width, height, true).also { scaled = it }
            }
            // Erros de escrita e de memória continuam fatais: só a leitura do recurso é opcional.
            val replacement = JPEGFactory.createFromImage(document, bitmap, settings.jpegQuality)
            currentCoroutineContext().ensureActive()
            if (replacement.cosObject.length >= image.cosObject.length) return image
            appearanceKeys.forEach { key ->
                image.cosObject.getItem(key)?.let { replacement.cosObject.setItem(key, it) }
            }
            return replacement
        } finally {
            scaled?.recycle()
            decoded.recycle()
        }
    }

    private inline fun <T> readImageResource(read: () -> T): T? = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        // PDFs externos podem conter codecs ou parâmetros que o decodificador não suporta.
        // Não capturar Error: OutOfMemoryError e falhas da VM devem interromper a operação.
        null
    }

    private val appearanceKeys = listOf(
        "OC", "Interpolate", "Intent", "Metadata", "StructParent",
    ).map(COSName::getPDFName)
}
