package com.joseleandro.scaniqo.ui.documentEditor.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.isValidBackgroundMask
import java.util.UUID

internal data class DocumentBackgroundRequest(val id: String, val pageId: String)
internal enum class DocumentBackgroundFeedback { SUGGESTED, NO_EDGES, FAILED }

/** Correlaciona a máscara de fundo ao pedido da página, sem modificar o documento. */
@Stable
internal class DocumentBackgroundSuggestionState(
    attemptedPages: Set<String> = emptySet(),
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {
    private var attemptedPageIds by mutableStateOf(attemptedPages)
    var pending: DocumentBackgroundRequest? by mutableStateOf(null)
        private set
    var feedback: DocumentBackgroundFeedback? by mutableStateOf(null)
        private set

    /** Só a primeira entrada pode iniciar automaticamente; ajustes e cancelamentos ficam intactos. */
    fun beginAutomatically(pageId: String, hasMask: Boolean): DocumentBackgroundRequest? {
        if (pending != null || pageId in attemptedPageIds) return null
        attemptedPageIds = attemptedPageIds + pageId
        return if (hasMask) null else begin(pageId)
    }

    fun begin(pageId: String): DocumentBackgroundRequest? {
        if (pending != null) return null
        attemptedPageIds = attemptedPageIds + pageId
        feedback = null
        return DocumentBackgroundRequest(newRequestId(), pageId).also { pending = it }
    }

    fun cancel() {
        pending = null
        feedback = null
    }

    fun clearFeedback() {
        feedback = null
    }

    fun consume(state: DocumentToolsUiState, pageId: String): PdfPerspective? {
        val request = pending ?: return null
        if (state.isLocked || request.pageId != pageId) {
            cancel()
            return null
        }
        if (state.isAnalyzing || state.correctionAnalysisRequestId != request.id) return null
        pending = null
        if (state.analysisError) {
            feedback = DocumentBackgroundFeedback.FAILED
            return null
        }
        val bounds = state.qualityReport?.pages?.firstOrNull { it.pageId == pageId }
            ?.suggestedCrop?.takeIf { it.isValidBackgroundMask() }
        feedback = if (bounds == null) DocumentBackgroundFeedback.NO_EDGES else DocumentBackgroundFeedback.SUGGESTED
        return bounds
    }

    companion object {
        // Nunca restaura um pedido em execução: a composição anterior o cancela ao sair.
        val Saver = listSaver<DocumentBackgroundSuggestionState, String>(
            save = { it.attemptedPageIds.toList() },
            restore = { DocumentBackgroundSuggestionState(it.toSet()) },
        )
    }
}
