package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.joseleandro.scaniqo.domain.model.PdfRedactionArea
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Reconstrói TODO o documento com pixels novos. Não importa objetos do PDF de entrada:
 * texto invisível, imagens originais, anexos, metadados e revisões nunca entram na saída.
 */
internal object PdfRedactor {
    internal fun redactPixels(bitmap: Bitmap, areas: List<PdfRedactionArea>) {
        require(areas.all(PdfRedactionArea::isValid))
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        areas.forEach { area ->
            // Arredonda para fora; nenhum pixel da região marcada fica nas bordas.
            canvas.drawRect(
                floor(area.x * bitmap.width), floor(area.y * bitmap.height),
                ceil((area.x + area.width) * bitmap.width), ceil((area.y + area.height) * bitmap.height),
                paint,
            )
        }
    }

    suspend fun write(source: File, target: File, workspace: PdfWorkspace, areas: List<PdfRedactionArea>, password: String?) {
        require(areas.isNotEmpty() && areas.size <= 1000 && areas.all(PdfRedactionArea::isValid))
        require(areas.all { area -> workspace.pages.any { it.id == area.pageId } })
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount == workspace.pages.size)
                PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(target.parentFile)).use { result ->
                    workspace.pages.forEachIndexed { index, spec ->
                        currentCoroutineContext().ensureActive()
                        renderer.openPage(index).use { rendered ->
                            val width = 1800
                            val height = (width.toFloat() * rendered.height / rendered.width).roundToInt()
                            require(height in 1..7200)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                rendered.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                redactPixels(bitmap, areas.filter { it.pageId == spec.id })
                                val page = PDPage(PDRectangle(rendered.width.toFloat(), rendered.height.toFloat()))
                                result.addPage(page)
                                PDPageContentStream(result, page).use { stream ->
                                    stream.drawImage(LosslessFactory.createFromImage(result, bitmap), 0f, 0f,
                                        rendered.width.toFloat(), rendered.height.toFloat())
                                }
                            } finally { bitmap.recycle() }
                        }
                    }
                    if (password != null) PdfProtection.apply(result, password)
                    val context = currentCoroutineContext()
                    target.outputStream().use { stream ->
                        result.save(PdfCancellableOutputStream(stream, context, PdfWorkspaceFiles.MAX_BYTES) {
                            java.io.IOException("A cópia censurada excedeu 50 MB.")
                        })
                    }
                }
            }
        }
    }
}
