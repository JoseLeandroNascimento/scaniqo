package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfExportRasterCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `reposicionar captura reutiliza raster final mas resolucao e qualidade ficam separadas`() {
        val source = temporary.newFile()
        val page = PdfPageSpec("pagina", source.toURI().toString(), 0,
            PdfPageLayout(imageFilter = PdfImageFilter.ENHANCE))
        val key = PdfRasterKey.from(source, page, 3200, .95f)
        val moved = page.copy(layout = page.layout.copy(
            alignment = PageAlignment.BOTTOM, rotationQuarterTurns = 1, contentScale = .7f
        ))
        assertEquals(key, PdfRasterKey.from(source, moved, 3200, .95f))
        assertNotEquals(key, PdfRasterKey.from(source, page, 1100, .9f))
        assertNotEquals(key, PdfRasterKey.from(source, page, 3200, .8f))
    }

    @Test
    fun `cache final reutiliza pixels e limpa apenas seus proprios arquivos`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val key = PdfRasterKey.from(source, PdfPageSpec("p", source.toURI().toString(), 0), 3200, .95f)
        val preview = PdfPreviewPageCache(directory)
        val export = PdfPreviewPageCache(directory, maxEntries = 32, filePrefix = "export_raster_")
        var previewFile: java.io.File? = null
        preview.use(key.copy(maxSide = 1100), "jpg", {
            it.writeBytes(byteArrayOf(1)); 600f to 800f
        }) { previewFile = it.file }
        var renders = 0
        repeat(2) {
            export.beginPreview(setOf(key))
            export.use(key, "jpg", {
                renders++
                it.writeBytes(byteArrayOf(2)); 600f to 800f
            }) { assertTrue(it.file.name.startsWith("export_raster_")) }
        }
        assertEquals(1, renders)
        export.clear()
        assertTrue(requireNotNull(previewFile).isFile)
        assertTrue(source.isFile)
        assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith("export_raster_") })
    }

    @Test
    fun `cancelar filtro de exportacao nao deixa raster parcial nem invalida fonte`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfPreviewPageCache(directory, filePrefix = "export_raster_")
        val key = PdfRasterKey.from(source, PdfPageSpec("p", source.toURI().toString(), 0), 3200)
        try {
            cache.use(key, "png", {
                it.writeBytes(ByteArray(30))
                throw CancellationException("cancelado")
            }) { error("Não deve consumir arquivo incompleto") }
        } catch (_: CancellationException) {
            // O cancelamento permanece observável pelo salvamento.
        }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        assertTrue(source.isFile)
    }
}
