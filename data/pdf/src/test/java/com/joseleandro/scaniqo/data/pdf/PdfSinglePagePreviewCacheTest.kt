package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfSinglePagePreviewCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun customNumberingInvalidatesPreviewWhenPagesMoveOrTotalChanges() {
        val source = temporary.newFile()
        val original = workspace(source, count = 3)
        val marked = original.copy(pages = original.pages.map { it.copy(overlays =
            listOf(PdfOverlay("number", PdfOverlayKind.PAGE_NUMBER, text = "Página {pagina} de {total}"))) })
        val page = marked.pages[1]
        val key = PdfSinglePagePreviewKey.from(marked, page.id, source)
        assertEquals(2, key.pageNumber)
        assertEquals(3, key.pageCount)
        val reordered = marked.copy(pages = listOf(page) + marked.pages.filterNot { it.id == page.id })
        assertNotEquals(key, PdfSinglePagePreviewKey.from(reordered, page.id, source))
        assertNotEquals(key, PdfSinglePagePreviewKey.from(marked.copy(pages = marked.pages.take(2)), page.id, source))
    }


    @Test
    fun `folha individual mantem indice e contagem do documento completo`() {
        val source = temporary.newFile()
        val workspace = workspace(source, count = 100, numbered = true)
        val key = PdfSinglePagePreviewKey.from(workspace, "pagina-7", source)

        assertEquals(workspace.pages[7], key.page)
        assertEquals(8, key.pageNumber)
        assertEquals(100, key.pageCount)
    }

    @Test
    fun `sem numeracao mover pagina ou alterar outras folhas reutiliza mesma previa`() {
        val source = temporary.newFile()
        val workspace = workspace(source, count = 100)
        val page = workspace.pages[7]
        val reordered = workspace.copy(pages = listOf(page, workspace.pages[0]))
        assertEquals(
            PdfSinglePagePreviewKey.from(workspace, page.id, source),
            PdfSinglePagePreviewKey.from(reordered, page.id, source),
        )
    }

    @Test
    fun `numeracao invalida previa quando posicao ou total mudam`() {
        val source = temporary.newFile()
        val workspace = workspace(source, count = 3, numbered = true)
        val page = workspace.pages[1]
        val key = PdfSinglePagePreviewKey.from(workspace, page.id, source)
        assertNotEquals(key, PdfSinglePagePreviewKey.from(
            workspace.copy(pages = workspace.pages.reversed()), workspace.pages[0].id, source
        ))
        assertNotEquals(key, PdfSinglePagePreviewKey.from(
            workspace.copy(pages = listOf(page) + workspace.pages.filterNot { it.id == page.id }),
            page.id, source
        ))
        assertNotEquals(key, PdfSinglePagePreviewKey.from(
            workspace.copy(pages = workspace.pages.take(2)), page.id, source
        ))
    }

    @Test
    fun `exportacao senha e OCR nao refazem previa de uma folha`() {
        val source = temporary.newFile()
        val workspace = workspace(source)
        val options = workspace.copy(
            exportQuality = PdfExportQuality.SMALL, ocrEnabled = true,
            isProtected = true, protectionKeyId = "chave"
        )
        assertEquals(
            PdfSinglePagePreviewKey.from(workspace, "pagina-0", source),
            PdfSinglePagePreviewKey.from(options, "pagina-0", source),
        )
    }

    @Test
    fun `trocar filtro e fonte invalida somente a chave da folha editada`() {
        val source = temporary.newFile().apply { writeText("original") }
        val workspace = workspace(source, count = 3)
        val page = workspace.pages[1]
        val changed = workspace.copy(pages = workspace.pages.map {
            if (it.id == page.id) it.copy(layout = it.layout.copy(imageFilter = PdfImageFilter.DENOISE)) else it
        })
        assertEquals(
            PdfSinglePagePreviewKey.from(workspace, "pagina-0", source),
            PdfSinglePagePreviewKey.from(changed, "pagina-0", source),
        )
        val oldKey = PdfSinglePagePreviewKey.from(workspace, page.id, source)
        assertNotEquals(oldKey, PdfSinglePagePreviewKey.from(changed, page.id, source))
        source.writeText("uma fonte diferente")
        assertNotEquals(oldKey, PdfSinglePagePreviewKey.from(workspace, page.id, source))
    }

    @Test
    fun `raster interativo e raster antigo nao colidem entre resolucoes`() {
        val source = temporary.newFile()
        val page = workspace(source).pages.first()
        assertNotEquals(PdfRasterKey.from(source, page, 1100), PdfRasterKey.from(source, page, 1800))
    }

    @Test
    fun `pagina pronta nao executa outra composicao`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val key = key(source)
        val cache = PdfSinglePagePreviewCache(directory)
        var count = 0
        val first = cache.getOrCreate(key, emptySet()) { count++; it.writeText("pdf") }
        val second = cache.getOrCreate(key, setOf(first)) { error("Não deve compor novamente") }

        assertEquals(first, second)
        assertEquals(1, count)
    }

    @Test
    fun `viewer e rascunho da mesma pagina permanecem durante descarte de versao antiga`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfSinglePagePreviewCache(directory, maxEntries = 3)
        val key = key(source)
        val viewer = cache.getOrCreate(key, emptySet()) { it.writeText("viewer") }
        val draftKey = key.copy(page = key.page.copy(layout = key.page.layout.copy(filterIntensity = .5f)))
        val draft = cache.getOrCreate(draftKey, setOf(viewer)) { it.writeText("draft") }
        val obsoleteKey = key.copy(page = key.page.copy(id = "obsoleta"))
        val obsolete = cache.getOrCreate(obsoleteKey, setOf(viewer, draft)) { it.writeText("old") }
        val newestKey = key.copy(page = key.page.copy(id = "nova"))
        val newest = cache.getOrCreate(newestKey, setOf(viewer, draft)) { it.writeText("new") }

        assertTrue(viewer.isFile)
        assertTrue(draft.isFile)
        assertTrue(newest.isFile)
        assertFalse(obsolete.exists())
    }

    @Test
    fun `limite de bytes remove somente paginas que nao estao em uso`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfSinglePagePreviewCache(directory, maxBytes = 10)
        val first = cache.getOrCreate(key(source), emptySet()) { it.writeBytes(ByteArray(6)) }
        val second = cache.getOrCreate(key(source).copy(pageNumber = 2), emptySet()) {
            it.writeBytes(ByteArray(6))
        }
        assertFalse(first.exists())
        assertTrue(second.isFile)
        assertEquals(6L, directory.listFiles().orEmpty().sumOf { it.length() })
    }

    @Test
    fun `cache lotado preserva arquivo exibido e remove nova previa recusada`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfSinglePagePreviewCache(directory, maxBytes = 10)
        val key = key(source)
        val first = cache.getOrCreate(key, emptySet()) { it.writeBytes(ByteArray(6)) }
        try {
            cache.getOrCreate(key.copy(pageNumber = 2), setOf(first)) { it.writeBytes(ByteArray(6)) }
            fail("A criação deveria respeitar o limite do cache")
        } catch (_: IOException) {
            // A folha em exibição tem prioridade sobre a tentativa que ultrapassa o limite.
        }
        assertTrue(first.isFile)
        assertEquals(listOf(first), directory.listFiles().orEmpty().toList())
    }

    @Test
    fun `cancelamento apaga PDF parcial e permite repetir pedido`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfSinglePagePreviewCache(directory)
        val key = key(source)
        try {
            cache.getOrCreate(key, emptySet()) {
                it.writeText("incompleto")
                throw CancellationException("novo ajuste")
            }
            fail("O cancelamento deveria propagar")
        } catch (_: CancellationException) {
            // O proprietário decide quando substituir o trabalho cancelado.
        }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        val recreated = cache.getOrCreate(key, emptySet()) { it.writeText("completo") }
        assertEquals("completo", recreated.readText())
    }

    @Test
    fun `limpar sessao remove todas previas individuais sem apagar fonte`() = runTest {
        val directory = temporary.newFolder()
        val source = File(directory, "source.pdf").apply { writeText("original") }
        val cache = PdfSinglePagePreviewCache(directory)
        val preview = cache.getOrCreate(key(source), emptySet()) { it.writeText("pdf") }
        cache.clear()
        assertFalse(preview.exists())
        assertEquals("original", source.readText())
    }

    @Test
    fun `arquivo de previa removido externamente e recomposto`() = runTest {
        val directory = temporary.newFolder()
        val source = temporary.newFile()
        val cache = PdfSinglePagePreviewCache(directory)
        val first = cache.getOrCreate(key(source), emptySet()) { it.writeText("primeiro") }
        first.delete()
        val second = cache.getOrCreate(key(source), emptySet()) { it.writeText("segundo") }
        assertNotEquals(first, second)
        assertEquals("segundo", second.readText())
    }

    @Test
    fun `snapshot salvo devolve indice nativo da folha sem compor documento de cem paginas`() {
        val source = temporary.newFile()
        val saved = temporary.newFile()
        val workspace = workspace(source, count = 100)
        val cache = PdfPreviewSnapshotCache()
        cache.remember(workspace, saved)
        val edited = workspace.copy(pages = workspace.pages.map {
            if (it.id == "pagina-0") it.copy(layout = it.layout.copy(imageFilter = PdfImageFilter.ENHANCE)) else it
        })
        assertEquals(saved to 99, cache.findPage(edited, "pagina-99"))
        assertNull(cache.findPage(edited, "pagina-0"))
        assertEquals(saved to 0, cache.findPage(workspace, "pagina-0"))
    }

    @Test
    fun `snapshot numerado deixa de servir quando total de folhas muda`() {
        val source = temporary.newFile()
        val saved = temporary.newFile()
        val workspace = workspace(source, count = 3, numbered = true)
        val cache = PdfPreviewSnapshotCache()
        cache.remember(workspace, saved)
        assertNull(cache.findPage(workspace.copy(pages = workspace.pages.take(2)), "pagina-0"))
        cache.clear(workspace.workspaceId)
        assertNull(cache.findPage(workspace, "pagina-0"))
    }

    private fun key(source: File) = PdfSinglePagePreviewKey.from(workspace(source), "pagina-0", source)

    private fun workspace(source: File, count: Int = 1, numbered: Boolean = false) = PdfWorkspace(
        documentId = 1,
        workspaceId = "sessao",
        directoryUri = temporary.root.toURI().toString(),
        pages = List(count) { PdfPageSpec("pagina-$it", source.toURI().toString(), it) },
        showPageNumbers = numbered,
    )
}
