package com.joseleandro.scaniqo.ui.pdfTools

import androidx.compose.runtime.saveable.Saver
import com.joseleandro.scaniqo.domain.model.PdfPageSelection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal enum class UtilityPageSelectionKind { ALL, NONE, EXPLICIT }

@Serializable
internal enum class UtilityPageSelectionMode { THUMBNAILS, INTERVALS }

@Serializable
internal data class UtilityPageSelection(
    val kind: UtilityPageSelectionKind = UtilityPageSelectionKind.ALL,
    val indices: List<Int> = emptyList(),
    val customOrder: Boolean = false,
) {
    fun resolve(pageCount: Int): List<Int> {
        require(pageCount in 1..100)
        return when (kind) {
            UtilityPageSelectionKind.ALL -> (0 until pageCount).toList()
            UtilityPageSelectionKind.NONE -> emptyList()
            UtilityPageSelectionKind.EXPLICIT -> indices.filter { it in 0 until pageCount }.distinct()
        }
    }

    fun parserValue(pageCount: Int): String {
        val pages = resolve(pageCount)
        require(pages.isNotEmpty()) { "Selecione pelo menos uma página." }
        return if (kind == UtilityPageSelectionKind.ALL) "" else utilityPageRangeText(pages)
    }

    fun toggle(index: Int, pageCount: Int): UtilityPageSelection {
        require(index in 0 until pageCount)
        val current = resolve(pageCount)
        val updated = if (index in current) current - index else current + index
        return fromIndices(if (customOrder) updated else updated.sorted(), pageCount, customOrder)
    }

    fun inDocumentOrder(pageCount: Int): UtilityPageSelection =
        fromIndices(resolve(pageCount).sorted(), pageCount)

    companion object {
        fun none() = UtilityPageSelection(UtilityPageSelectionKind.NONE)

        fun fromIndices(indices: List<Int>, pageCount: Int, customOrder: Boolean = false): UtilityPageSelection {
            require(pageCount in 1..100 && indices.all { it in 0 until pageCount })
            val unique = indices.distinct()
            val custom = customOrder || unique != unique.sorted()
            return when {
                unique.isEmpty() -> none()
                !custom && unique == (0 until pageCount).toList() -> UtilityPageSelection()
                else -> UtilityPageSelection(UtilityPageSelectionKind.EXPLICIT, unique, custom)
            }
        }

        fun fromText(value: String, pageCount: Int): UtilityPageSelection {
            require(value.isNotBlank()) { "Selecione pelo menos uma página." }
            return fromIndices(PdfPageSelection.parse(value, pageCount), pageCount)
        }
    }
}

internal fun utilityPageRanges(indices: List<Int>): List<List<Int>> {
    val ranges = mutableListOf<MutableList<Int>>()
    indices.forEach { index ->
        if (ranges.lastOrNull()?.lastOrNull()?.plus(1) == index) ranges.last().add(index)
        else ranges.add(mutableListOf(index))
    }
    return ranges
}

internal fun utilityPageRangeText(indices: List<Int>): String =
    utilityPageRanges(indices).joinToString(", ") { range ->
        if (range.size == 1) (range.first() + 1).toString()
        else "${range.first() + 1}-${range.last() + 1}"
    }

@Serializable
internal data class PdfUtilityPageSelectionState(
    val selection: UtilityPageSelection = UtilityPageSelection(),
    val mode: UtilityPageSelectionMode = UtilityPageSelectionMode.THUMBNAILS,
    val draft: String = "",
    val draftActive: Boolean = false,
    val advanced: Boolean = false,
    val from: String = "",
    val until: String = "",
    val editingRange: Int? = null,
    val attemptedRange: Boolean = false,
) {
    fun select(value: UtilityPageSelection, pageCount: Int) = copy(
        selection = value, draft = utilityPageRangeText(value.resolve(pageCount)),
        draftActive = false, from = "", until = "", editingRange = null, attemptedRange = false,
    )

    fun show(value: UtilityPageSelectionMode, pageCount: Int) = copy(
        mode = value,
        draft = if (draftActive) draft else utilityPageRangeText(selection.resolve(pageCount)),
    )

    fun showAdvanced(value: Boolean) = copy(
        advanced = value, from = "", until = "", editingRange = null, attemptedRange = false,
    )

    fun edit(value: String) = copy(draft = value.take(1001), draftActive = true)

    fun cancel(pageCount: Int) = select(selection, pageCount)

    fun confirm(pageCount: Int): PdfUtilityPageSelectionState {
        val parsed = UtilityPageSelection.fromText(draft, pageCount)
        return select(UtilityPageSelection.fromIndices(
            parsed.resolve(pageCount), pageCount,
            parsed.customOrder || (selection.customOrder && !(advanced && draftActive)),
        ), pageCount)
    }

    fun addRange(pageCount: Int): PdfUtilityPageSelectionState {
        val added = PdfPageSelection.parse("$from-$until", pageCount)
        val existing = if (draft.isBlank()) emptyList() else PdfPageSelection.parse(draft, pageCount)
        val ranges = utilityPageRanges(existing).toMutableList()
        val editing = editingRange
        if (editing != null && editing in ranges.indices) ranges[editing] = added
        else ranges.add(added)
        val updated = ranges.flatten().distinct()
        val ordered = if (selection.customOrder || existing != existing.sorted()) updated else updated.sorted()
        return copy(
            draft = utilityPageRangeText(ordered), draftActive = true,
            from = "", until = "", editingRange = null, attemptedRange = false,
        )
    }

    fun removeRange(index: Int, pageCount: Int): PdfUtilityPageSelectionState {
        val current = PdfPageSelection.parse(draft, pageCount)
        val ranges = utilityPageRanges(current).filterIndexed { position, _ -> position != index }
        return copy(draft = utilityPageRangeText(ranges.flatten()), draftActive = true, editingRange = null)
    }
}

internal val PdfUtilityPageSelectionSaver = Saver<PdfUtilityPageSelectionState, String>(
    save = { Json.encodeToString(it) },
    restore = { runCatching { Json.decodeFromString<PdfUtilityPageSelectionState>(it) }.getOrNull() },
)
