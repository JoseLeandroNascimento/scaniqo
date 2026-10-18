package com.joseleandro.scaniqo.ui.documentCommon.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.uniqueMergeItems

/** Conserva referências ordenadas durante busca, rotação e abertura do seletor de arquivos. */
@Stable
class DocumentMergeSelectionState(initialItems: List<DocumentMergeItem> = emptyList()) {
    var items: List<DocumentMergeItem> by mutableStateOf(uniqueMergeItems(initialItems))
        private set

    fun add(items: List<DocumentMergeItem>) {
        this.items = uniqueMergeItems(this.items + items)
    }

    /** Atualiza a sessão sem mudar a posição escolhida ao arrastar. */
    fun includeCurrentDocument(item: DocumentMergeItem) {
        val current = item.copy(isCurrentDocument = true)
        val existing = items.indexOfFirst { it.key == current.key || it.uri == current.uri }
        items = if (existing < 0) uniqueMergeItems(listOf(current) + items) else {
            uniqueMergeItems(items.mapIndexed { index, previous -> if (index == existing) current else previous })
        }
    }

    fun toggle(item: DocumentMergeItem) {
        if (items.any { it.key == item.key }) remove(item.key) else add(listOf(item))
    }

    fun remove(key: String) {
        items = items.filterNot { it.key == key && !it.isCurrentDocument }
    }

    fun reorder(keys: List<String>) {
        if (keys.distinct().size != keys.size) return
        val byKey = items.associateBy { it.key }
        if (keys.any { it !in byKey }) return
        // Itens fora do catálogo atual não desaparecem apenas porque a lista está recarregando.
        items = keys.mapNotNull(byKey::get) + items.filterNot { it.key in keys }
    }

    fun clear() {
        items = items.filter { it.isCurrentDocument }
    }

    /** Usa metadados atuais do app e mantém as referências de arquivos externos. */
    fun resolve(documents: List<DocumentModel>): List<DocumentMergeItem> {
        val current = documents.associateBy { it.id }
        return uniqueMergeItems(items.mapNotNull { item ->
            if (item.isCurrentDocument) item else {
                item.documentId?.let { current[it]?.toMergeItem() } ?: item.takeIf { it.documentId == null }
            }
        })
    }

    companion object {
        val Saver = listSaver<DocumentMergeSelectionState, Any?>(
            save = { state ->
                listOf("merge-selection-v2") + state.items.flatMap {
                    listOf(it.uri, it.name, it.documentId, it.pageCount, it.protectionKeyId, it.isCurrentDocument)
                }
            },
            restore = { values ->
                val hasCurrentFlag = values.firstOrNull() == "merge-selection-v2"
                val fields = if (hasCurrentFlag) values.drop(1) else values
                DocumentMergeSelectionState(
                    fields.chunked(if (hasCurrentFlag) 6 else 5).map { item ->
                        DocumentMergeItem(
                            uri = item[0] as String,
                            name = item[1] as String,
                            documentId = item[2] as Long?,
                            pageCount = item[3] as Int?,
                            protectionKeyId = item[4] as String?,
                            isCurrentDocument = hasCurrentFlag && item[5] as Boolean,
                        )
                    },
                )
            },
        )
    }
}

@Composable
fun rememberDocumentMergeSelectionState(selectionKey: Any? = null): DocumentMergeSelectionState =
    rememberSaveable(selectionKey, saver = DocumentMergeSelectionState.Saver) {
        DocumentMergeSelectionState()
    }
