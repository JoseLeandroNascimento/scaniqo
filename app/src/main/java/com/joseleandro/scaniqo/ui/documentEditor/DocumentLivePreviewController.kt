package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DocumentLivePreviewState(
    val pageId: String? = null,
    val pixels: PdfInteractivePixels? = null,
    val isRendering: Boolean = false,
    val error: Boolean = false,
    val unsupported: Boolean = false,
)

/** Mantém uma amostra em andamento durante o gesto e processa depois apenas o rascunho mais recente. */
internal class DocumentLivePreviewController(
    private val scope: CoroutineScope,
    private val render: suspend (PdfWorkspace, String, Int) -> PdfInteractivePixels?,
    private val onChanged: (DocumentLivePreviewState) -> Unit,
) {
    private var state = DocumentLivePreviewState()
    private var identity: PageIdentity? = null
    private var requested: Request? = null
    private var completedFastKey: RasterKey? = null
    private var settledSequence: Long? = null
    private var generation = 0L
    private var sequence = 0L
    private var refinementRevision = 0L
    private var worker: Job? = null
    private var settling: Job? = null
    private var refinement: Job? = null

    fun request(workspace: PdfWorkspace, pageId: String, force: Boolean = false) {
        val page = workspace.pages.firstOrNull { it.id == pageId } ?: return
        val nextIdentity = PageIdentity(workspace.workspaceId, pageId)
        val layout = page.layout
        val key = RasterKey(
            nextIdentity, page.sourceUri, page.sourcePageIndex,
            layout.imageFilter, layout.filterIntensity, layout.rasterizeText,
            layout.straightenDegrees, layout.perspective, layout.curvature,
        )
        if (!force && requested?.key == key && !state.error &&
            (worker != null || settling != null || refinement != null ||
                completedFastKey == key || state.unsupported)
        ) return

        if (identity != nextIdentity) {
            cancelWork()
            identity = nextIdentity
            state = DocumentLivePreviewState(pageId = pageId)
        }
        val next = Request(workspace, pageId, key, ++sequence, force)
        requested = next
        completedFastKey = null
        settledSequence = null
        refinementRevision++
        refinement?.cancel()
        refinement = null
        settling?.cancel()
        state = state.copy(pageId = pageId, isRendering = true, error = false, unsupported = false)
        publish()

        // O prazo começa no último movimento, sem somar mais 250 ms ao tempo da amostra.
        val currentGeneration = generation
        settling = scope.launch(start = CoroutineStart.LAZY) {
            delay(REFINEMENT_DELAY)
            if (generation == currentGeneration && requested?.sequence == next.sequence) {
                settledSequence = next.sequence
                settling = null
                startRefinementIfReady()
            }
        }
        settling?.start()
        if (worker == null) startWorker()
    }

    /** Cancela processamento sem apagar o último quadro; uma nova solicitação retoma a prévia. */
    fun pause() {
        cancelWork()
        state = state.copy(isRendering = false)
        publish()
    }

    /** Libera referências da página, inclusive quando o documento é bloqueado ou substituído. */
    fun clear() {
        cancelWork()
        identity = null
        state = DocumentLivePreviewState()
        publish()
    }

    private fun startWorker() {
        val currentGeneration = generation
        worker = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (generation == currentGeneration) {
                    val current = requested ?: break
                    val pixels = try {
                        render(current.workspace, current.pageId, FAST_SIZE)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (generation != currentGeneration) break
                        if (!isLatest(current)) continue
                        state = state.copy(isRendering = false, error = true, unsupported = false)
                        publish()
                        break
                    }
                    if (generation != currentGeneration) break
                    val latest = isLatest(current)
                    if (pixels != null) {
                        // Um quadro intermediário é útil durante o arraste, desde que seja da mesma página.
                        state = DocumentLivePreviewState(
                            pageId = current.pageId,
                            pixels = pixels,
                            isRendering = !latest,
                        )
                        publish()
                    } else if (latest) {
                        state = DocumentLivePreviewState(pageId = current.pageId, unsupported = true)
                        publish()
                    }
                    if (isLatest(current)) {
                        completedFastKey = current.key.takeIf { pixels != null }
                        break
                    }
                }
            } finally {
                if (generation == currentGeneration) {
                    worker = null
                    if (state.isRendering) {
                        state = state.copy(isRendering = false)
                        publish()
                    }
                    startRefinementIfReady()
                }
            }
        }
        worker?.start()
    }

    private fun isLatest(current: Request): Boolean {
        val latest = requested ?: return false
        return latest.key == current.key && (!latest.force || latest.sequence == current.sequence)
    }

    private fun startRefinementIfReady() {
        val current = requested ?: return
        if (worker != null || refinement != null || state.error || state.unsupported ||
            settledSequence != current.sequence || completedFastKey != current.key
        ) return
        val currentGeneration = generation
        val revision = ++refinementRevision
        refinement = scope.launch(start = CoroutineStart.LAZY) {
            try {
                state = state.copy(isRendering = true)
                publish()
                val pixels = render(current.workspace, current.pageId, REFINED_SIZE)
                if (canPublishRefinement(currentGeneration, revision, current)) {
                    state = if (pixels != null) {
                        DocumentLivePreviewState(pageId = current.pageId, pixels = pixels)
                    } else {
                        DocumentLivePreviewState(pageId = current.pageId, unsupported = true)
                    }
                    publish()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (canPublishRefinement(currentGeneration, revision, current)) {
                    state = state.copy(isRendering = false, error = true)
                    publish()
                }
            } finally {
                if (generation == currentGeneration && refinementRevision == revision) {
                    refinement = null
                    if (state.isRendering) {
                        state = state.copy(isRendering = false)
                        publish()
                    }
                }
            }
        }
        refinement?.start()
    }

    private fun canPublishRefinement(currentGeneration: Long, revision: Long, current: Request): Boolean =
        generation == currentGeneration && refinementRevision == revision && requested?.sequence == current.sequence

    private fun cancelWork() {
        generation++
        refinementRevision++
        worker?.cancel()
        settling?.cancel()
        refinement?.cancel()
        worker = null
        settling = null
        refinement = null
        requested = null
        completedFastKey = null
        settledSequence = null
    }

    private fun publish() = onChanged(state)

    private data class PageIdentity(val workspaceId: String, val pageId: String)

    /** Posição na folha e anotações pertencem ao Compose, sem invalidar o processamento da imagem. */
    private data class RasterKey(
        val page: PageIdentity,
        val sourceUri: String,
        val sourcePageIndex: Int,
        val imageFilter: PdfImageFilter,
        val filterIntensity: Float,
        val rasterizeText: Boolean,
        val straightenDegrees: Float,
        val perspective: PdfPerspective?,
        val curvature: Float,
    )

    private data class Request(
        val workspace: PdfWorkspace,
        val pageId: String,
        val key: RasterKey,
        val sequence: Long,
        val force: Boolean,
    )

    private companion object {
        const val FAST_SIZE = 480
        const val REFINED_SIZE = 1100
        const val REFINEMENT_DELAY = 250L
    }
}
