package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfReusablePreviewTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `editar uma pagina reutiliza as outras noventa e nove folhas prontas`() {
        val original = workspace(100)
        val changed = original.copy(pages = original.pages.mapIndexed { index, page ->
            if (index == 0) page.copy(layout = page.layout.copy(alignment = PageAlignment.TOP)) else page
        })
        val indices = snapshot(original).matchingPageIndices(changed)

        assertNull(indices.first())
        assertEquals((1 until 100).toList(), indices.drop(1))
    }

    @Test
    fun `comparar pagina atual ignora filtro apenas na pagina alterada`() {
        val original = workspace(3)
        val comparison = original.copy(pages = original.pages.mapIndexed { index, page ->
            if (index == 1) page.copy(layout = PdfPageLayout()) else page
        })

        assertEquals(listOf(0, null, 2), snapshot(original).matchingPageIndices(comparison))
    }

    @Test
    fun `reordenar paginas sem numeracao reutiliza indice anterior`() {
        val original = workspace(3)
        assertEquals(listOf(2, 0, 1), snapshot(original).matchingPageIndices(
            original.copy(pages = listOf(original.pages[2], original.pages[0], original.pages[1]))
        ))
    }

    @Test
    fun `numeracao invalida folhas deslocadas ou com novo total`() {
        val original = workspace(3).copy(showPageNumbers = true)
        assertEquals(listOf(null, 1, null), snapshot(original).matchingPageIndices(
            original.copy(pages = original.pages.reversed())
        ))
        assertEquals(listOf(null, null), snapshot(original).matchingPageIndices(
            original.copy(pages = original.pages.take(2))
        ))
        assertEquals(listOf(null, null, null), snapshot(original).matchingPageIndices(
            original.copy(showPageNumbers = false)
        ))
    }

    @Test
    fun `alterar assinatura impede reaproveitar folha antiga`() {
        val original = workspace(2)
        val changed = original.copy(pages = original.pages.mapIndexed { index, page ->
            if (index == 0) page.copy(signatures = listOf(PdfSignaturePlacement("assinatura", "file:/assinatura.png")))
            else page
        })
        assertEquals(listOf(null, 1), snapshot(original).matchingPageIndices(changed))
    }

    @Test
    fun `escolhe snapshot com mais paginas inalteradas e limpa referencias ao bloquear`() {
        val original = workspace(3)
        val unrelated = original.copy(pages = original.pages.map { it.copy(layout = PdfPageLayout()) })
        val originalFile = temporary.newFile()
        val cache = PdfPreviewSnapshotCache()
        cache.remember(original, originalFile)
        cache.remember(unrelated, temporary.newFile())
        val changed = original.copy(pages = original.pages.mapIndexed { index, page ->
            if (index == 0) page.copy(layout = page.layout.copy(contentScale = .8f)) else page
        })

        assertEquals(originalFile, cache.reusable(changed)?.file)
        cache.clear(original.workspaceId)
        assertNull(cache.reusable(changed))
    }

    @Test
    fun `arquivo de snapshot removido nunca e escolhido para reuso`() {
        val original = workspace(2)
        val file = temporary.newFile()
        val cache = PdfPreviewSnapshotCache()
        cache.remember(original, file)
        file.delete()

        assertNull(cache.reusable(original))
    }

    @Test
    fun `numero dinamico invalida somente paginas deslocadas`() {
        val original = withTemplate("{pagina}")
        val reordered = original.copy(pages = original.pages.reversed())

        assertEquals(listOf(null, 1, null), snapshot(original).matchingPageIndices(reordered))
        assertEquals(listOf(0, 1), snapshot(original).matchingPageIndices(
            original.copy(pages = original.pages.take(2))
        ))
    }

    @Test
    fun `total dinamico invalida paginas quando quantidade muda mas permite reordenar`() {
        val original = withTemplate("{total}")

        assertEquals(listOf(2, 1, 0), snapshot(original).matchingPageIndices(
            original.copy(pages = original.pages.reversed())
        ))
        assertEquals(listOf(null, null), snapshot(original).matchingPageIndices(
            original.copy(pages = original.pages.take(2))
        ))
    }

    @Test
    fun `cache da folha nao devolve snapshot com numero ou total desatualizado`() {
        val original = withTemplate("{pagina}/{total}")
        val file = temporary.newFile()
        val cache = PdfPreviewSnapshotCache()
        cache.remember(original, file)

        assertEquals(file to 0, cache.findPage(original, original.pages[0].id))
        assertNull(cache.findPage(
            original.copy(pages = original.pages.reversed()), original.pages[0].id
        ))
        assertNull(cache.findPage(
            original.copy(pages = original.pages.take(2)), original.pages[0].id
        ))
    }

    private fun withTemplate(text: String): PdfWorkspace = workspace(3).let { workspace ->
        workspace.copy(pages = workspace.pages.map { page ->
            page.copy(overlays = listOf(PdfOverlay("number", PdfOverlayKind.PAGE_NUMBER, text = text)))
        })
    }

    private fun snapshot(workspace: PdfWorkspace) = PdfReusablePreview(
        File(temporary.root, "preview.pdf"), PdfPreviewKey.from(workspace)
    )

    private fun workspace(count: Int) = PdfWorkspace(
        documentId = 1,
        workspaceId = "sessao",
        directoryUri = temporary.root.toURI().toString(),
        pages = List(count) { index ->
            PdfPageSpec("pagina-$index", "file:/source.pdf", index,
                PdfPageLayout(imageFilter = PdfImageFilter.ENHANCE))
        },
    )
}
