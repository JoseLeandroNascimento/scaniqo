package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Quatro fontes pequenas evitam decodificar o PDF novamente a cada movimento de um controle. */
internal class PdfInteractivePageRenderer {
    private data class Key(val source: PdfSourceStamp, val pageIndex: Int, val crop: PdfCropBounds?)
    private val originals = LinkedHashMap<Key, PdfFilteredPageRenderer.FilteredPage>(4, .75f, true)

    suspend fun render(
        file: File,
        sourceUri: String,
        pageIndex: Int,
        layout: PdfPageLayout,
        maxSide: Int = MAX_SIDE,
        captureBounds: PdfCropBounds? = null,
    ): PdfFilteredPageRenderer.FilteredPage {
        require(maxSide in 128..MAX_SIDE) { "O tamanho da prévia deve ficar entre 128 e 1100 pixels." }
        currentCoroutineContext().ensureActive()
        layout.validateImageProcessing()
        val key = Key(PdfSourceStamp.from(file), pageIndex, layout.crop?.takeUnless { it.isIdentity() })
        val original = originals[key] ?: PdfFilteredPageRenderer(mapOf(sourceUri to file)).use {
            it.render(sourceUri, pageIndex, layout = PdfPageLayout(crop = key.crop), maxSide = MAX_SIDE)
        }.also { decoded ->
            originals[key] = decoded
            while (originals.size > MAX_ORIGINALS) {
                val oldest = originals.entries.first()
                originals.remove(oldest.key)
                oldest.value.close()
            }
        }
        currentCoroutineContext().ensureActive()
        val size = PdfInteractiveRasterSize.fit(original.bitmap.width, original.bitmap.height, maxSide)
        // Reduz antes do processamento. A fonte de 1100 px nunca é copiada inteira para um gesto.
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setHasAlpha(false)
            Canvas(bitmap).drawBitmap(
                original.bitmap, null, Rect(0, 0, size.width, size.height), Paint(Paint.FILTER_BITMAP_FLAG),
            )
            if (layout.needsImageProcessing()) {
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val isolated = PdfImageBackgroundRemoval.apply(
                    pixels, width, height, layout.backgroundMask, layout.crop, captureBounds = captureBounds,
                )
                val corrected = PdfImageCorrections.apply(isolated, width, height, layout)
                PdfImageFilters.apply(corrected, layout.imageFilter, width, height, layout.filterIntensity)
                bitmap.setPixels(corrected, 0, width, 0, 0, width, height)
            }
            currentCoroutineContext().ensureActive()
            return PdfFilteredPageRenderer.FilteredPage(bitmap, original.width, original.height)
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    fun clear(directory: File) {
        val parent = directory.canonicalFile
        val iterator = originals.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (File(entry.key.source.path).parentFile == parent) {
                entry.value.close()
                iterator.remove()
            }
        }
    }

    companion object {
        const val MAX_SIDE = PdfInteractiveRasterSize.MAX_SIDE
        private const val MAX_ORIGINALS = 4
    }
}
