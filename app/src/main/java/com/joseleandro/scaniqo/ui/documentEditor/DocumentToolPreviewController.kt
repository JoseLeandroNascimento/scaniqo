package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import kotlinx.coroutines.CoroutineScope

data class DocumentToolPreviewState(
    val preview: PdfPagePreview? = null,
    val live: DocumentLivePreviewState = DocumentLivePreviewState(),
    val isRendering: Boolean = false,
    val error: Boolean = false,
    val showingOriginal: Boolean = false,
)

/** Usa pixels durante a edição e prepara uma folha PDF somente para fontes sem suporte. */
internal class DocumentToolPreviewController(
    scope: CoroutineScope,
    renderPixels: suspend (PdfWorkspace, String, Int) -> PdfInteractivePixels?,
    renderPage: suspend (PdfWorkspace, String) -> PdfPagePreview,
    private val onChanged: (DocumentToolPreviewState) -> Unit,
) {
    private var state = DocumentToolPreviewState()
    private var identity: PageIdentity? = null
    private var requested: Request? = null
    private var liveState = DocumentLivePreviewState()
    private var pageState = DocumentPagePreviewState()
    private var completedPixels: PixelFrame? = null
    private var displayedPixels: PixelFrame? = null
    private var generation = 0L
    private var active = false
    private var suppressPublication = false

    private val pageController = DocumentPagePreviewController(scope, renderPage) { states ->
        pageState = states[identity?.pageId] ?: DocumentPagePreviewState()
        if (!suppressPublication) publish()
    }
    private val liveController = DocumentLivePreviewController(scope, { workspace, pageId, size ->
        val currentGeneration = generation
        val key = rasterContent(workspace, pageId)
        val showingOriginal = requested?.takeIf { it.raster == key }?.showingOriginal ?: false
        renderPixels(workspace, pageId, size)?.also { pixels ->
            if (generation == currentGeneration) {
                completedPixels = PixelFrame(pixels, key, showingOriginal)
            }
        }
    }) { live ->
        liveState = live
        completedPixels?.takeIf { it.pixels === live.pixels }?.let { displayedPixels = it }
        if (!suppressPublication) {
            updateFallback()
            publish()
        }
    }

    fun request(
        workspace: PdfWorkspace,
        pageId: String,
        showingOriginal: Boolean = false,
        debounceMillis: Long = 120,
    ) {
        if (workspace.pages.none { it.id == pageId }) return
        val nextIdentity = PageIdentity(workspace.workspaceId, pageId)
        suppressPublication = true
        if (identity != nextIdentity) {
            reset()
            identity = nextIdentity
        }
        requested = Request(workspace, pageId, showingOriginal, debounceMillis, rasterContent(workspace, pageId))
        active = true
        liveController.request(workspace, pageId)
        suppressPublication = false
        // Alterar só o layout não publica outro estado live, mas invalida uma folha PDF de fallback.
        updateFallback()
        publish()
    }

    /** Interrompe os dois produtores, mantendo o quadro e o rótulo que já estão visíveis. */
    fun pause() {
        suppressPublication = true
        active = false
        requested = null
        generation++
        completedPixels = null
        liveController.pause()
        pageController.pause()
        suppressPublication = false
        publish()
    }

    fun clear() {
        suppressPublication = true
        reset()
        suppressPublication = false
        publish()
    }

    private fun reset() {
        active = false
        requested = null
        identity = null
        generation++
        completedPixels = null
        displayedPixels = null
        liveController.clear()
        pageController.clear()
        liveState = DocumentLivePreviewState()
        pageState = DocumentPagePreviewState()
    }

    private fun updateFallback() {
        val current = requested ?: return
        if (!active) return
        if (liveState.unsupported) {
            pageController.request(
                current.workspace,
                current.pageId,
                showingOriginal = current.showingOriginal,
                debounceMillis = current.debounceMillis,
            )
        } else if (pageState.isRendering) {
            pageController.pause()
        }
    }

    private fun publish() {
        val preview = pageState.preview.takeIf { liveState.pixels == null }
        val current = requested
        val frame = displayedPixels?.takeIf { it.pixels === liveState.pixels }
        val showingOriginal = when {
            preview != null -> pageState.showingOriginal
            frame != null && current != null && frame.raster == current.raster -> current.showingOriginal
            frame != null -> if (frame.pixels === state.live.pixels) state.showingOriginal else frame.showingOriginal
            else -> false
        }
        val next = DocumentToolPreviewState(
            preview = preview,
            live = liveState,
            isRendering = liveState.isRendering || pageState.isRendering,
            error = liveState.error || (liveState.unsupported && pageState.error),
            showingOriginal = showingOriginal,
        )
        if (state != next) {
            state = next
            onChanged(next)
        }
    }

    private data class PageIdentity(val workspaceId: String, val pageId: String)

    private data class RasterContent(val workspaceId: String, val page: PdfPageSpec)

    private data class PixelFrame(
        val pixels: PdfInteractivePixels,
        val raster: RasterContent,
        val showingOriginal: Boolean,
    )

    private data class Request(
        val workspace: PdfWorkspace,
        val pageId: String,
        val showingOriginal: Boolean,
        val debounceMillis: Long,
        val raster: RasterContent,
    )

    /** A comparação segue a mesma identidade visual do produtor de pixels. */
    private fun rasterContent(workspace: PdfWorkspace, pageId: String): RasterContent {
        val page = workspace.pages.first { it.id == pageId }
        return RasterContent(workspace.workspaceId, page.copy(
            layout = page.layout.copy(
                alignment = PageAlignment.CENTER,
                scaleMode = PageScaleMode.FIT,
                rotationQuarterTurns = 0,
                contentScale = 1f,
            ),
            signatures = emptyList(),
        ))
    }
}
