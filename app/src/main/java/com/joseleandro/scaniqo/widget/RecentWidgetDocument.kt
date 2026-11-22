package com.joseleandro.scaniqo.widget

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

internal data class RecentWidgetDocument(
    val id: Long,
    val name: String,
    val pageCount: Int,
    val createdAt: Long,
    val isProtected: Boolean,
)

internal fun recentWidgetDocuments(
    documents: List<DocumentModel>,
): List<RecentWidgetDocument> =
    documents.asSequence()
        .filter { it.id > 0 && it.deletedAt == null }
        .sortedWith(compareByDescending<DocumentModel> { it.createdAt }.thenByDescending { it.id })
        .take(5)
        .map {
            RecentWidgetDocument(
                id = it.id,
                name = it.name,
                pageCount = it.pageCount,
                createdAt = it.createdAt,
                isProtected = it.isProtected,
            )
        }
        .toList()

internal fun recentWidgetDocumentUpdates(
    documents: Flow<List<DocumentModel>>,
): Flow<List<RecentWidgetDocument>?> = documents
    .map<List<DocumentModel>, List<RecentWidgetDocument>?> { recentWidgetDocuments(it) }
    .retryWhen { failure, attempt ->
        if (failure is CancellationException) throw failure
        if (failure !is Exception) return@retryWhen false
        emit(null)
        delay((1_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30_000L))
        true
    }
    .distinctUntilChanged()

internal suspend fun loadRecentWidgetDocuments(): List<RecentWidgetDocument>? =
    withContext(Dispatchers.IO) {
        try {
            val koin = GlobalContext.get()
            val documents = koin.get<DocumentRepository>().observeDocuments().first()
            recentWidgetDocuments(documents)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
