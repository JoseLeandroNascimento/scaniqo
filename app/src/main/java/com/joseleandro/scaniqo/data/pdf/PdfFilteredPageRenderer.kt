package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Renderização limitada a uma página por vez; também atende análise e OCR locais. */
internal class PdfFilteredPageRenderer(private val sourceFiles: Map<String, File>) : Closeable {
    private val renderers = mutableMapOf<String, PdfRenderer>()

    suspend fun render(
        sourceUri: String,
        index: Int,
        layout: PdfPageLayout = PdfPageLayout(),
        maxSide: Int = 2400,
        captureBounds: PdfCropBounds? = null,
    ): FilteredPage {
        require(maxSide in 128..4000)
        currentCoroutineContext().ensureActive()
        val renderer = renderers[sourceUri] ?: open(requireNotNull(sourceFiles[sourceUri]))
            .also { renderers[sourceUri] = it }
        return renderer.openPage(index).use { page ->
            layout.validateImageProcessing()
            // Renderiza diretamente a região final: cortar não reduz a definição nem estica pixels.
            val viewport = PdfCropGeometry.viewport(page.width.toFloat(), page.height.toFloat(), layout.crop, maxSide)
            val width = viewport.pixelWidth
            val height = viewport.pixelHeight
            val matrix = Matrix().apply {
                setValues(floatArrayOf(
                    viewport.scale, 0f, -viewport.left * viewport.scale,
                    0f, viewport.scale, -viewport.top * viewport.scale,
                    0f, 0f, 1f,
                ))
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                bitmap.setHasAlpha(false)
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                currentCoroutineContext().ensureActive()
                if (layout.needsImageProcessing()) {
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    val isolated = PdfImageBackgroundRemoval.apply(
                        pixels, width, height, layout.backgroundMask, layout.crop, captureBounds = captureBounds,
                    )
                    val corrected = PdfImageCorrections.apply(isolated, width, height, layout)
                    PdfImageFilters.apply(corrected, layout.imageFilter, width, height, layout.filterIntensity)
                    bitmap.setPixels(corrected, 0, width, 0, 0, width, height)
                }
                FilteredPage(bitmap, viewport.width, viewport.height)
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
