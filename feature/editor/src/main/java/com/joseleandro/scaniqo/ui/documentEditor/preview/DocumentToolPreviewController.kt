package com.joseleandro.scaniqo.ui.documentEditor.preview

import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentLivePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
import kotlinx.coroutines.CoroutineScope

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
    private var active = false
    private var suppressPublication = false

    private val pageController = DocumentPagePreviewController(scope, renderPage) { states ->
        pageState = states[identity?.pageId] ?: DocumentPagePreviewState()
        if (!suppressPublication) publish()
    }
    private val liveController = DocumentLivePreviewController(scope, renderPixels) { live ->
        liveState = live
        if (!suppressPublication) {
            updateFallback()
            publish()
        }
    }

    fun request(
        workspace: PdfWorkspace,
        pageId: String,
        debounceMillis: Long = 120,
    ) {
        if (workspace.pages.none { it.id == pageId }) return
        val nextIdentity = PageIdentity(workspace.workspaceId, pageId)
        suppressPublication = true
        if (identity != nextIdentity) {
            reset()
            identity = nextIdentity
        }
        requested = Request(workspace, pageId, debounceMillis)
        active = true
        liveController.request(workspace, pageId)
        suppressPublication = false
        // Alterar só o layout não publica outro estado live, mas invalida uma folha PDF de fallback.
        updateFallback()
        publish()
    }

    /** Interrompe os dois produtores, mantendo o quadro que já está visível. */
    fun pause() {
        suppressPublication = true
        active = false
        requested = null
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
                debounceMillis = current.debounceMillis,
            )
        } else if (pageState.isRendering) {
            pageController.pause()
        }
    }

    private fun publish() {
        val next = DocumentToolPreviewState(
            preview = pageState.preview.takeIf { liveState.pixels == null },
            live = liveState,
            isRendering = liveState.isRendering || pageState.isRendering,
            error = liveState.error || (liveState.unsupported && pageState.error),
        )
        if (state != next) {
            state = next
            onChanged(next)
        }
    }

    private data class PageIdentity(val workspaceId: String, val pageId: String)

    private data class Request(
        val workspace: PdfWorkspace,
        val pageId: String,
        val debounceMillis: Long,
    )
}
