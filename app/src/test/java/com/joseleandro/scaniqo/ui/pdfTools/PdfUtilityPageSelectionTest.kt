package com.joseleandro.scaniqo.ui.pdfTools

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PdfUtilityPageSelectionTest {
    @Test fun allAndNoneRemainDifferentAcrossSerialization() {
        for (count in listOf(1, 2, 51, 100)) {
            val all = PdfUtilityPageSelectionState()
            val none = all.select(UtilityPageSelection.none(), count)
            assertEquals((0 until count).toList(), restore(all).selection.resolve(count))
            assertTrue(restore(none).selection.resolve(count).isEmpty())
            assertEquals("", all.selection.parserValue(count))
            assertTrue(runCatching { none.selection.parserValue(count) }.isFailure)
        }
    }

    @Test fun visualSelectionUsesPdfOrderRegardlessOfTapOrder() {
        val selection = UtilityPageSelection.none().toggle(8, 10).toggle(3, 10).toggle(1, 10)
        assertEquals(listOf(1, 3, 8), selection.resolve(10))
        assertFalse(selection.customOrder)
        assertEquals("2, 4, 9", selection.parserValue(10))
    }

    @Test fun explicitOrderSurvivesCompactionTogglesAndRestoration() {
        val original = UtilityPageSelection.fromText("5, 2-4, 1", 6)
        assertTrue(original.customOrder)
        assertEquals(listOf(4, 1, 2, 3, 0), original.resolve(6))
        assertEquals("5, 2-4, 1", original.parserValue(6))
        val toggled = original.toggle(2, 6).toggle(5, 6)
        assertEquals(listOf(4, 1, 3, 0, 5), toggled.resolve(6))
        assertEquals(toggled, restore(PdfUtilityPageSelectionState(selection = toggled)).selection)
        assertEquals(listOf(0, 1, 3, 4, 5), toggled.inDocumentOrder(6).resolve(6))
        assertFalse(toggled.inDocumentOrder(6).customOrder)
    }

    @Test fun duplicatePagesAreRemovedWithoutReordering() {
        assertEquals(listOf(4, 1, 2, 3, 0),
            UtilityPageSelection.fromText("5, 2-4, 5, 1, 2", 6).resolve(6))
    }

    @Test fun rangesOnlyCompactConsecutiveForwardPages() {
        assertEquals("5, 2-4, 1", utilityPageRangeText(listOf(4, 1, 2, 3, 0)))
        assertEquals("4, 3, 2, 1", utilityPageRangeText(listOf(3, 2, 1, 0)))
    }

    @Test fun localizedDigitsAndPunctuationUseExistingParser() {
        assertEquals(listOf(4, 1, 2, 3, 0),
            UtilityPageSelection.fromText("\u200f５， ٢–٤، ১\u200e", 6).resolve(6))
    }

    @Test fun invalidDraftDoesNotReplaceConfirmedSelectionOrMeanAll() {
        val original = PdfUtilityPageSelectionState(selection = UtilityPageSelection.fromText("2-5", 10))
            .show(UtilityPageSelectionMode.INTERVALS, 10)
        for (text in listOf("", "3-", "9-2", "101", "1,")) {
            val edited = original.edit(text)
            assertEquals(listOf(1, 2, 3, 4), edited.selection.resolve(10))
            assertTrue(runCatching { edited.confirm(10) }.isFailure)
            assertEquals(original.selection, edited.cancel(10).selection)
            assertFalse(edited.cancel(10).draftActive)
        }
    }

    @Test fun switchingModeAndRestoringKeepUnconfirmedDraftSeparate() {
        val original = PdfUtilityPageSelectionState().select(UtilityPageSelection.none(), 51)
        val edited = original.show(UtilityPageSelectionMode.INTERVALS, 51).edit("2-5, 9")
        val switched = restore(edited.show(UtilityPageSelectionMode.THUMBNAILS, 51))
            .show(UtilityPageSelectionMode.INTERVALS, 51)
        assertEquals("2-5, 9", switched.draft)
        assertTrue(switched.draftActive)
        assertTrue(switched.selection.resolve(51).isEmpty())
        assertEquals(listOf(1, 2, 3, 4, 8), switched.confirm(51).selection.resolve(51))
    }

    @Test fun addingEditingAndRemovingRangesPreserveSelectionUntilConfirmed() {
        val empty = PdfUtilityPageSelectionState().select(UtilityPageSelection.none(), 10)
            .show(UtilityPageSelectionMode.INTERVALS, 10)
        val draft = empty.copy(from = "2", until = "5").addRange(10)
            .copy(from = "9", until = "9").addRange(10)
        assertEquals("2-5, 9", draft.draft)
        assertTrue(draft.selection.resolve(10).isEmpty())
        val edited = draft.copy(from = "3", until = "4", editingRange = 0).addRange(10)
        assertEquals("3-4, 9", edited.draft)
        assertEquals("3-4", edited.removeRange(1, 10).draft)
        assertEquals(listOf(2, 3, 8), edited.confirm(10).selection.resolve(10))
    }

    @Test fun clearingLastRangeNeverSelectsAll() {
        val state = PdfUtilityPageSelectionState().show(UtilityPageSelectionMode.INTERVALS, 2)
            .removeRange(0, 2)
        assertEquals("", state.draft)
        assertTrue(state.draftActive)
        assertTrue(runCatching { state.confirm(2) }.isFailure)
        assertEquals(listOf(0, 1), state.cancel(2).selection.resolve(2))
    }

    @Test fun customOrderRemainsCustomAfterRemovingEarlierPages() {
        val selection = UtilityPageSelection.fromText("5, 2-4, 1", 6).toggle(4, 6).toggle(0, 6)
        assertEquals(listOf(1, 2, 3), selection.resolve(6))
        assertTrue(selection.customOrder)
        val restored = PdfUtilityPageSelectionState(selection = selection)
            .show(UtilityPageSelectionMode.INTERVALS, 6).confirm(6)
        assertTrue(restored.selection.customOrder)
        assertEquals(listOf(1, 2, 3, 0), restored.selection.toggle(0, 6).resolve(6))
    }

    @Test fun unsupportedDocumentsAndLongAdvancedInputKeepParserLimits() {
        assertTrue(runCatching { UtilityPageSelection().resolve(101) }.isFailure)
        assertTrue(runCatching { UtilityPageSelection.fromText("1", 101) }.isFailure)
        assertTrue(runCatching { UtilityPageSelection.fromText("1,".repeat(501), 100) }.isFailure)
        assertTrue(runCatching { PdfUtilityPageSelectionState().edit("1,".repeat(501)).confirm(100) }.isFailure)
        assertTrue(runCatching { UtilityPageSelection.none().toggle(100, 100) }.isFailure)
    }

    @Test fun freshSessionResetsAllWithoutReusingOtherDocumentDraft() {
        val previous = PdfUtilityPageSelectionState().select(UtilityPageSelection.none(), 51)
            .show(UtilityPageSelectionMode.INTERVALS, 51).edit("50-51")
        val fresh = PdfUtilityPageSelectionState()
        assertTrue(previous.selection.resolve(51).isEmpty())
        assertEquals(listOf(0, 1), fresh.selection.resolve(2))
        assertFalse(fresh.draftActive)
    }

    @Test fun switchingFromChipEditorToAdvancedListClearsHiddenFields() {
        val chipEditor = PdfUtilityPageSelectionState(
            selection = UtilityPageSelection.fromText("2-5", 6),
            mode = UtilityPageSelectionMode.INTERVALS, draft = "2-5",
            from = "2", until = "5", editingRange = 0, attemptedRange = true,
        )
        val advanced = chipEditor.showAdvanced(true).edit("5, 2-4, 1")
        assertTrue(advanced.advanced)
        assertTrue(advanced.from.isBlank())
        assertTrue(advanced.until.isBlank())
        assertNull(advanced.editingRange)
        assertFalse(advanced.attemptedRange)
        assertEquals(listOf(4, 1, 2, 3, 0), advanced.confirm(6).selection.resolve(6))
    }

    @Test fun explicitAdvancedNaturalOrderReplacesPreviousCustomOrdering() {
        val custom = PdfUtilityPageSelectionState(selection = UtilityPageSelection.fromText("3, 1, 2", 5))
            .show(UtilityPageSelectionMode.INTERVALS, 5)
        val changed = custom.showAdvanced(true).edit("1-3").confirm(5)
        assertFalse(changed.selection.customOrder)
        assertEquals(listOf(0, 1, 2), changed.selection.resolve(5))
        assertEquals(listOf(0, 2), changed.selection.toggle(1, 5).resolve(5))
        assertEquals(listOf(0, 1, 2), changed.selection.toggle(1, 5).toggle(1, 5).resolve(5))
    }

    private fun restore(state: PdfUtilityPageSelectionState): PdfUtilityPageSelectionState =
        Json.decodeFromString(Json.encodeToString(state))
}
