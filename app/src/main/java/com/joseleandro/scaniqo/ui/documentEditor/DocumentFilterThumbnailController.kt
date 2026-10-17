package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfFilterThumbnails
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class DocumentFilterThumbnailState(
    val pageId: String? = null,
    val result: PdfFilterThumbnails? = null,
    val isLoading: Boolean = false,
    val error: Boolean = false,
)

/** As amostras são fixas em 100%: mover a intensidade não gera novamente todas as opções. */
internal class DocumentFilterThumbnailController(
    private val scope: CoroutineScope,
    private val load: suspend (PdfWorkspace, String, Boolean, Boolean) -> PdfFilterThumbnails,
    private val onChanged: (DocumentFilterThumbnailState) -> Unit,
) {
    private data class Content(val workspaceId: String, val page: PdfPageSpec, val numbering: Pair<Int, Int>?)
    private var state = DocumentFilterThumbnailState()
    private var displayed: Content? = null
    private var pending: Content? = null
    private var job: Job? = null
    private var revision = 0L

    fun request(workspace: PdfWorkspace, pageId: String, rasterizeText: Boolean, force: Boolean = false) {
        val page = workspace.pages.firstOrNull { it.id == pageId } ?: return
        val content = Content(
            workspaceId = workspace.workspaceId,
            page = page.copy(layout = page.layout.copy(
                imageFilter = PdfImageFilter.ORIGINAL, filterIntensity = 1f, rasterizeText = rasterizeText,
            )),
            numbering = (workspace.pages.indexOf(page) to workspace.pages.size).takeIf { workspace.showPageNumbers },
        )
        if (!force && (pending == content ||
            (displayed == content && state.result != null && state.pageId == pageId))) return
        pause()
        val requestRevision = revision
        pending = content
        // Uma amostra de outra página ou de uma permissão anterior nunca fica sob o novo rótulo.
        state = DocumentFilterThumbnailState(pageId = pageId, isLoading = true)
        onChanged(state)
        job = scope.launch {
            try {
                val result = load(workspace, pageId, rasterizeText, force)
                if (requestRevision != revision) return@launch
                displayed = content
                state = DocumentFilterThumbnailState(pageId, result)
                onChanged(state)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestRevision == revision) {
                    state = DocumentFilterThumbnailState(pageId = pageId, error = true)
                    onChanged(state)
                }
            } finally {
                if (requestRevision == revision) pending = null
            }
        }
    }

    fun pause() {
        revision++
        job?.cancel()
        job = null
        pending = null
        state = state.copy(isLoading = false)
        onChanged(state)
    }

    fun clear() {
        pause()
        displayed = null
        state = DocumentFilterThumbnailState()
        onChanged(state)
    }
}
