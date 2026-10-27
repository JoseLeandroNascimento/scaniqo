package com.joseleandro.scaniqo.ui.documentEditor.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Guarda somente receitas de layout; um arraste inteiro vira uma entrada no histórico local. */
@Stable
internal class CorrectionDraftSession(initial: PdfPageLayout) {
    val baseline: PdfPageLayout = initial
    var draft by mutableStateOf(initial)
        private set
    var revision by mutableStateOf(0)
        private set

    private val undoStack = mutableStateListOf<PdfPageLayout>()
    private val redoStack = mutableStateListOf<PdfPageLayout>()
    private var gestureStart: PdfPageLayout? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty() || gestureStart?.let { it != draft } == true
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isDirty: Boolean get() = draft != baseline

    fun edit(next: PdfPageLayout) {
        finishGesture()
        if (next == draft) return
        rememberUndo(draft)
        redoStack.clear()
        draft = next
        revision++
    }

    fun beginGesture() {
        if (gestureStart == null) gestureStart = draft
    }

    fun changeGesture(next: PdfPageLayout) {
        beginGesture()
        if (next == draft) return
        draft = next
        revision++
    }

    fun finishGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        if (start != draft) {
            rememberUndo(start)
            redoStack.clear()
        }
    }

    fun cancelGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        if (draft != start) {
            draft = start
            revision++
        }
    }

    fun undo(): Boolean {
        finishGesture()
        if (undoStack.isEmpty()) return false
        redoStack.add(draft)
        draft = undoStack.removeAt(undoStack.lastIndex)
        revision++
        return true
    }

    fun redo(): Boolean {
        finishGesture()
        if (redoStack.isEmpty()) return false
        rememberUndo(draft)
        draft = redoStack.removeAt(redoStack.lastIndex)
        revision++
        return true
    }

    private fun rememberUndo(value: PdfPageLayout) {
        undoStack.add(value)
        if (undoStack.size > MAX_HISTORY) undoStack.removeAt(0)
    }

    private fun save(): SavedCorrectionDraft {
        val history = undoStack.toMutableList()
        gestureStart?.takeIf { it != draft }?.let { history.add(it) }
        return SavedCorrectionDraft(baseline, draft, history.takeLast(MAX_HISTORY), redoStack.toList(), revision)
    }

    companion object {
        private const val MAX_HISTORY = 24
        val Saver = Saver<CorrectionDraftSession, String>(
            save = { Json.encodeToString(it.save()) },
            restore = { encoded ->
                runCatching {
                    Json.decodeFromString<SavedCorrectionDraft>(encoded).let { saved ->
                        CorrectionDraftSession(saved.baseline).apply {
                            draft = saved.draft
                            undoStack.addAll(saved.undo)
                            redoStack.addAll(saved.redo)
                            revision = saved.revision
                        }
                    }
                }.getOrNull()
            },
        )
    }
}

@Serializable
private data class SavedCorrectionDraft(
    val baseline: PdfPageLayout,
    val draft: PdfPageLayout,
    val undo: List<PdfPageLayout>,
    val redo: List<PdfPageLayout>,
    val revision: Int,
)
