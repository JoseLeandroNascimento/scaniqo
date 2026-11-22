package com.joseleandro.scaniqo.widget

import com.joseleandro.scaniqo.domain.model.DocumentModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentWidgetUpdatesTest {
    @Test
    fun repeatedReadFailuresRecoverWithBoundedBackoffAndResumeMetadataUpdates() = runTest {
        val document = document()
        val renamed = document.copy(name = "Documento atualizado")
        val subscriptionTimes = mutableListOf<Long>()
        val source = flow {
            subscriptionTimes += currentTime
            if (subscriptionTimes.size <= 7) throw IOException("Temporarily unavailable")
            emit(listOf(document))
            emit(listOf(document))
            emit(listOf(renamed))
        }

        val updates = recentWidgetDocumentUpdates(source).toList()

        assertEquals(
            listOf(0L, 1_000L, 3_000L, 7_000L, 15_000L, 31_000L, 61_000L, 91_000L),
            subscriptionTimes,
        )
        assertEquals(
            listOf(null, recentWidgetDocuments(listOf(document)), recentWidgetDocuments(listOf(renamed))),
            updates,
        )
    }

    @Test
    fun cancellationPropagatesAndCancellingBackoffStopsFurtherSubscriptions() = runTest {
        val cancellation = CancellationException("Widget removed")
        var cancelledSourceSubscriptions = 0
        val cancelledSource: Flow<List<DocumentModel>> = flow {
            cancelledSourceSubscriptions++
            throw cancellation
        }
        val cancelledUpdates = mutableListOf<List<RecentWidgetDocument>?>()
        try {
            recentWidgetDocumentUpdates(cancelledSource).toList(cancelledUpdates)
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertEquals(cancellation.message, actual.message)
        }
        assertEquals(1, cancelledSourceSubscriptions)
        assertTrue(cancelledUpdates.isEmpty())
        assertEquals(0L, currentTime)

        var retryingSubscriptions = 0
        val unavailableSource: Flow<List<DocumentModel>> = flow {
            retryingSubscriptions++
            throw IOException("Temporarily unavailable")
        }
        val errorUpdates = mutableListOf<List<RecentWidgetDocument>?>()
        val job = launch { recentWidgetDocumentUpdates(unavailableSource).toList(errorUpdates) }
        runCurrent()
        assertEquals(listOf<List<RecentWidgetDocument>?>(null), errorUpdates)

        job.cancelAndJoin()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, retryingSubscriptions)
        assertTrue(job.isCancelled)
    }

    private fun document() = DocumentModel(
        id = 7,
        name = "Documento",
        pdfUri = "content://documents/7",
        pageCount = 2,
        createdAt = 7_000,
        sizeBytes = 4_096,
    )
}
