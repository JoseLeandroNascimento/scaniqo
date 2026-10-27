package com.joseleandro.scaniqo.ui.documentEditor.preview

import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentPreviewContent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/** Agenda somente as folhas solicitadas e descarta resultados de gestos já superados. */
internal class DocumentPagePreviewController(
    private val scope: CoroutineScope,
    private val render: suspend (PdfWorkspace, String) -> PdfPagePreview,
    private val onChanged: (Map<String, DocumentPagePreviewState>) -> Unit,
) {
    private val states = mutableMapOf<String, DocumentPagePreviewState>()
    private val displayed = mutableMapOf<String, DocumentPreviewContent>()
    private val pending = mutableMapOf<String, DocumentPreviewContent>()
    private val revisions = mutableMapOf<String, Long>()
    private val jobs = mutableMapOf<String, Job>()
    private val requestedOriginal = mutableMapOf<String, Boolean>()

    fun request(
        workspace: PdfWorkspace,
        pageId: String,
        showingOriginal: Boolean = false,
        force: Boolean = false,
        debounceMillis: Long = 0,
    ) {
        val index = workspace.pages.indexOfFirst { it.id == pageId }
        if (index < 0) return
        val content = DocumentPreviewContent(
            workspace.workspaceId, workspace.pages[index],
            (index to workspace.pages.size).takeIf { workspace.showPageNumbers || workspace.pages[index].overlays.any {
                it.text.contains("{pagina}") || it.text.contains("{total}")
            } },
        )
        requestedOriginal[pageId] = showingOriginal
        if (!force && pending[pageId] == content) return
        if (!force && displayed[pageId] == content && states[pageId]?.preview != null) {
            cancel(pageId)
            states[pageId] = states.getValue(pageId).copy(error = false, showingOriginal = showingOriginal)
            publish()
            return
        }
        cancel(pageId)
        val revision = revisions.getOrDefault(pageId, 0)
        val previous = states[pageId] ?: DocumentPagePreviewState()
        states[pageId] = previous.copy(isRendering = true, error = false)
        pending[pageId] = content
        publish()
        jobs[pageId] = scope.launch {
            try {
                if (debounceMillis > 0) delay(debounceMillis)
                val preview = render(workspace, pageId)
                if (revisions[pageId] != revision) return@launch
                displayed[pageId] = content
                states[pageId] = DocumentPagePreviewState(
                    preview = preview,
                    showingOriginal = requestedOriginal[pageId] ?: showingOriginal,
                    renderedOverlayIds = content.page.overlays.mapTo(mutableSetOf()) { it.id },
                )
                publish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revisions[pageId] == revision) {
                    states[pageId] = previous.copy(error = true)
                    publish()
                }
            } finally {
                if (revisions[pageId] == revision) pending.remove(pageId)
            }
        }
    }

    fun cancelExcept(pageIds: Set<String>) {
        pending.keys.toList().filterNot { it in pageIds }.forEach(::cancel)
        publish()
    }

    fun retainPages(pageIds: Set<String>) {
        (states.keys + pending.keys).filterNot { it in pageIds }.forEach { id ->
            cancel(id)
            states.remove(id)
            displayed.remove(id)
            requestedOriginal.remove(id)
        }
        publish()
    }

    fun pause() {
        pending.keys.toList().forEach(::cancel)
        publish()
    }

    /** Depois de salvar, solicita a nova saída, inclusive quando só o perfil de qualidade mudou. */
    fun invalidate() {
        pause()
        displayed.clear()
    }

    fun clear() {
        pause()
        states.clear()
        displayed.clear()
        requestedOriginal.clear()
        jobs.clear()
        publish()
    }

    private fun cancel(pageId: String) {
        revisions[pageId] = revisions.getOrDefault(pageId, 0) + 1
        jobs.remove(pageId)?.cancel()
        pending.remove(pageId)
        states[pageId]?.let { states[pageId] = it.copy(isRendering = false) }
    }

    private fun publish() = onChanged(states.toMap())
}
