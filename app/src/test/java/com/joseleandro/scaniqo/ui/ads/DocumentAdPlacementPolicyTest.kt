package com.joseleandro.scaniqo.ui.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAdPlacementPolicyTest {
    @Test
    fun shortAndEmptyLibrariesHaveNoAds() {
        listOf(-1, 0, 1, 5).forEach {
            assertTrue(DocumentAdPlacementPolicy.afterDocumentCounts(it).isEmpty())
        }
    }

    @Test
    fun firstAdFollowsSixActualDocuments() {
        assertEquals(listOf(6), DocumentAdPlacementPolicy.afterDocumentCounts(6))
        assertEquals(listOf(6), DocumentAdPlacementPolicy.afterDocumentCounts(17))
    }

    @Test
    fun secondAdRequiresTwelveMoreDocumentsAndLibraryHasAtMostTwoAds() {
        assertEquals(listOf(6, 18), DocumentAdPlacementPolicy.afterDocumentCounts(18))
        assertEquals(listOf(6, 18), DocumentAdPlacementPolicy.afterDocumentCounts(Int.MAX_VALUE))
    }

    @Test
    fun loadingErrorsAndBlockingActionsNeverContainAdSlots() {
        assertTrue(DocumentAdPlacementPolicy.afterDocumentCounts(30, isLoading = true).isEmpty())
        assertTrue(DocumentAdPlacementPolicy.afterDocumentCounts(30, hasError = true).isEmpty())
        assertTrue(DocumentAdPlacementPolicy.afterDocumentCounts(30, hasBlockingAction = true).isEmpty())
    }

    @Test
    fun inactiveOrSearchingRouteNeverContainsAdSlots() {
        assertTrue(DocumentAdPlacementPolicy.afterDocumentCounts(30, enabled = false).isEmpty())
    }

    @Test
    fun loadedAdsHaveSeparateStableKeysAndFollowSixAndEighteenDocuments() {
        val ids = (1L..20L).toList()
        val rows = DocumentAdPlacementPolicy.rows(ids, listOf(6, 18), setOf(0, 1))
        assertEquals(DocumentListRow.NativeAdSlot(0), rows[6])
        assertEquals(DocumentListRow.NativeAdSlot(1), rows[19])
        assertEquals(ids, rows.filterIsInstance<DocumentListRow.Document>().map { it.id })
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }

    @Test
    fun unloadedSlotsDoNotCreateEmptyRowsOrChangeDocumentOrder() {
        val ids = (1L..20L).toList()
        val withoutAds = DocumentAdPlacementPolicy.rows(ids, listOf(6, 18), emptySet())
        val secondOnly = DocumentAdPlacementPolicy.rows(ids, listOf(6, 18), setOf(1))
        assertEquals(ids, withoutAds.map { it.key })
        assertEquals(20, withoutAds.size)
        assertEquals(21, secondOnly.size)
        assertEquals(DocumentListRow.NativeAdSlot(1), secondOnly[18])
        assertEquals(ids, secondOnly.filterIsInstance<DocumentListRow.Document>().map { it.id })
    }

    @Test
    fun reorderingDocumentsPreservesEveryDocumentAndEachAdIdentityOnce() {
        val ids = (1L..20L).toList()
        val before = DocumentAdPlacementPolicy.rows(ids, listOf(6, 18), setOf(0, 1))
        val reordered = listOf(100L) + ids.reversed()
        val after = DocumentAdPlacementPolicy.rows(reordered, listOf(6, 18), setOf(0, 1))
        assertEquals(reordered, after.filterIsInstance<DocumentListRow.Document>().map { it.id })
        assertEquals(
            before.filterIsInstance<DocumentListRow.NativeAdSlot>().map { it.key },
            after.filterIsInstance<DocumentListRow.NativeAdSlot>().map { it.key },
        )
        assertEquals(after.size, after.map { it.key }.distinct().size)
        after.filterIsInstance<DocumentListRow.Document>().forEach { row ->
            assertEquals(reordered[row.documentIndex], row.id)
        }
    }

    @Test
    fun shrinkingLibraryCannotKeepAdSlotsPastItsLastDocument() {
        val rows = DocumentAdPlacementPolicy.rows((1L..5L).toList(), listOf(6, 18), setOf(0, 1))
        assertEquals(5, rows.size)
        assertTrue(rows.none { it is DocumentListRow.NativeAdSlot })
    }
}

