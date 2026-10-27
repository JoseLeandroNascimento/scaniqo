package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.data.pdf.PdfPageGeometry
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLivePageNumber
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentLivePageGeometry
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Durante o gesto só muda a matriz da captura. Assinaturas e rodapé permanecem na folha A4.
 * A composição deve ter uma chave por página, evitando reutilizar imagens entre páginas distintas.
 */
@Composable
internal fun DocumentLivePagePreview(
    pixels: PdfInteractivePixels,
    layout: PdfPageLayout,
    signatures: List<PdfSignaturePlacement>,
    pageNumber: Int,
    pageCount: Int,
    showPageNumbers: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val raster by produceState<LiveRasterState>(LiveRasterState(), pixels) {
        var prepared: Bitmap? = null
        try {
            val next = withContext(Dispatchers.Default) {
                require(pixels.width > 0 && pixels.height > 0)
                require(pixels.width.toLong() * pixels.height == pixels.pixels.size.toLong())
                prepared = Bitmap.createBitmap(
                    pixels.pixels,
                    pixels.width,
                    pixels.height,
                    Bitmap.Config.ARGB_8888
                )
                ensureActive()
                LiveRaster(requireNotNull(prepared), pixels.sourceWidth, pixels.sourceHeight)
            }
            value = LiveRasterState(next)
        } catch (cancelled: CancellationException) {
            prepared?.recycle()
            throw cancelled
        } catch (_: Exception) {
            prepared?.recycle()
            value = value.copy(error = true)
        }
        // Um bitmap já publicado pode estar em uso pela GPU; sua liberação fica a cargo do GC.
    }
    val signatureUris = remember(signatures) { signatures.map { it.imageUri }.distinct() }
    val signatureAssets by produceState<LiveSignatureAssets>(LiveSignatureAssets(), signatureUris) {
        val previous = value.images.filterKeys { it in signatureUris }
        val decoded = mutableMapOf<String, Bitmap>()
        try {
            val next = withContext(Dispatchers.IO) {
                var failed = false
                signatureUris.forEach { uri ->
                    ensureActive()
                    if (uri !in previous) {
                        try {
                            decoded[uri] = decodeLiveSignature(context, uri)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failed = true
                        }
                    }
                }
                ensureActive()
                LiveSignatureAssets(previous + decoded, failed)
            }
            value = next
        } catch (cancelled: CancellationException) {
            decoded.values.forEach(Bitmap::recycle)
            throw cancelled
        }
    }
    val footer by produceState<LiveFooter?>(null, pageNumber, pageCount, showPageNumbers) {
        value = if (showPageNumbers) withContext(Dispatchers.Default) {
            val text = "$pageNumber / $pageCount"
            val width = PDType1Font.HELVETICA.getStringWidth(text) / 1000f * 10f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = 10f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            // Mantém o avanço da Helvetica mesmo quando a fonte disponível no Android é outra.
            val nativeWidth = paint.measureText(text)
            if (nativeWidth > 0f) paint.textScaleX = width / nativeWidth
            LiveFooter(text, DocumentLivePageGeometry.pageNumber(width), paint)
        } else null
    }

    val current = raster.image
    val matrix = remember(
        current,
        layout.alignment,
        layout.scaleMode,
        layout.contentPosition,
        layout.edgeToEdge,
        layout.rotationQuarterTurns,
        layout.contentScale,
    ) {
        current?.let {
            Matrix().apply {
                setValues(
                    DocumentLivePageGeometry.bitmapTransform(
                        it.bitmap.width, it.bitmap.height, it.sourceWidth, it.sourceHeight, layout,
                    ).matrixValues()
                )
            }
        }
    }
    val clip = remember(layout.scaleMode, layout.edgeToEdge) { DocumentLivePageGeometry.contentClip(layout) }
    val placedSignatures = remember(signatures, signatureAssets.images) {
        signatures.mapNotNull { placement ->
            signatureAssets.images[placement.imageUri]?.let { bitmap ->
                val bounds =
                    DocumentLivePageGeometry.signatureRect(placement, bitmap.width, bitmap.height)
                LiveSignature(bitmap, RectF(bounds.left, bounds.top, bounds.right, bounds.bottom), placement.rotationDegrees)
            }
        }
    }
    val imagePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val paperPaint = remember { Paint().apply { color = android.graphics.Color.WHITE } }
    val description = stringResource(R.string.document_pdf_page_description, pageNumber)
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        Canvas(Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }) {
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                val paperSave = canvas.save()
                try {
                    // Os cálculos permanecem em pontos PDF, independentemente do tamanho do preview.
                    canvas.scale(
                        size.width / PdfPageGeometry.WIDTH,
                        size.height / PdfPageGeometry.HEIGHT
                    )
                    canvas.clipRect(0f, 0f, PdfPageGeometry.WIDTH, PdfPageGeometry.HEIGHT)
                    canvas.drawRect(
                        0f,
                        0f,
                        PdfPageGeometry.WIDTH,
                        PdfPageGeometry.HEIGHT,
                        paperPaint
                    )
                    if (current != null && matrix != null) {
                        val contentSave = canvas.save()
                        try {
                            canvas.clipRect(clip.left, clip.top, clip.right, clip.bottom)
                            canvas.concat(matrix)
                            canvas.drawBitmap(current.bitmap, 0f, 0f, imagePaint)
                        } finally {
                            canvas.restoreToCount(contentSave)
                        }
                    }
                    // Estas camadas não recebem a matriz, nem o recorte das margens da captura.
                    placedSignatures.forEach { signature ->
                        val signatureSave = canvas.save()
                        try {
                            canvas.rotate(signature.rotationDegrees, signature.destination.centerX(), signature.destination.centerY())
                            canvas.drawBitmap(signature.bitmap, null, signature.destination, imagePaint)
                        } finally {
                            canvas.restoreToCount(signatureSave)
                        }
                    }
                    if (showPageNumbers) footer?.let { number ->
                        val background = number.geometry.background
                        canvas.drawRect(
                            background.left,
                            background.top,
                            background.right,
                            background.bottom,
                            paperPaint
                        )
                        canvas.drawText(
                            number.text,
                            number.geometry.left,
                            number.geometry.baseline,
                            number.paint
                        )
                    }
                } finally {
                    canvas.restoreToCount(paperSave)
                }
            }
        }
        if (current == null && !raster.error) CircularProgressIndicator()
        if (raster.error || signatureAssets.error) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    stringResource(R.string.document_pdf_error),
                    Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private data class LiveRaster(val bitmap: Bitmap, val sourceWidth: Float, val sourceHeight: Float)
private data class LiveRasterState(val image: LiveRaster? = null, val error: Boolean = false)
private data class LiveSignatureAssets(
    val images: Map<String, Bitmap> = emptyMap(),
    val error: Boolean = false
)

private data class LiveSignature(val bitmap: Bitmap, val destination: RectF, val rotationDegrees: Float)
private data class LiveFooter(
    val text: String,
    val geometry: DocumentLivePageNumber,
    val paint: Paint
)

/** A mesma resolução de assinatura da prévia PDF, mantendo transparência e proporção. */
private fun decodeLiveSignature(context: Context, uri: String): Bitmap {
    val source = uri.toUri()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(source)
        ?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight).toLong() > 512L * sample) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(source)
        ?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Não foi possível preparar a assinatura.")
}
