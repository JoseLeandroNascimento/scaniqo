package com.joseleandro.scaniqo.ui.documentCommon

import androidx.compose.runtime.saveable.SaverScope
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMergeSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentMergeSelectionStateTest {
    private val document = DocumentModel(
        id = 7,
        name = "Contrato",
        pdfUri = "file:///contract.pdf",
        pageCount = 3,
        createdAt = 0,
        sizeBytes = 120,
        protectionKeyId = "protected-key",
    )
    private val external = DocumentMergeItem("content://files/1", "Anexo.pdf")
    private val otherExternal = DocumentMergeItem("content://files/2", "Comprovante.pdf")

    @Test
    fun addingFilesKeepsExistingOrderAndDeduplicatesRepeatedPickerResults() {
        val state = DocumentMergeSelectionState()
        state.add(listOf(document.toMergeItem(), external))
        state.add(listOf(external.copy(name = "Nome atualizado.pdf"), otherExternal, external))

        assertEquals(listOf(document.toMergeItem().key, external.key, otherExternal.key), state.items.map { it.key })
        assertEquals(external, state.items[1])
    }

    @Test
    fun reorderSupportsMixedSourcesAndKeepsSelectionsAddedAfterTheGestureStarted() {
        val state = DocumentMergeSelectionState(listOf(document.toMergeItem(), external, otherExternal))
        state.reorder(listOf(external.key, document.toMergeItem().key))

        assertEquals(listOf(external, document.toMergeItem(), otherExternal), state.items)
    }

    @Test
    fun invalidOrRepeatedKeysCannotCorruptTheOrder() {
        val initial = listOf(document.toMergeItem(), external)
        val state = DocumentMergeSelectionState(initial)
        state.reorder(listOf("missing", external.key))
        assertEquals(initial, state.items)
        state.reorder(listOf(external.key, external.key))
        assertEquals(initial, state.items)
    }

    @Test
    fun togglingAndRemovingPreserveTheOrderOfTheRemainingItems() {
        val state = DocumentMergeSelectionState(listOf(document.toMergeItem(), external, otherExternal))
        state.toggle(external)
        assertEquals(listOf(document.toMergeItem(), otherExternal), state.items)
        state.toggle(external)
        assertEquals(listOf(document.toMergeItem(), otherExternal, external), state.items)
        state.remove(document.toMergeItem().key)
        assertEquals(listOf(otherExternal, external), state.items)
        state.clear()
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun confirmationUsesLatestLibraryMetadataAndOmitsDeletedDocuments() {
        val state = DocumentMergeSelectionState(listOf(external, document.toMergeItem(), otherExternal))
        val updated = document.copy(name = "Contrato atualizado", pdfUri = "file:///updated.pdf", pageCount = 8)

        assertEquals(listOf(external, updated.toMergeItem(), otherExternal), state.resolve(listOf(updated)))
        assertEquals(listOf(external, otherExternal), state.resolve(emptyList()))
        // Uma emissão temporária vazia não apaga referências salvas.
        assertEquals(listOf(external, updated.toMergeItem(), otherExternal), state.resolve(listOf(updated)))
    }

    @Test
    fun savedSelectionRestoresMixedSourceOrderAndProtectionReferences() {
        val state = DocumentMergeSelectionState(listOf(document.toMergeItem(), external, otherExternal))
        state.reorder(listOf(otherExternal.key, document.toMergeItem().key, external.key))
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(DocumentMergeSelectionState.Saver) { scope.save(state) }
        val restored = DocumentMergeSelectionState.Saver.restore(checkNotNull(saved))

        assertEquals(state.items, restored!!.items)
        assertEquals("protected-key", restored.items[1].protectionKeyId)
    }

    @Test
    fun theSameUriSelectedFromLibraryAndFilesCountsOnceAndKeepsLibraryMetadata() {
        val fromDevice = DocumentMergeItem(document.pdfUri, "Nome externo.pdf")
        val state = DocumentMergeSelectionState(listOf(external, fromDevice, document.toMergeItem()))
        assertEquals(listOf(external, document.toMergeItem()), state.items)

        state.add(listOf(fromDevice, otherExternal))
        assertEquals(listOf(external, document.toMergeItem(), otherExternal), state.items)
        assertEquals(3, state.resolve(listOf(document)).size)

        val updated = document.copy(pdfUri = otherExternal.uri)
        assertEquals(listOf(external, updated.toMergeItem()), state.resolve(listOf(updated)))
    }


    @Test
    fun currentDocumentKeepsItsLivePageCountAndCannotBeRemovedOrDowngraded() {
        val current = document.toMergeItem().copy(pageCount = 9, isCurrentDocument = true)
        val state = DocumentMergeSelectionState(listOf(external))
        state.includeCurrentDocument(current)
        state.add(listOf(document.toMergeItem(), DocumentMergeItem(document.pdfUri, "Duplicado")))
        state.remove(current.key)
        state.toggle(current)

        assertEquals(listOf(current, external), state.items)
        assertEquals(listOf(current, external), state.resolve(listOf(document)))
        assertEquals(listOf(current, external), state.resolve(emptyList()))
        state.clear()
        assertEquals(listOf(current), state.items)
    }

    @Test
    fun refreshingCurrentDocumentPreservesItsDraggedPositionAndSavedIdentity() {
        val current = document.toMergeItem().copy(isCurrentDocument = true)
        val state = DocumentMergeSelectionState(listOf(current, external, otherExternal))
        state.reorder(listOf(external.key, current.key, otherExternal.key))
        val updated = current.copy(name = "Sessão editada", pageCount = 12)
        state.includeCurrentDocument(updated)
        val saverScope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(DocumentMergeSelectionState.Saver) { saverScope.save(state) }
        val restored = checkNotNull(DocumentMergeSelectionState.Saver.restore(checkNotNull(saved)))

        assertEquals(listOf(external, updated, otherExternal), restored.items)
        restored.remove(updated.key)
        assertEquals(listOf(external, updated, otherExternal), restored.items)
    }

    @Test
    fun blankFileUrisAreIgnored() {
        val state = DocumentMergeSelectionState()
        state.add(listOf(DocumentMergeItem("", "Inválido"), external))

        assertEquals(listOf(external), state.items)
    }
}
