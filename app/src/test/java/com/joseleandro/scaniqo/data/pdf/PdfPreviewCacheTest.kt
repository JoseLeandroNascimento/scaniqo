package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfPreviewCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `alinhamento e rotacao reutilizam filtro mas intensidade invalida pixels`() {
        val source = temporary.newFile().apply { writeText("source") }
        val page = page(source)
        val key = PdfRasterKey.from(source, page)
        val positioned = page.copy(layout = page.layout.copy(
            alignment = PageAlignment.BOTTOM, rotationQuarterTurns = 3, contentScale = .7f
        ))

        assertEquals(key, PdfRasterKey.from(source, positioned))
        assertNotEquals(key, PdfRasterKey.from(source, page.copy(
            layout = page.layout.copy(filterIntensity = .3f)
        )))
    }

    @Test
    fun `trocar apenas opcoes de exportacao nao invalida previa visual`() {
        val source = temporary.newFile()
        val workspace = workspace(page(source))
        val changed = workspace.copy(
            ocrEnabled = true, exportQuality = PdfExportQuality.SMALL,
            isProtected = true, protectionKeyId = "chave"
        )
        assertEquals(PdfPreviewKey.from(workspace), PdfPreviewKey.from(changed))
        assertNotEquals(PdfPreviewKey.from(workspace), PdfPreviewKey.from(workspace.copy(showPageNumbers = true)))
    }

    @Test
    fun `mesma pagina filtrada e criada apenas uma vez`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val key = PdfRasterKey.from(source, page(source))
        val cache = PdfPreviewPageCache(directory)
        cache.beginPreview(setOf(key))
        var created = 0
        repeat(3) {
            cache.use(key, "jpg", create = {
                created++
                it.writeBytes(byteArrayOf(1, 2, 3))
                600f to 800f
            }) { assertTrue(it.file.isFile) }
        }

        assertEquals(1, created)
        cache.clear()
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        assertTrue(source.isFile)
    }

    @Test
    fun `limite preserva pagina fixada e remove excedente temporario`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val first = PdfRasterKey.from(source, page(source))
        val second = first.copy(pageIndex = 1)
        val cache = PdfPreviewPageCache(directory, maxBytes = 10)
        cache.beginPreview(setOf(first, second))
        var firstFile: File? = null
        var secondFile: File? = null
        cache.use(first, "jpg", create = {
            it.writeBytes(ByteArray(8))
            600f to 800f
        }) { firstFile = it.file }
        cache.use(second, "jpg", create = {
            it.writeBytes(ByteArray(8))
            600f to 800f
        }) { secondFile = it.file }

        assertTrue(requireNotNull(firstFile).isFile)
        assertFalse(requireNotNull(secondFile).exists())
        assertEquals(8L, directory.listFiles().orEmpty().sumOf { it.length() })
    }

    @Test
    fun `cancelamento remove a imagem incompleta`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val key = PdfRasterKey.from(source, page(source))
        val cache = PdfPreviewPageCache(directory)
        try {
            cache.use(key, "jpg", create = {
                it.writeBytes(ByteArray(8))
                throw CancellationException("cancelado")
            }) { error("Não deve consumir imagem incompleta") }
        } catch (_: CancellationException) {
            // O cancelamento continua chegando ao proprietário da operação.
        }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `snapshot pronto e reutilizado sem regenerar e arquivo removido invalida cache`() {
        val source = temporary.newFile()
        val saved = temporary.newFile()
        val workspace = workspace(page(source))
        val cache = PdfPreviewSnapshotCache()
        cache.remember(workspace, saved)

        assertEquals(saved, cache.find(workspace))
        assertNull(cache.find(workspace.copy(showPageNumbers = true)))
        saved.delete()
        assertNull(cache.find(workspace))
    }

    @Test
    fun `metadados nativos sao reutilizados e invalidam quando fonte muda`() = runTest {
        val source = temporary.newFile().apply { writeText("a") }
        val cache = PdfNativeContentCache()
        var inspections = 0
        repeat(3) {
            cache.get(source, 0) {
                inspections++
                PdfNativeContent(hasText = true, hasVectors = false, largestImageSide = 0)
            }
        }
        assertEquals(1, inspections)
        source.writeText("conteudo novo")
        cache.get(source, 0) {
            inspections++
            PdfNativeContent(false, false, 1000)
        }
        assertEquals(2, inspections)
        cache.clear(source.parentFile!!)
        cache.get(source, 0) {
            inspections++
            PdfNativeContent(false, false, 1000)
        }
        assertEquals(3, inspections)
    }

    private fun page(source: File) = PdfPageSpec(
        "pagina", source.toURI().toString(), 0,
        PdfPageLayout(imageFilter = PdfImageFilter.ENHANCE),
    )

    private fun workspace(page: PdfPageSpec) = PdfWorkspace(
        1, "sessao", temporary.root.toURI().toString(), listOf(page)
    )
}
