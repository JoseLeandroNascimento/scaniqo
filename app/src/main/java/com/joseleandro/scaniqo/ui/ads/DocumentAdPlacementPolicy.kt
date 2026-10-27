package com.joseleandro.scaniqo.ui.ads

internal sealed interface DocumentListRow {
    val key: Any

    data class Document(val id: Long, val documentIndex: Int) : DocumentListRow {
        override val key: Any get() = id
    }

    data class NativeAdSlot(val slot: Int) : DocumentListRow {
        override val key: Any get() = "native_ad_$slot"
    }
}

internal object DocumentAdPlacementPolicy {
    fun afterDocumentCounts(
        documentCount: Int,
        enabled: Boolean = true,
        isLoading: Boolean = false,
        hasError: Boolean = false,
        hasBlockingAction: Boolean = false,
    ): List<Int> {
        if (!enabled || isLoading || hasError || hasBlockingAction) return emptyList()
        return listOf(6, 18).filter { it <= documentCount }
    }

    fun rows(
        documentIds: List<Long>,
        afterDocumentCounts: List<Int>,
        loadedSlots: Set<Int>,
    ): List<DocumentListRow> {
        val slotsByDocumentCount = afterDocumentCounts.take(2)
            .mapIndexedNotNull { slot, count ->
                if (slot in loadedSlots) count to slot else null
            }
            .toMap()
        return buildList {
            documentIds.forEachIndexed { index, id ->
                add(DocumentListRow.Document(id, index))
                slotsByDocumentCount[index + 1]?.let { slot ->
                    add(DocumentListRow.NativeAdSlot(slot))
                }
            }
        }
    }
}
