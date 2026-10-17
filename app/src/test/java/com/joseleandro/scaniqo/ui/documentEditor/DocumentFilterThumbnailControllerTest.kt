package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentFilterThumbnailControllerTest {
    @Test
    fun `solicitacoes iguais pendentes e prontas reaproveitam o mesmo lote`() = runTest {
        val loader = Loader()
        val states = mutableListOf<DocumentFilterThumbnailState>()
        val controller = DocumentFilterThumbnailController(this, loader::load, states::add)
        val workspace = workspace()
        val gate = CompletableDeferred<Unit>().also { loader.nextGate = it }
        controller.request(workspace, "pagina-1", false)
        runCurrent()
        assertTrue(states.last().isLoading)
        val count = states.size
        controller.request(workspace, "pagina-1", false)
        runCurrent()
        assertEquals(1, loader.calls.size)
        assertEquals(count, states.size)
        gate.complete(Unit)
        advanceUntilIdle()
        val ready = states.last()
        val changed = workspace.copy(
            pages = workspace.pages.map {
                it.copy(layout = it.layout.copy(imageFilter = PdfImageFilter.ENHANCE, filterIntensity = .35f))
            },
            exportQuality = PdfExportQuality.SMALL,
            ocrEnabled = true,
            isProtected = true,
        )
        controller.request(changed, "pagina-1", false)
        advanceUntilIdle()
        assertEquals(1, loader.calls.size)
        assertEquals(ready, states.last())
        assertFalse(ready.isLoading)
        assertEquals(PdfImageFilter.entries.size, ready.result!!.images.size)
    }

    @Test
    fun `voltar a pagina anterior enquanto outra carrega recupera suas amostras`() = runTest {
        val loader = Loader()
        val states = mutableListOf<DocumentFilterThumbnailState>()
        val controller = DocumentFilterThumbnailController(this, loader::load, states::add)
        val workspace = workspace()
        controller.request(workspace, "pagina-1", false)
        advanceUntilIdle()
        val first = states.last().result
        val gate = CompletableDeferred<Unit>()
        loader.nextGate = gate
        loader.ignoreNextCancellation = true
        controller.request(workspace, "pagina-2", false)
        runCurrent()
        assertNull(states.last().result)
        assertEquals("pagina-2", states.last().pageId)
        controller.request(workspace, "pagina-1", false)
        runCurrent()
        assertEquals(3, loader.calls.size)
        val restored = states.last()
        assertEquals("pagina-1", restored.pageId)
        assertFalse(restored.isLoading)
        assertTrue(restored.result != null && restored.result != first)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(restored, states.last())
    }

    @Test
    fun `clear remove URIs e resultado tardio nao reaparece em nova sessao`() = runTest {
        val loader = Loader()
        val states = mutableListOf<DocumentFilterThumbnailState>()
        val controller = DocumentFilterThumbnailController(this, loader::load, states::add)
        val gate = CompletableDeferred<Unit>()
        loader.nextGate = gate
        loader.ignoreNextCancellation = true
        controller.request(workspace(), "pagina-1", false)
        runCurrent()
        controller.clear()
        assertEquals(DocumentFilterThumbnailState(), states.last())
        controller.request(workspace().copy(workspaceId = "nova-sessao"), "pagina-1", true)
        runCurrent()
        val newer = states.last()
        assertFalse(newer.isLoading)
        assertFalse(newer.result!!.requiresRasterization)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(newer, states.last())
        controller.clear()
        assertNull(states.last().pageId)
        assertNull(states.last().result)
        assertFalse(states.last().isLoading)
        assertFalse(states.last().error)
    }

    @Test
    fun `falha forcada limpa estado visivel e retry pode gerar um novo lote`() = runTest {
        val loader = Loader()
        val states = mutableListOf<DocumentFilterThumbnailState>()
        val controller = DocumentFilterThumbnailController(this, loader::load, states::add)
        val workspace = workspace()
        controller.request(workspace, "pagina-1", false)
        advanceUntilIdle()
        val first = states.last().result
        val gate = CompletableDeferred<Unit>()
        loader.nextGate = gate
        loader.failNext = true
        controller.request(workspace, "pagina-1", false, force = true)
        runCurrent()
        assertNull(states.last().result)
        assertTrue(states.last().isLoading)
        assertTrue(loader.calls.last().force)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(states.last().error)
        assertFalse(states.last().isLoading)
        assertNull(states.last().result)
        controller.request(workspace, "pagina-1", false)
        advanceUntilIdle()
        assertEquals(3, loader.calls.size)
        assertFalse(states.last().error)
        assertTrue(states.last().result != null && states.last().result != first)
    }

    @Test
    fun `indice e total invalidam lote apenas com numeracao ativa`() = runTest {
        val loader = Loader()
        val controller = DocumentFilterThumbnailController(this, loader::load) {}
        val workspace = workspace()
        controller.request(workspace, "pagina-1", false)
        advanceUntilIdle()
        val reversed = workspace.copy(pages = workspace.pages.reversed())
        controller.request(reversed, "pagina-1", false)
        advanceUntilIdle()
        assertEquals(1, loader.calls.size)
        controller.request(workspace.copy(showPageNumbers = true), "pagina-1", false)
        advanceUntilIdle()
        assertEquals(2, loader.calls.size)
        controller.request(reversed.copy(showPageNumbers = true), "pagina-1", false)
        advanceUntilIdle()
        assertEquals(3, loader.calls.size)
        controller.request(reversed.copy(
            pages = reversed.pages.drop(1), showPageNumbers = true,
        ), "pagina-1", false)
        advanceUntilIdle()
        assertEquals(4, loader.calls.size)
    }

    @Test
    fun `assinatura e sua posicao alteram as amostras da propria pagina`() = runTest {
        val loader = Loader()
        val controller = DocumentFilterThumbnailController(this, loader::load) {}
        val workspace = workspace()
        controller.request(workspace, "pagina-1", false)
        advanceUntilIdle()
        val signature = PdfSignaturePlacement("assinatura", "file:///assinatura.png")
        val otherPage = workspace.copy(pages = workspace.pages.map {
            if (it.id == "pagina-2") it.copy(signatures = listOf(signature)) else it
        })
        controller.request(otherPage, "pagina-1", false)
        advanceUntilIdle()
        assertEquals(1, loader.calls.size)
        val signed = workspace.copy(pages = workspace.pages.map {
            if (it.id == "pagina-1") it.copy(signatures = listOf(signature)) else it
        })
        controller.request(signed, "pagina-1", false)
        advanceUntilIdle()
        assertEquals(2, loader.calls.size)
        val moved = signed.copy(pages = signed.pages.map {
            if (it.id == "pagina-1") it.copy(signatures = listOf(signature.copy(x = .1f))) else it
        })
        controller.request(moved, "pagina-1", false)
        advanceUntilIdle()
        assertEquals(3, loader.calls.size)
    }

    @Test
    fun `geometria e permissao de rasterizar nao exibem lote com aviso anterior`() = runTest {
        val loader = Loader()
        val states = mutableListOf<DocumentFilterThumbnailState>()
        val controller = DocumentFilterThumbnailController(this, loader::load, states::add)
        val workspace = workspace()
        controller.request(workspace, "pagina-1", false)
        advanceUntilIdle()
        assertTrue(states.last().result!!.requiresRasterization)
        val gate = CompletableDeferred<Unit>().also { loader.nextGate = it }
        controller.request(workspace, "pagina-1", true)
        runCurrent()
        assertNull(states.last().result)
        assertTrue(states.last().isLoading)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(states.last().result!!.requiresRasterization)
        val adjusted = workspace.copy(pages = workspace.pages.map {
            if (it.id == "pagina-1") it.copy(layout = it.layout.copy(straightenDegrees = 2f)) else it
        })
        controller.request(adjusted, "pagina-1", true)
        advanceUntilIdle()
        assertEquals(3, loader.calls.size)
        assertEquals(2f, loader.calls.last().workspace.pages.first().layout.straightenDegrees, 0f)
    }

    @Test
    fun `pagina desconhecida nao cancela carga e pause preserva somente resultado pronto`() = runTest {
        val loader = Loader()
        val states = mutableListOf<DocumentFilterThumbnailState>()
        val controller = DocumentFilterThumbnailController(this, loader::load, states::add)
        val workspace = workspace()
        val gate = CompletableDeferred<Unit>().also { loader.nextGate = it }
        controller.request(workspace, "pagina-1", false)
        runCurrent()
        val loading = states.last()
        controller.request(workspace, "removida", false)
        assertEquals(loading, states.last())
        gate.complete(Unit)
        advanceUntilIdle()
        val ready = states.last().result
        controller.pause()
        assertEquals(ready, states.last().result)
        controller.request(workspace, "pagina-1", false)
        advanceUntilIdle()
        assertEquals(1, loader.calls.size)
        val secondGate = CompletableDeferred<Unit>()
        loader.nextGate = secondGate
        loader.ignoreNextCancellation = true
        controller.request(workspace, "pagina-2", false)
        runCurrent()
        controller.pause()
        assertNull(states.last().result)
        assertFalse(states.last().isLoading)
        secondGate.complete(Unit)
        advanceUntilIdle()
        assertNull(states.last().result)
        controller.request(workspace, "pagina-2", false)
        advanceUntilIdle()
        assertEquals(3, loader.calls.size)
        assertEquals("pagina-2", states.last().pageId)
        assertTrue(states.last().result != null)
    }

    private fun workspace() = PdfWorkspace(
        documentId = 1L,
        workspaceId = "sessao",
        directoryUri = "file:///sessao",
        pages = (1..3).map { PdfPageSpec("pagina-$it", "file:///fonte.pdf", it - 1) },
    )

    private data class Call(
        val workspace: PdfWorkspace,
        val pageId: String,
        val rasterizeText: Boolean,
        val force: Boolean,
    )

    private class Loader {
        val calls = mutableListOf<Call>()
        var nextGate: CompletableDeferred<Unit>? = null
        var ignoreNextCancellation = false
        var failNext = false

        suspend fun load(
            workspace: PdfWorkspace,
            pageId: String,
            rasterizeText: Boolean,
            force: Boolean,
        ): PdfFilterThumbnails {
            val gate = nextGate.also { nextGate = null }
            val ignoreCancellation = ignoreNextCancellation.also { ignoreNextCancellation = false }
            val fail = failNext.also { failNext = false }
            calls += Call(workspace, pageId, rasterizeText, force)
            val result = PdfFilterThumbnails(
                PdfImageFilter.entries.associateWith { "file:///$pageId-${calls.size}-${it.name}.png" },
                requiresRasterization = !rasterizeText,
            )
            if (ignoreCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
            if (fail) throw IOException("Falha ao gerar amostras.")
            return result
        }
    }
}
