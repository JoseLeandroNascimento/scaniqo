package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.runtime.saveable.Saver
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentLayoutControl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** Only completed edits enter the local history. Preview frames are never stored. */
@Serializable
internal data class LayoutDraftHistory(
    val undo: List<PdfPageLayout> = emptyList(),
    val redo: List<PdfPageLayout> = emptyList(),
) {
    fun record(before: PdfPageLayout, after: PdfPageLayout): LayoutDraftHistory =
        if (before == after) this else copy(
            undo = (undo + before).takeLast(MAX_EDITS),
            redo = emptyList(),
        )

    fun undo(current: PdfPageLayout): Pair<PdfPageLayout, LayoutDraftHistory>? =
        undo.lastOrNull()?.let { previous ->
            previous to copy(undo = undo.dropLast(1), redo = (redo + current).takeLast(MAX_EDITS))
        }

    fun redo(current: PdfPageLayout): Pair<PdfPageLayout, LayoutDraftHistory>? =
        redo.lastOrNull()?.let { next ->
            next to copy(undo = (undo + current).takeLast(MAX_EDITS), redo = redo.dropLast(1))
        }

    companion object {
        private const val MAX_EDITS = 30
    }
}

internal val LayoutDraftHistorySaver = Saver<LayoutDraftHistory, String>(
    save = { Json.encodeToString(it) },
    restore = { runCatching { Json.decodeFromString<LayoutDraftHistory>(it) }.getOrNull() },
)

/** Presets intentionally set the size back to 100%, while keeping position and rotation. */
internal enum class LayoutSizePreset(val scaleMode: PageScaleMode, val edgeToEdge: Boolean) {
    WITH_MARGIN(PageScaleMode.FIT, false),
    WITHOUT_MARGIN(PageScaleMode.FIT, true),
    FILL(PageScaleMode.FILL, true);

    fun apply(layout: PdfPageLayout): PdfPageLayout = layout.copy(
        scaleMode = scaleMode,
        edgeToEdge = edgeToEdge,
        contentScale = 1f,
    )

    companion object {
        fun selected(layout: PdfPageLayout): LayoutSizePreset? =
            if (abs(layout.contentScale - 1f) > .0001f) null
            else entries.firstOrNull {
                it.scaleMode == layout.scaleMode && it.edgeToEdge == layout.edgeToEdge
            }
    }
}

/** Restores only the category being edited, using the page's state at session entry. */
internal fun restoreLayoutCategory(
    draft: PdfPageLayout,
    baseline: PdfPageLayout,
    control: DocumentLayoutControl,
): PdfPageLayout = when (control) {
    DocumentLayoutControl.POSITION -> draft.copy(
        alignment = baseline.alignment,
        contentPosition = baseline.contentPosition,
    )
    DocumentLayoutControl.SIZE, DocumentLayoutControl.MARGINS -> draft.copy(
        scaleMode = baseline.scaleMode,
        edgeToEdge = baseline.edgeToEdge,
        contentScale = baseline.contentScale,
    )
    DocumentLayoutControl.ROTATION -> draft.copy(
        rotationQuarterTurns = baseline.rotationQuarterTurns,
    )
}
