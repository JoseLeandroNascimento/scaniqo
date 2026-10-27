package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentPagePreviewController
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentPagePreviewControllerTest {
    @Test fun requestingOnePageDoesNotPrepareOtherNinetyNine() = runTest {
        val calls = mutableListOf<String>()
        var states = emptyMap<String, DocumentPagePreviewState>()
        val controller = DocumentPagePreviewController(this,
            { _, id -> calls += id; PdfPagePreview("file:///$id.pdf") },
            { states = it },
        )
        controller.request(workspace(100), "page-51")
        controller.request(workspace(100), "unknown")
        advanceUntilIdle()
        assertEquals(listOf("page-51"), calls)
        assertEquals(setOf("page-51"), states.keys)
        assertEquals(0, states.getValue("page-51").preview!!.pageIndex)
    }

    @Test fun identicalPendingAndCompletedRequestsDoNotRenderAgain() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        var states = emptyMap<String, DocumentPagePreviewState>()
        val controller = DocumentPagePreviewController(this,
            { _, _ -> calls++; gate.await(); PdfPagePreview("file:///ready.pdf") },
            { states = it },
        )
        val workspace = workspace()
        controller.request(workspace, "page-1")
        runCurrent()
        controller.request(workspace, "page-1")
        controller.request(workspace.copy(exportQuality = PdfExportQuality.SMALL, ocrEnabled = true), "page-1")
        assertEquals(1, calls)
        gate.complete(Unit)
        advanceUntilIdle()
        controller.request(workspace, "page-1")
        advanceUntilIdle()
        assertEquals(1, calls)
        assertFalse(states.getValue("page-1").isRendering)
    }

    @Test fun overlayIdsStayTiedToTheDisplayedPreviewDuringAnotherRender() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var states = emptyMap<String, DocumentPagePreviewState>()
        val controller = DocumentPagePreviewController(this,
            { _, _ ->
                calls++
                if (calls == 2) gate.await()
                PdfPagePreview("file:///render-$calls.pdf")
            },
            { states = it },
        )
        val original = workspace().copy(pages = workspace().pages.map { page ->
            if (page.id == "page-1") page.copy(overlays = listOf(PdfOverlay("a", PdfOverlayKind.CHECKMARK))) else page
        })
        controller.request(original, "page-1")
        advanceUntilIdle()
        assertEquals(setOf("a"), states.getValue("page-1").renderedOverlayIds)

        val next = original.copy(pages = original.pages.map { page ->
            if (page.id == "page-1") page.copy(overlays = page.overlays + PdfOverlay("b", PdfOverlayKind.CROSSMARK)) else page
        })
        controller.request(next, "page-1")
        runCurrent()
        assertTrue(states.getValue("page-1").isRendering)
        assertEquals(setOf("a"), states.getValue("page-1").renderedOverlayIds)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(setOf("a", "b"), states.getValue("page-1").renderedOverlayIds)
        assertEquals("file:///render-2.pdf", states.getValue("page-1").preview!!.uri)
    }

    @Test fun updatingOnePageLeavesOtherCachedPreviewUntouched() = runTest {
        val calls = mutableListOf<String>()
        var states = emptyMap<String, DocumentPagePreviewState>()
        val controller = DocumentPagePreviewController(this,
            { _, id -> calls += id; PdfPagePreview("file:///$id-${calls.size}.pdf") },
            { states = it },
        )
        val original = workspace()
        controller.request(original, "page-1")
        controller.request(original, "page-2")
        advanceUntilIdle()
        val second = states.getValue("page-2").preview
        val updated = original.filtered("page-1", PdfImageFilter.ENHANCE)
        controller.request(updated, "page-1")
        controller.request(updated, "page-2")
        advanceUntilIdle()
        assertEquals(listOf("page-1", "page-2", "page-1"), calls)
        assertEquals(second, states.getValue("page-2").preview)
    }

    @Test fun reorderReusesPageUriUntilVisibleNumberingChanges() = runTest {
        var calls = 0
        var states = emptyMap<String, DocumentPagePreviewState>()
        val controller = DocumentPagePreviewController(this,
            { _, _ -> calls++; PdfPagePreview("file:///page-$calls.pdf", 0) },
            { states = it },
        )
        val original = workspace()
        controller.request(original, "page-1")
        advanceUntilIdle()
        val first = states.getValue("page-1").preview
        val reordered = original.copy(pages = original.pages.reversed())
        controller.request(reordered, "page-1")
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(first, states.getValue("page-1").preview)
        assertEquals(0, states.getValue("page-1").preview!!.pageIndex)

        controller.request(reordered.copy(showPageNumbers = true), "page-1")
        advanceUntilIdle()
        assertEquals(2, calls)
        controller.request(original.copy(showPageNumbers = true), "page-1")
        advanceUntilIdle()
        assertEquals(3, calls)
    }

    @Test fun debounceOnlyRendersLatestSliderValue() = runTest {
        val calls = mutableListOf<PdfWorkspace>()
        val controller = DocumentPagePreviewController(this,
            { value, _ -> calls += value; PdfPagePreview("file:///ready.pdf") }, {},
        )
        controller.request(workspace().filtered("page-1", PdfImageFilter.WHITEN),
            "page-1", debounceMillis = 120)
        advanceTimeBy(60)
        controller.request(workspace().filtered("page-1", PdfImageFilter.DENOISE),
            "page-1", debounceMillis = 120)
        advanceTimeBy(119)
        runCurrent()
        assertTrue(calls.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, calls.size)
        assertEquals(PdfImageFilter.DENOISE, calls.single().pages.first().layout.imageFilter)
    }

    @Test fun oldUncancellableResultCannotOverwriteNewerPage() = runTest {
        val oldGate = CompletableDeferred<Unit>()
        var calls = 0
        var states = emptyMap<String, DocumentPagePreviewState>()
        val controller = DocumentPagePreviewController(this,
            { _, _ ->
                val call = ++calls
                if (call == 1) withContext(NonCancellable) { oldGate.await() }
                PdfPagePreview("file:///page-$call.pdf")
            },
            { states = it },
        )
        controller.request(workspace(), "page-1")
        runCurrent()
        controller.request(workspace().filtered("page-1", PdfImageFilter.ENHANCE), "page-1")
        runCurrent()
        assertEquals("file:///page-2.pdf", states.getValue("page-1").preview!!.uri)
        oldGate.complete(Unit)
        advanceUntilIdle()
        assertEquals("file:///page-2.pdf", states.getValue("page-1").preview!!.uri)
        assertFalse(states.getValue("page-1").isRendering)
    }

    @Test fun failureRetainsLastGoodPageAndRetryCanReplaceIt() = runTest {
        var fail = false
        var calls = 0
        var states = emptyMap<String, DocumentPagePreviewState>()
        val controller = DocumentPagePreviewController(this,
            { _, _ ->
                calls++
                if (fail) error("Sem espaço para renderizar")
                PdfPagePreview("file:///page-$calls.pdf")
            },
            { states = it },
        )
        controller.request(workspace(), "page-1")
        advanceUntilIdle()
        val original = states.getValue("page-1").preview
        fail = true
        val edited = workspace().filtered("page-1", PdfImageFilter.SHARPEN)
        controller.request(edited, "page-1")
        advanceUntilIdle()
        assertEquals(original, states.getValue("page-1").preview)
        assertTrue(states.getValue("page-1").error)
        assertFalse(states.getValue("page-1").isRendering)
        fail = false
        controller.request(edited, "page-1", force = true)
        advanceUntilIdle()
        assertEquals("file:///page-3.pdf", states.getValue("page-1").preview!!.uri)
        assertFalse(states.getValue("page-1").error)
    }

    @Test fun clearingDropsLateResultsEvenWhenNextDocumentReusesPageId() = runTest {
        val gate = CompletableDeferred<Unit>()
        var states = emptyMap<String, DocumentPagePreviewState>()
        val controller = DocumentPagePreviewController(this,
            { value, _ ->
                if (value.workspaceId == "old") withContext(NonCancellable) { gate.await() }
                PdfPagePreview("file:///${value.workspaceId}.pdf")
            },
            { states = it },
        )
        controller.request(workspace().copy(workspaceId = "old"), "page-1")
        runCurrent()
        controller.clear()
        assertTrue(states.isEmpty())
        controller.request(workspace().copy(workspaceId = "new"), "page-1")
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("file:///new.pdf", states.getValue("page-1").preview!!.uri)
    }

    @Test fun comparisonMetadataUsesLatestRequestEvenForSamePendingImage() = runTest {
        val gate = CompletableDeferred<Unit>()
        var states = emptyMap<String, DocumentPagePreviewState>()
        var calls = 0
        val controller = DocumentPagePreviewController(this,
            { _, _ -> calls++; gate.await(); PdfPagePreview("file:///ready.pdf") },
            { states = it },
        )
        controller.request(workspace(), "page-1", showingOriginal = false)
        runCurrent()
        controller.request(workspace(), "page-1", showingOriginal = true)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(states.getValue("page-1").showingOriginal)
    }

    @Test fun removedPageAndCancelledInvisibleWorkDoNotPublishLatePreviews() = runTest {
        val gate = CompletableDeferred<Unit>()
        var states = emptyMap<String, DocumentPagePreviewState>()
        val controller = DocumentPagePreviewController(this,
            { _, id ->
                if (id == "page-2") gate.await()
                PdfPagePreview("file:///$id.pdf")
            },
            { states = it },
        )
        controller.request(workspace(), "page-1")
        controller.request(workspace(), "page-2")
        runCurrent()
        controller.cancelExcept(setOf("page-1"))
        assertFalse(states.getValue("page-2").isRendering)
        assertNull(states.getValue("page-2").preview)
        controller.retainPages(setOf("page-1"))
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(setOf("page-1"), states.keys)
    }

    private fun workspace(count: Int = 3) = PdfWorkspace(
        documentId = 1, workspaceId = "workspace", directoryUri = "file:///workspace",
        pages = (1..count).map { PdfPageSpec("page-$it", "file:///source.pdf", it - 1) },
    )

    private fun PdfWorkspace.filtered(id: String, filter: PdfImageFilter) = copy(
        pages = pages.map { if (it.id == id) it.copy(layout = it.layout.copy(imageFilter = filter)) else it },
    )
}
