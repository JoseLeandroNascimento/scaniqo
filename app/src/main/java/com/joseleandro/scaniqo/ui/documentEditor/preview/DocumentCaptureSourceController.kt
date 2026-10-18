package com.joseleandro.scaniqo.ui.documentEditor.preview

import com.joseleandro.scaniqo.domain.model.PdfCaptureSource
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentCaptureSourceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Retém só uma captura; resultados antigos nunca substituem outra página ou sessão. */
internal class DocumentCaptureSourceController(
    private val scope: CoroutineScope,
    private val load: suspend (PdfWorkspace, String) -> PdfCaptureSource?,
    private val onChanged: (DocumentCaptureSourceState) -> Unit,
) {
    private var state = DocumentCaptureSourceState()
    private var key: SourceKey? = null
    private var job: Job? = null
    private var revision = 0L

    fun request(workspace: PdfWorkspace, pageId: String, force: Boolean = false) {
        val page = workspace.pages.firstOrNull { it.id == pageId } ?: return
        val next = SourceKey(workspace.workspaceId, page.id, page.sourceUri, page.sourcePageIndex)
        if (!force && next == key && (state.isLoading || state.source != null || state.unsupported)) return
        pause()
        key = next
        val request = ++revision
        state = DocumentCaptureSourceState(pageId = pageId, isLoading = true)
        onChanged(state)
        job = scope.launch {
            try {
                val source = load(workspace, pageId)
                if (request != revision) return@launch
                state = DocumentCaptureSourceState(pageId, source, unsupported = source == null)
                onChanged(state)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == revision) {
                    state = DocumentCaptureSourceState(pageId = pageId, error = true)
                    onChanged(state)
                }
            } finally {
                if (request == revision) job = null
            }
        }
    }

    fun pause() {
        revision++
        job?.cancel()
        job = null
        if (state.isLoading) {
            state = state.copy(isLoading = false)
            onChanged(state)
        }
    }

    fun clear() {
        pause()
        key = null
        state = DocumentCaptureSourceState()
        onChanged(state)
    }

    private data class SourceKey(
        val workspaceId: String,
        val pageId: String,
        val sourceUri: String,
        val sourcePageIndex: Int,
    )
}
