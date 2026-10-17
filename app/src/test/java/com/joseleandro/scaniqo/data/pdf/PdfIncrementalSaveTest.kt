package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfIncrementalSaveTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `alterar uma folha em cem reutiliza as outras noventa e nove na exportacao`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 100)
        val snapshot = snapshot(workspace, source)
        val changed = workspace.copy(pages = workspace.pages.mapIndexed { index, page ->
            if (index == 35) page.copy(layout = page.layout.copy(alignment = PageAlignment.BOTTOM)) else page
        })
        val indices = snapshot.matchingPageIndices(changed, sources(source))
        assertEquals(99, indices.count { it != null })
        assertNull(indices[35])
        assertEquals(99, indices[99])
    }

    @Test
    fun `mudar qualidade ou OCR exige reconstruir folhas a partir das fontes`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 3)
        val snapshot = snapshot(workspace, source)
        assertEquals(listOf(null, null, null), snapshot.matchingPageIndices(
            workspace.copy(exportQuality = PdfExportQuality.SMALL), sources(source)
        ))
        assertEquals(listOf(null, null, null), snapshot.matchingContentPageIndices(
            workspace.copy(ocrEnabled = true), sources(source)
        ))
    }

    @Test
    fun `trocar protecao nao altera pixels texto ou vetores reaproveitaveis`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 2)
        val snapshot = snapshot(workspace, source)
        assertEquals(listOf(0, 1), snapshot.matchingPageIndices(
            workspace.copy(isProtected = true, protectionKeyId = "nova-chave"), sources(source)
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `previa interativa nunca pode originar exportacao final`() {
        val source = temporary.newFile()
        val preview = temporary.newFile("page_preview_reduzida.pdf")
        PdfFinalExportSnapshot.capture(workspace(source, 1), preview, sources(source))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `previa antiga sem comprovacao de qualidade final tambem e recusada`() {
        val source = temporary.newFile()
        val preview = temporary.newFile("preview_antiga.pdf")
        PdfFinalExportSnapshot.capture(workspace(source, 1), preview, sources(source))
    }

    @Test
    fun `alterar fonte impede reaproveitar dados de snapshot anterior`() {
        val source = temporary.newFile().apply { writeText("fonte") }
        val workspace = workspace(source, 2)
        val snapshot = snapshot(workspace, source)
        source.writeText("fonte modificada")
        assertEquals(listOf(null, null), snapshot.matchingPageIndices(workspace, sources(source)))
    }

    @Test
    fun `nova assinatura invalida so a folha que recebeu a assinatura`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 2)
        val snapshot = snapshot(workspace, source)
        val changed = workspace.copy(pages = workspace.pages.mapIndexed { index, page ->
            if (index == 0) page.copy(signatures = listOf(
                PdfSignaturePlacement("assinatura", "file:///assinatura.png")
            )) else page
        })
        assertEquals(listOf(null, 1), snapshot.matchingPageIndices(changed, sources(source)))
    }

    @Test
    fun `renumeracao reaproveita conteudo mas nao o rodape anterior`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 3).copy(showPageNumbers = true)
        val snapshot = snapshot(workspace, source)
        val changed = workspace.copy(pages = workspace.pages.reversed())
        assertEquals(listOf(null, 1, null), snapshot.matchingPageIndices(changed, sources(source)))
        assertEquals(listOf(2, 1, 0), snapshot.matchingContentPageIndices(changed, sources(source)))
        assertEquals(listOf(0, 1, 2), snapshot.matchingContentPageIndices(
            workspace.copy(showPageNumbers = false), sources(source)
        ))
    }

    @Test
    fun `arquivo final removido invalida todo reaproveitamento`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 1)
        val snapshot = snapshot(workspace, source)
        snapshot.file.delete()
        assertEquals(listOf(null), snapshot.matchingContentPageIndices(workspace, sources(source)))
    }

    @Test
    fun `arquivo de fonte unica pode ser copiado sem normalizar nem mudar indices`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 3)
        val archive = PdfSourceArchive.single(workspace, sources(source), 3)
        assertNotNull(archive)
        assertEquals(listOf(0, 1, 2), archive?.indices(workspace, sources(source)))
        assertEquals(listOf(2, 1, 0), archive?.indices(
            workspace.copy(pages = workspace.pages.reversed()), sources(source)
        ))
    }

    @Test
    fun `excluir pagina impede copiar arquivo que ainda contem material excluido`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 3)
        val removed = workspace.copy(pages = workspace.pages.take(2))
        val archive = requireNotNull(PdfSourceArchive.single(workspace, sources(source), 3))
        assertNull(archive.indices(removed, sources(source)))
        assertNull(PdfSourceArchive.single(removed, sources(source), 3))
    }

    @Test
    fun `fontes consolidadas depois de exclusao usam indices compactados`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 4).let { it.copy(pages = listOf(it.pages[3], it.pages[1])) }
        val archiveFile = temporary.newFile()
        val archive = PdfSourceArchive.composed(workspace, archiveFile, sources(source))
        assertEquals(listOf(0, 1), archive.indices(workspace, sources(source)))
        assertEquals(listOf(1, 0), archive.indices(
            workspace.copy(pages = workspace.pages.reversed()), sources(source)
        ))
    }

    @Test
    fun `ajustes filtros e assinaturas nao reconsolidam fontes originais`() {
        val source = temporary.newFile()
        val workspace = workspace(source, 3)
        val archive = requireNotNull(PdfSourceArchive.single(workspace, sources(source), 3))
        val changed = workspace.copy(pages = workspace.pages.map {
            it.copy(
                layout = it.layout.copy(imageFilter = PdfImageFilter.AUTO, alignment = PageAlignment.TOP),
                signatures = listOf(PdfSignaturePlacement("s", "file:///s.png"))
            )
        }, showPageNumbers = true)
        assertEquals(listOf(0, 1, 2), archive.indices(changed, sources(source)))
    }

    @Test
    fun `anexar fonte ou restaurar folha excluida exige nova consolidacao`() {
        val source = temporary.newFile()
        val second = temporary.newFile()
        val original = workspace(source, 3)
        val selected = original.copy(pages = original.pages.take(2))
        val archive = PdfSourceArchive.composed(selected, temporary.newFile(), sources(source))
        assertNull(archive.indices(original, sources(source)))
        val appended = selected.copy(pages = selected.pages + PdfPageSpec("extra", second.toURI().toString(), 0))
        assertNull(archive.indices(appended, sources(source) + sources(second)))
    }

    @Test
    fun `fonte substituida invalida arquivo consolidado`() {
        val source = temporary.newFile().apply { writeText("original") }
        val workspace = workspace(source, 1)
        val archive = requireNotNull(PdfSourceArchive.single(workspace, sources(source), 1))
        source.writeText("mudou a fonte")
        assertNull(archive.indices(workspace, sources(source)))
    }

    private fun snapshot(workspace: PdfWorkspace, source: File) = PdfFinalExportSnapshot.capture(
        workspace, temporary.newFile("final_preview_${System.nanoTime()}.pdf"), sources(source)
    )

    private fun sources(source: File) = mapOf(source.toURI().toString() to source)

    private fun workspace(source: File, count: Int) = PdfWorkspace(
        documentId = 1,
        workspaceId = "sessao",
        directoryUri = temporary.root.toURI().toString(),
        pages = List(count) { PdfPageSpec("pagina-$it", source.toURI().toString(), it) }
    )
}
