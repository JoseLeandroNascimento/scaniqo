package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Amostras pequenas da página real; nenhum PDF intermediário é criado para os filtros. */
internal class PdfFilterThumbnailRenderer(
    private val files: PdfWorkspaceFiles,
    private val composer: PdfBoxComposer,
) {
    private val caches = mutableMapOf<String, PdfFilterThumbnailCache>()

    suspend fun render(
        workspace: PdfWorkspace,
        pageId: String,
        rasterizeText: Boolean,
        force: Boolean,
    ): PdfFilterThumbnails {
        currentCoroutineContext().ensureActive()
        val index = workspace.pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "A página não está mais disponível." }
        val page = workspace.pages[index]
        page.layout.validateImageProcessing()
        val directory = files.directory(workspace)
        val source = files.source(workspace, page.sourceUri)
        val signatureFiles = page.signatures.map { files.source(workspace, it.imageUri) }
        val key = PdfFilterThumbnailKey.from(workspace, pageId, source, rasterizeText, signatureFiles)
        val cache = caches.getOrPut(workspace.workspaceId) { PdfFilterThumbnailCache(directory) }
        val batch = cache.getOrCreate(key, force) { targets ->
            // O aviso descreve a fonte, mesmo quando as amostras já mostram a conversão em imagem.
            val requiresRasterization = composer.inspectContent(source, page.sourcePageIndex).shouldPreserve
            val preserveNative = !rasterizeText && requiresRasterization
            val layout = page.layout.copy(
                imageFilter = PdfImageFilter.ORIGINAL,
                filterIntensity = 1f,
                rasterizeText = rasterizeText,
                straightenDegrees = if (preserveNative) 0f else page.layout.straightenDegrees,
                perspective = if (preserveNative) null else page.layout.perspective,
                curvature = if (preserveNative) 0f else page.layout.curvature,
            )
            PdfFilteredPageRenderer(mapOf(page.sourceUri to source)).use { renderer ->
                // A fonte e as correções são calculadas uma vez, antes de percorrer as opções.
                renderer.render(page.sourceUri, page.sourcePageIndex, layout, MAX_SIDE).use { rendered ->
                    val bitmap = rendered.bitmap
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val overlay = createOverlay(workspace, page, index, signatureFiles)
                    val sheet = Bitmap.createBitmap(THUMB_WIDTH, MAX_SIDE, Bitmap.Config.ARGB_8888)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    val matrix = Matrix().apply {
                        setValues(PdfFilterThumbnailPlacement.matrix(
                            rendered.width, rendered.height, bitmap.width, bitmap.height, layout,
                        ))
                    }
                    try {
                        val outputs = linkedMapOf<PdfImageFilter, File>()
                        PdfFilterSamplePixels.forEach(
                            pixels, bitmap.width, bitmap.height, allowFilters = !preserveNative,
                        ) { filter, sample ->
                            bitmap.setPixels(sample, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            sheet.eraseColor(Color.WHITE)
                            sheet.setHasAlpha(false)
                            val canvas = Canvas(sheet)
                            canvas.save()
                            canvas.scale(THUMB_WIDTH / PdfPageGeometry.WIDTH, MAX_SIDE / PdfPageGeometry.HEIGHT)
                            val margin = PdfPageGeometry.marginFor(layout)
                            canvas.clipRect(
                                margin, margin, PdfPageGeometry.WIDTH - margin, PdfPageGeometry.HEIGHT - margin,
                            )
                            canvas.drawBitmap(bitmap, matrix, paint)
                            canvas.restore()
                            canvas.drawBitmap(overlay, 0f, 0f, paint)
                            currentCoroutineContext().ensureActive()
                            val target = targets.getValue(filter)
                            val context = currentCoroutineContext()
                            target.outputStream().use { output ->
                                check(sheet.compress(
                                    Bitmap.CompressFormat.PNG, 100,
                                    PdfCancellableOutputStream(output, context, MAX_THUMBNAIL_BYTES),
                                )) { "Não foi possível gravar a amostra do filtro." }
                            }
                            currentCoroutineContext().ensureActive()
                            outputs[filter] = target
                        }
                        if (preserveNative) {
                            // Sem consentimento, todas as opções refletem a aparência preservada do PDF.
                            val original = outputs.getValue(PdfImageFilter.ORIGINAL)
                            PdfImageFilter.entries.forEach { outputs[it] = original }
                        }
                        PdfFilterThumbnailBatch(outputs, requiresRasterization = requiresRasterization)
                    } finally {
                        overlay.recycle()
                        sheet.recycle()
                    }
                }
            }
        }
        return PdfFilterThumbnails(batch.images.mapValues { files.uri(it.value) }, batch.requiresRasterization)
    }

    /** O bloqueio do documento remove também as amostras legíveis mantidas na sessão privada. */
    fun clear(workspace: PdfWorkspace) {
        val cache = caches.remove(workspace.workspaceId)
        if (cache != null) cache.clear()
        else if (files.file(workspace.directoryUri).isDirectory) {
            PdfFilterThumbnailCache(files.directory(workspace)).clear()
        }
    }

    private suspend fun createOverlay(
        workspace: PdfWorkspace,
        page: PdfPageSpec,
        index: Int,
        signatureFiles: List<File>,
    ): Bitmap {
        require(page.signatures.size <= 20) { "Use até 20 assinaturas por página." }
        val overlay = Bitmap.createBitmap(THUMB_WIDTH, MAX_SIDE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(overlay)
            canvas.scale(THUMB_WIDTH / PdfPageGeometry.WIDTH, MAX_SIDE / PdfPageGeometry.HEIGHT)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            page.signatures.forEachIndexed { position, signature ->
                currentCoroutineContext().ensureActive()
                require(listOf(signature.x, signature.y, signature.width, signature.height)
                    .all { it.isFinite() && it in 0f..1f } &&
                    signature.width > 0 && signature.height > 0 &&
                    signature.x + signature.width <= 1.001f && signature.y + signature.height <= 1.001f
                ) { "A posição da assinatura é inválida." }
                val bitmap = decodeSignature(signatureFiles[position])
                try {
                    val width = signature.width * PdfPageGeometry.WIDTH
                    val height = signature.height * PdfPageGeometry.HEIGHT
                    val scale = min(width / bitmap.width, height / bitmap.height)
                    val drawnWidth = bitmap.width * scale
                    val drawnHeight = bitmap.height * scale
                    val left = signature.x * PdfPageGeometry.WIDTH + (width - drawnWidth) / 2
                    val top = signature.y * PdfPageGeometry.HEIGHT + (height - drawnHeight) / 2
                    canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawnWidth, top + drawnHeight), paint)
                } finally {
                    bitmap.recycle()
                }
            }
            if (workspace.showPageNumbers) {
                val label = "${index + 1} / ${workspace.pages.size}"
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                paint.textSize = 10f
                val width = paint.measureText(label)
                val left = (PdfPageGeometry.WIDTH - width) / 2
                paint.color = Color.WHITE
                canvas.drawRect(left - 4, PdfPageGeometry.HEIGHT - 28, left + width + 4,
                    PdfPageGeometry.HEIGHT - 12, paint)
                paint.color = Color.BLACK
                canvas.drawText(label, left, PdfPageGeometry.HEIGHT - 15, paint)
            }
            return overlay
        } catch (failure: Throwable) {
            overlay.recycle()
            throw failure
        }
    }

    private fun decodeSignature(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "A assinatura não está disponível." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight).toLong() > MAX_SIDE.toLong() * sample) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Não foi possível ler a assinatura.")
    }

    companion object {
        private const val MAX_SIDE = 220
        private val THUMB_WIDTH = (MAX_SIDE * PdfPageGeometry.WIDTH / PdfPageGeometry.HEIGHT).roundToInt()
        private const val MAX_THUMBNAIL_BYTES = 512L * 1024
    }
}
