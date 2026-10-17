package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.roundToInt

/** Renderização limitada a uma página por vez; também atende análise e OCR locais. */
internal class PdfFilteredPageRenderer(private val sourceFiles: Map<String, File>) : Closeable {
    private val renderers = mutableMapOf<String, PdfRenderer>()

    suspend fun render(
        sourceUri: String,
        index: Int,
        layout: PdfPageLayout = PdfPageLayout(),
        maxSide: Int = 2400,
    ): FilteredPage {
        require(maxSide in 128..4000)
        currentCoroutineContext().ensureActive()
        val renderer = renderers[sourceUri] ?: open(requireNotNull(sourceFiles[sourceUri]))
            .also { renderers[sourceUri] = it }
        return renderer.openPage(index).use { page ->
            // O limite é menor na análise e acompanha o perfil escolhido na exportação.
            val scale = maxSide.toFloat() / max(page.width, page.height)
            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                bitmap.setHasAlpha(false)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                currentCoroutineContext().ensureActive()
                if (layout.needsImageProcessing()) {
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    val corrected = PdfImageCorrections.apply(pixels, width, height, layout)
                    PdfImageFilters.apply(corrected, layout.imageFilter, width, height, layout.filterIntensity)
                    bitmap.setPixels(corrected, 0, width, 0, 0, width, height)
                }
                FilteredPage(bitmap, page.width.toFloat(), page.height.toFloat())
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        }
    }

    private fun open(file: File): PdfRenderer {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(descriptor)
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
    }

    override fun close() {
        var failure: Exception? = null
        renderers.values.forEach { renderer ->
            try {
                renderer.close()
            } catch (error: Exception) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        renderers.clear()
        failure?.let { throw it }
    }

    class FilteredPage(val bitmap: Bitmap, val width: Float, val height: Float) : Closeable {
        override fun close() = bitmap.recycle()
    }
}
