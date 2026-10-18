package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentToolPreviewController
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentToolPreviewControllerTest {
    @Test fun supportedPagesUsePixelsAndLayoutChangesDoNotRenderAgain() = runTest {
        val sizes = mutableListOf<Int>()
        var pdfCalls = 0
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { _, _, size -> sizes += size; pixels(size) },
            { _, _ -> pdfCalls++; PdfPagePreview("file:///fallback.pdf") },
            { state = it },
        )
        val original = workspace()
        controller.request(original, "page-1")
        advanceUntilIdle()
        val displayed = state.live.pixels
        val page = original.pages.first()
        val layoutOnly = original.copy(
            showPageNumbers = true,
            pages = listOf(page.copy(
                layout = page.layout.copy(
                    alignment = PageAlignment.RIGHT,
                    scaleMode = PageScaleMode.FILL,
                    rotationQuarterTurns = 1,
                    contentScale = 1.5f,
                ),
                signatures = listOf(PdfSignaturePlacement("signature", "file:///signature.png")),
            )),
        )
        controller.request(layoutOnly, "page-1")
        controller.request(layoutOnly, "missing")
        advanceUntilIdle()
        assertEquals(listOf(480, 1100), sizes)
        assertEquals(0, pdfCalls)
        assertSame(displayed, state.live.pixels)
        assertNull(state.preview)
        assertFalse(state.isRendering)
        assertFalse(state.error)
    }

    @Test fun unsupportedResultUsesNewestSnapshotAndDebounce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val pdfRequests = mutableListOf<PdfWorkspace>()
        var pixelCalls = 0
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { _, _, _ -> pixelCalls++; gate.await(); null },
            { value, _ -> pdfRequests += value; PdfPagePreview("file:///fallback.pdf") },
            { state = it },
        )
        controller.request(workspace(), "page-1", debounceMillis = 120)
        runCurrent()
        advanceTimeBy(40)
        val latest = workspace().scaled(1.7f)
        controller.request(latest, "page-1", debounceMillis = 25)
        gate.complete(Unit)
        runCurrent()
        advanceTimeBy(24)
        runCurrent()
        assertTrue(pdfRequests.isEmpty())
        assertTrue(state.isRendering)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(latest), pdfRequests)
        assertEquals(1, pixelCalls)
        assertEquals("file:///fallback.pdf", state.preview?.uri)
        assertTrue(state.live.unsupported)
        assertFalse(state.isRendering)
    }

    @Test fun unsupportedLayoutChangesUpdateFallbackEvenWhenLiveDoesNotPublish() = runTest {
        val renderedScales = mutableListOf<Float>()
        var pixelCalls = 0
        val controller = DocumentToolPreviewController(this,
            { _, _, _ -> pixelCalls++; null },
            { value, _ ->
                renderedScales += value.pages.first().layout.contentScale
                PdfPagePreview("file:///fallback.pdf")
            }, {},
        )
        controller.request(workspace(), "page-1")
        runCurrent()
        advanceTimeBy(20)
        controller.request(workspace().scaled(1.2f), "page-1", debounceMillis = 80)
        advanceTimeBy(79)
        runCurrent()
        assertTrue(renderedScales.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(1.2f), renderedScales)
        controller.request(workspace().scaled(1.4f), "page-1", debounceMillis = 40)
        advanceUntilIdle()
        assertEquals(listOf(1.2f, 1.4f), renderedScales)
        assertEquals(1, pixelCalls)
    }

    @Test fun liveFailureReportsErrorWithoutPdfAndCanBeRetried() = runTest {
        var fail = true
        var pdfCalls = 0
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { _, _, _ -> if (fail) error("Falha na amostra"); pixels() },
            { _, _ -> pdfCalls++; PdfPagePreview("file:///fallback.pdf") },
            { state = it },
        )
        controller.request(workspace(), "page-1")
        advanceUntilIdle()
        assertTrue(state.error)
        assertFalse(state.isRendering)
        assertFalse(state.live.unsupported)
        assertEquals(0, pdfCalls)
        fail = false
        controller.request(workspace(), "page-1")
        advanceUntilIdle()
        assertFalse(state.error)
        assertTrue(state.live.pixels != null)
        assertEquals(0, pdfCalls)
    }

    @Test fun failedFallbackKeepsDisplayedPdfAndOriginalLabelUntilRetrySucceeds() = runTest {
        var fail = false
        var calls = 0
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { _, _, _ -> null },
            { _, _ ->
                calls++
                if (fail) error("Falha no PDF")
                PdfPagePreview("file:///fallback-$calls.pdf")
            },
            { state = it },
        )
        controller.request(workspace(), "page-1", showingOriginal = true)
        advanceUntilIdle()
        val displayed = state.preview
        fail = true
        val changed = workspace().scaled(1.5f)
        controller.request(changed, "page-1", showingOriginal = false)
        advanceUntilIdle()
        assertSame(displayed, state.preview)
        assertTrue(state.showingOriginal)
        assertTrue(state.error)
        assertFalse(state.isRendering)
        fail = false
        controller.request(changed, "page-1", showingOriginal = false)
        advanceUntilIdle()
        assertEquals("file:///fallback-3.pdf", state.preview?.uri)
        assertFalse(state.error)
        assertFalse(state.showingOriginal)
    }

    @Test fun oldFallbackCannotReplaceLatestLayoutEvenWhenCancellationIsIgnored() = runTest {
        val oldGate = CompletableDeferred<Unit>()
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { _, _, _ -> null },
            { value, _ ->
                val scale = value.pages.first().layout.contentScale
                if (scale == 1f) withContext(NonCancellable) { oldGate.await() }
                PdfPagePreview("file:///scale-$scale.pdf")
            },
            { state = it },
        )
        controller.request(workspace(), "page-1", debounceMillis = 0)
        runCurrent()
        controller.request(workspace().scaled(1.8f), "page-1", debounceMillis = 0)
        runCurrent()
        assertEquals("file:///scale-1.8.pdf", state.preview?.uri)
        oldGate.complete(Unit)
        advanceUntilIdle()
        assertEquals("file:///scale-1.8.pdf", state.preview?.uri)
        assertFalse(state.isRendering)
    }

    @Test fun pauseCancelsPendingFallbackAndPreservesDisplayedPdf() = runTest {
        val gate = CompletableDeferred<Unit>()
        var state = DocumentToolPreviewState()
        var calls = 0
        val controller = DocumentToolPreviewController(this,
            { _, _, _ -> null },
            { value, _ ->
                calls++
                val scale = value.pages.first().layout.contentScale
                if (scale != 1f) withContext(NonCancellable) { gate.await() }
                PdfPagePreview("file:///scale-$scale.pdf")
            },
            { state = it },
        )
        controller.request(workspace(), "page-1", showingOriginal = true, debounceMillis = 0)
        advanceUntilIdle()
        val displayed = state.preview
        controller.request(workspace().scaled(1.3f), "page-1", debounceMillis = 0)
        runCurrent()
        controller.pause()
        gate.complete(Unit)
        advanceUntilIdle()
        assertSame(displayed, state.preview)
        assertTrue(state.showingOriginal)
        assertFalse(state.isRendering)
        assertEquals(2, calls)
        controller.request(workspace().scaled(1.3f), "page-1", debounceMillis = 0)
        advanceUntilIdle()
        assertEquals("file:///scale-1.3.pdf", state.preview?.uri)
        assertFalse(state.showingOriginal)
    }

    @Test fun pauseCancelsPixelsAndRetainsLastFrameAndComparisonLabel() = runTest {
        val gate = CompletableDeferred<Unit>()
        var state = DocumentToolPreviewState()
        var pdfCalls = 0
        val controller = DocumentToolPreviewController(this,
            { value, _, _ ->
                if (value.pages.first().layout.imageFilter == PdfImageFilter.ENHANCE) {
                    withContext(NonCancellable) { gate.await() }
                }
                pixels()
            },
            { _, _ -> pdfCalls++; PdfPagePreview("file:///fallback.pdf") },
            { state = it },
        )
        controller.request(workspace(), "page-1", showingOriginal = true)
        advanceUntilIdle()
        val displayed = state.live.pixels
        controller.request(workspace().filtered(), "page-1")
        runCurrent()
        assertTrue(state.showingOriginal)
        controller.pause()
        gate.complete(Unit)
        advanceUntilIdle()
        assertSame(displayed, state.live.pixels)
        assertTrue(state.showingOriginal)
        assertFalse(state.isRendering)
        assertEquals(0, pdfCalls)
    }

    @Test fun pausingBeforeUnsupportedResultDoesNotStartFallback() = runTest {
        val gate = CompletableDeferred<Unit>()
        var pdfCalls = 0
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { _, _, _ -> withContext(NonCancellable) { gate.await() }; null },
            { _, _ -> pdfCalls++; PdfPagePreview("file:///fallback.pdf") },
            { state = it },
        )
        controller.request(workspace(), "page-1")
        runCurrent()
        controller.pause()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, pdfCalls)
        assertFalse(state.isRendering)
        assertNull(state.preview)
        assertNull(state.live.pixels)
    }

    @Test fun clearRejectsLatePdfAndResetsEveryVisibleField() = runTest {
        val gate = CompletableDeferred<Unit>()
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { _, _, _ -> null },
            { _, _ ->
                withContext(NonCancellable) { gate.await() }
                PdfPagePreview("file:///late.pdf")
            },
            { state = it },
        )
        controller.request(workspace(), "page-1", showingOriginal = true, debounceMillis = 0)
        runCurrent()
        controller.clear()
        assertEquals(DocumentToolPreviewState(), state)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(DocumentToolPreviewState(), state)
    }

    @Test fun clearRejectsLatePixelsAndCanStartAgain() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { _, _, _ ->
                if (++calls == 1) withContext(NonCancellable) { gate.await() }
                pixels(calls)
            },
            { _, _ -> error("PDF inesperado") },
            { state = it },
        )
        controller.request(workspace(), "page-1", showingOriginal = true)
        runCurrent()
        controller.clear()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(DocumentToolPreviewState(), state)
        controller.request(workspace(), "page-1")
        advanceUntilIdle()
        assertTrue(state.live.pixels != null)
        assertFalse(state.showingOriginal)
    }

    @Test fun workspaceAndPageIdentityClearFramesAndRejectOldWork() = runTest {
        val oldGate = CompletableDeferred<Unit>()
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { value, id, _ ->
                if (value.workspaceId == "old") withContext(NonCancellable) { oldGate.await() }
                pixels(if (id == "page-1") 1 else 2)
            },
            { _, _ -> error("PDF inesperado") },
            { state = it },
        )
        controller.request(workspace().copy(workspaceId = "old"), "page-1")
        runCurrent()
        controller.request(workspace(), "page-1")
        assertNull(state.live.pixels)
        runCurrent()
        val newFrame = state.live.pixels
        oldGate.complete(Unit)
        runCurrent()
        assertSame(newFrame, state.live.pixels)
        val second = workspace().copy(pages = listOf(PdfPageSpec("page-2", "file:///other.pdf", 0)))
        controller.request(second, "page-2")
        assertNull(state.live.pixels)
        advanceUntilIdle()
        assertEquals("page-2", state.live.pageId)
        assertEquals(2, state.live.pixels?.pixels?.single())
    }

    @Test fun originalLabelChangesOnlyWhenRequestedLiveFrameIsVisible() = runTest {
        val gate = CompletableDeferred<Unit>()
        var gateOriginal = true
        var calls = 0
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { value, _, _ ->
                calls++
                if (gateOriginal && value.pages.first().layout.imageFilter == PdfImageFilter.ORIGINAL) {
                    gate.await()
                }
                pixels()
            },
            { _, _ -> error("PDF inesperado") },
            { state = it },
        )
        controller.request(workspace().filtered(), "page-1")
        advanceUntilIdle()
        controller.request(workspace(), "page-1", showingOriginal = true)
        runCurrent()
        assertFalse(state.showingOriginal)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(state.showingOriginal)
        gateOriginal = false
        val completedCalls = calls
        controller.request(workspace(), "page-1", showingOriginal = false)
        assertFalse(state.showingOriginal)
        assertEquals(completedCalls, calls)
        controller.request(workspace(), "page-1", showingOriginal = true)
        controller.pause()
        assertTrue(state.showingOriginal)
        controller.request(workspace().filtered(), "page-1")
        assertTrue(state.showingOriginal)
        advanceUntilIdle()
        assertFalse(state.showingOriginal)
    }

    @Test fun originalPdfLabelWaitsForNewFallbackAndUsesLatestEquivalentRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        var state = DocumentToolPreviewState()
        val controller = DocumentToolPreviewController(this,
            { _, _, _ -> null },
            { value, _ ->
                if (value.pages.first().layout.contentScale != 1f) gate.await()
                PdfPagePreview("file:///scale-${value.pages.first().layout.contentScale}.pdf")
            },
            { state = it },
        )
        controller.request(workspace(), "page-1", debounceMillis = 0)
        advanceUntilIdle()
        val changed = workspace().scaled(1.6f)
        controller.request(changed, "page-1", showingOriginal = true, debounceMillis = 0)
        runCurrent()
        assertFalse(state.showingOriginal)
        controller.request(changed, "page-1", showingOriginal = false, debounceMillis = 0)
        controller.request(changed, "page-1", showingOriginal = true, debounceMillis = 0)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(state.showingOriginal)
        assertEquals("file:///scale-1.6.pdf", state.preview?.uri)
    }

    private fun workspace() = PdfWorkspace(
        documentId = 1,
        workspaceId = "workspace",
        directoryUri = "file:///workspace",
        pages = listOf(PdfPageSpec("page-1", "file:///source.pdf", 0)),
    )

    private fun pixels(value: Int = 1) = PdfInteractivePixels(1, 1, intArrayOf(value), 100f, 150f)

    private fun PdfWorkspace.scaled(scale: Float) = copy(
        pages = pages.map { it.copy(layout = it.layout.copy(contentScale = scale)) },
    )

    private fun PdfWorkspace.filtered() = copy(
        pages = pages.map { it.copy(layout = it.layout.copy(imageFilter = PdfImageFilter.ENHANCE)) },
    )
}
