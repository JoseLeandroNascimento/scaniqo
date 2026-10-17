package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
class DocumentLivePreviewControllerTest {
    @Test fun preparesFastFrameImmediatelyAndRefinesOnlyAfterTheGestureSettles() = runTest {
        val harness = Harness(this)
        harness.controller.request(workspace(), "page-1")
        assertTrue(harness.state.isRendering)
        runCurrent()

        assertEquals(listOf(480), harness.calls.map { it.maxSide })
        assertSame(harness.calls.first().pixels, harness.state.pixels)
        assertFalse(harness.state.isRendering)
        advanceTimeBy(249)
        runCurrent()
        assertEquals(1, harness.calls.size)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(480, 1100), harness.calls.map { it.maxSide })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
        assertFalse(harness.state.isRendering)
        assertFalse(harness.state.error)
    }

    @Test fun slowFastFrameDoesNotAddAnotherSettlingDelay() = runTest {
        val harness = Harness(this)
        val gate = CompletableDeferred<Unit>()
        harness.render = { call ->
            if (call.maxSide == 480) gate.await()
            call.pixels
        }
        harness.controller.request(workspace(), "page-1")
        runCurrent()
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf(480), harness.calls.map { it.maxSide })

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(480, 1100), harness.calls.map { it.maxSide })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
        assertFalse(harness.state.isRendering)
    }

    @Test fun keepsCurrentSampleAndCoalescesPendingChangesToTheLastDraft() = runTest {
        val harness = Harness(this)
        val gate = CompletableDeferred<Unit>()
        harness.render = { call ->
            if (call.index == 1) gate.await()
            call.pixels
        }
        val original = workspace()
        harness.controller.request(original, "page-1")
        runCurrent()
        advanceTimeBy(40)
        harness.controller.request(original.withLayout(PdfPageLayout(straightenDegrees = 3f)), "page-1")
        advanceTimeBy(40)
        harness.controller.request(original.withLayout(PdfPageLayout(straightenDegrees = 7f)), "page-1")
        runCurrent()
        assertEquals(1, harness.calls.size)

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(0f, 7f), harness.calls.map { it.page.layout.straightenDegrees })
        assertTrue(harness.states.any { it.pixels === harness.calls.first().pixels && it.isRendering })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
        advanceTimeBy(249)
        runCurrent()
        assertEquals(2, harness.calls.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(480, 480, 1100), harness.calls.map { it.maxSide })
        assertEquals(7f, harness.calls.last().page.layout.straightenDegrees, 0f)
    }

    @Test fun sheetGeometrySignaturesAndNumberingReuseTheSameRaster() = runTest {
        val harness = Harness(this)
        val original = workspace()
        harness.controller.request(original, "page-1")
        runCurrent()
        val changed = original.withLayout(PdfPageLayout(
            alignment = PageAlignment.BOTTOM,
            scaleMode = PageScaleMode.FILL,
            rotationQuarterTurns = 3,
            contentScale = 1.7f,
        )).let { value ->
            value.copy(
                pages = value.pages.map { page ->
                    page.copy(signatures = listOf(PdfSignaturePlacement("signature", "file:///signature.png")))
                }.reversed(),
                showPageNumbers = true,
                exportQuality = PdfExportQuality.SMALL,
                ocrEnabled = true,
            )
        }
        harness.controller.request(changed, "page-1")
        advanceUntilIdle()
        val refined = harness.state.pixels

        repeat(3) { harness.controller.request(changed, "page-1") }
        advanceUntilIdle()

        assertEquals(listOf(480, 1100), harness.calls.map { it.maxSide })
        assertSame(refined, harness.state.pixels)
    }

    @Test fun sourceAndPixelProcessingChangesInvalidateTheRaster() = runTest {
        val harness = Harness(this)
        val original = workspace()
        val changedPages = listOf(
            original.pages.first().copy(sourceUri = "file:///replacement.pdf"),
            original.pages.first().copy(sourcePageIndex = 5),
            original.pages.first().copy(layout = PdfPageLayout(imageFilter = PdfImageFilter.SHARPEN)),
            original.pages.first().copy(layout = PdfPageLayout(filterIntensity = .4f)),
            original.pages.first().copy(layout = PdfPageLayout(rasterizeText = true)),
            original.pages.first().copy(layout = PdfPageLayout(straightenDegrees = 4f)),
            original.pages.first().copy(layout = PdfPageLayout(perspective = PdfPerspective())),
            original.pages.first().copy(layout = PdfPageLayout(curvature = .2f)),
        )
        harness.controller.request(original, "page-1")
        advanceUntilIdle()

        changedPages.forEach { page ->
            val count = harness.calls.size
            harness.controller.request(original.copy(pages = listOf(page)), "page-1")
            advanceUntilIdle()
            assertEquals(count + 2, harness.calls.size)
            assertEquals(page, harness.calls.last().page)
        }
    }

    @Test fun identicalRequestsAreDeduplicatedButForceRegeneratesCompletedPixels() = runTest {
        val harness = Harness(this)
        val original = workspace()
        harness.controller.request(original, "page-1")
        repeat(3) { harness.controller.request(original, "page-1") }
        runCurrent()
        repeat(3) { harness.controller.request(original, "page-1") }
        advanceUntilIdle()
        val previous = harness.state.pixels
        repeat(3) { harness.controller.request(original, "page-1") }
        advanceUntilIdle()
        assertEquals(2, harness.calls.size)

        harness.controller.request(original, "page-1", force = true)
        assertSame(previous, harness.state.pixels)
        advanceUntilIdle()

        assertEquals(listOf(480, 1100, 480, 1100), harness.calls.map { it.maxSide })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
    }

    @Test fun forcingAnInFlightKeyRequiresAFreshSample() = runTest {
        val harness = Harness(this)
        val gate = CompletableDeferred<Unit>()
        harness.render = { call ->
            if (call.index == 1) gate.await()
            call.pixels
        }
        val original = workspace()
        harness.controller.request(original, "page-1")
        runCurrent()
        harness.controller.request(original, "page-1", force = true)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(480, 480, 1100), harness.calls.map { it.maxSide })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
    }

    @Test fun newDraftCancelsCooperativeRefinement() = runTest {
        val harness = Harness(this)
        val gate = CompletableDeferred<Unit>()
        var refinementFinished = false
        harness.render = { call ->
            if (call.index == 2) {
                try {
                    gate.await()
                } finally {
                    refinementFinished = true
                }
            }
            call.pixels
        }
        val original = workspace()
        harness.controller.request(original, "page-1")
        advanceTimeBy(250)
        runCurrent()
        assertTrue(harness.state.isRendering)

        harness.controller.request(original.withLayout(PdfPageLayout(curvature = .3f)), "page-1")
        runCurrent()

        assertTrue(refinementFinished)
        assertEquals(listOf(480, 1100, 480), harness.calls.map { it.maxSide })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
        advanceUntilIdle()
        assertEquals(1100, harness.calls.last().maxSide)
        assertEquals(.3f, harness.calls.last().page.layout.curvature, 0f)
    }

    @Test fun uncancellableOldRefinementCannotOverwriteNewDraftOrItsRefinement() = runTest {
        val harness = Harness(this)
        val staleGate = CompletableDeferred<Unit>()
        harness.render = { call ->
            if (call.index == 2) withContext(NonCancellable) { staleGate.await() }
            call.pixels
        }
        val original = workspace()
        harness.controller.request(original, "page-1")
        advanceTimeBy(250)
        runCurrent()
        val stale = harness.calls.last().pixels

        harness.controller.request(original.withLayout(PdfPageLayout(straightenDegrees = 8f)), "page-1")
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        val latest = harness.calls.last().pixels
        assertEquals(listOf(480, 1100, 480, 1100), harness.calls.map { it.maxSide })
        assertSame(latest, harness.state.pixels)

        staleGate.complete(Unit)
        advanceUntilIdle()

        assertSame(latest, harness.state.pixels)
        assertFalse(harness.states.any { it.pixels === stale })
        assertFalse(harness.state.isRendering)
    }

    @Test fun switchingWorkspaceAndPageDropsPreviousPixelsAndLateResults() = runTest {
        val harness = Harness(this)
        val staleGate = CompletableDeferred<Unit>()
        harness.render = { call ->
            if (call.index == 1) withContext(NonCancellable) { staleGate.await() }
            call.pixels
        }
        val original = workspace()
        harness.controller.request(original, "page-1")
        runCurrent()
        val stale = harness.calls.first().pixels

        val replacement = original.copy(workspaceId = "replacement")
        harness.controller.request(replacement, "page-1")
        assertNull(harness.state.pixels)
        runCurrent()
        assertSame(harness.calls.last().pixels, harness.state.pixels)
        harness.controller.request(replacement, "page-2")
        assertNull(harness.state.pixels)
        assertEquals("page-2", harness.state.pageId)
        runCurrent()
        staleGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("workspace", "replacement", "replacement", "replacement"),
            harness.calls.map { it.workspace.workspaceId })
        assertEquals(listOf("page-1", "page-1", "page-2", "page-2"),
            harness.calls.map { it.pageId })
        assertFalse(harness.states.any { it.pixels === stale })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
    }

    @Test fun pauseRetainsFrameCancelsRefinementAndAllowsSameKeyToResume() = runTest {
        val harness = Harness(this)
        val original = workspace()
        harness.controller.request(original, "page-1")
        runCurrent()
        val fast = harness.state.pixels

        harness.controller.pause()
        advanceUntilIdle()

        assertEquals(1, harness.calls.size)
        assertSame(fast, harness.state.pixels)
        assertFalse(harness.state.isRendering)
        harness.controller.request(original, "page-1")
        advanceUntilIdle()
        assertEquals(listOf(480, 480, 1100), harness.calls.map { it.maxSide })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
    }

    @Test fun clearReleasesStateAndRejectsUncancellableResult() = runTest {
        val harness = Harness(this)
        val gate = CompletableDeferred<Unit>()
        harness.render = { call ->
            withContext(NonCancellable) { gate.await() }
            call.pixels
        }
        val original = workspace()
        harness.controller.request(original, "page-1")
        runCurrent()
        harness.controller.clear()
        assertEquals(DocumentLivePreviewState(), harness.state)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(DocumentLivePreviewState(), harness.state)
        assertEquals(1, harness.calls.size)
        harness.controller.request(original, "page-1")
        advanceUntilIdle()
        assertEquals(listOf(480, 480, 1100), harness.calls.map { it.maxSide })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
    }

    @Test fun unsupportedFastRenderDoesNotRefineOrRepeatUntilForced() = runTest {
        val harness = Harness(this)
        harness.render = { null }
        val original = workspace()
        harness.controller.request(original, "page-1")
        advanceUntilIdle()
        assertTrue(harness.state.unsupported)
        assertFalse(harness.state.error)
        assertFalse(harness.state.isRendering)
        assertNull(harness.state.pixels)
        harness.controller.request(original, "page-1")
        advanceUntilIdle()
        assertEquals(1, harness.calls.size)

        harness.render = { it.pixels }
        harness.controller.request(original, "page-1", force = true)
        advanceUntilIdle()
        assertEquals(listOf(480, 480, 1100), harness.calls.map { it.maxSide })
        assertFalse(harness.state.unsupported)
        assertSame(harness.calls.last().pixels, harness.state.pixels)
    }

    @Test fun failedFastRenderRetainsPreviousFrameAndSameRequestCanRetry() = runTest {
        val harness = Harness(this)
        val original = workspace()
        harness.controller.request(original, "page-1")
        advanceUntilIdle()
        val previous = harness.state.pixels
        harness.render = { error("Render failed") }
        val draft = original.withLayout(PdfPageLayout(imageFilter = PdfImageFilter.DENOISE))

        harness.controller.request(draft, "page-1")
        advanceUntilIdle()

        assertEquals(3, harness.calls.size)
        assertSame(previous, harness.state.pixels)
        assertTrue(harness.state.error)
        assertFalse(harness.state.unsupported)
        assertFalse(harness.state.isRendering)
        harness.render = { it.pixels }
        harness.controller.request(draft, "page-1")
        advanceUntilIdle()
        assertEquals(5, harness.calls.size)
        assertFalse(harness.state.error)
        assertSame(harness.calls.last().pixels, harness.state.pixels)
    }

    @Test fun staleSampleFailureDoesNotBlockTheLatestDraft() = runTest {
        val harness = Harness(this)
        val gate = CompletableDeferred<Unit>()
        harness.render = { call ->
            if (call.index == 1) {
                gate.await()
                error("Old sample failed")
            }
            call.pixels
        }
        val original = workspace()
        harness.controller.request(original, "page-1")
        runCurrent()
        harness.controller.request(original.withLayout(PdfPageLayout(curvature = .4f)), "page-1")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(480, 480, 1100), harness.calls.map { it.maxSide })
        assertFalse(harness.states.any { it.error })
        assertSame(harness.calls.last().pixels, harness.state.pixels)
    }

    @Test fun failedRefinementRetainsFastFrameAndForcedRetryRecovers() = runTest {
        val harness = Harness(this)
        harness.render = { call ->
            if (call.maxSide == 1100) error("Refinement failed")
            call.pixels
        }
        val original = workspace()
        harness.controller.request(original, "page-1")
        advanceUntilIdle()

        assertEquals(listOf(480, 1100), harness.calls.map { it.maxSide })
        assertSame(harness.calls.first().pixels, harness.state.pixels)
        assertTrue(harness.state.error)
        assertFalse(harness.state.isRendering)
        harness.render = { it.pixels }
        harness.controller.request(original, "page-1", force = true)
        advanceUntilIdle()

        assertEquals(4, harness.calls.size)
        assertFalse(harness.state.error)
        assertSame(harness.calls.last().pixels, harness.state.pixels)
    }

    @Test fun unsupportedRefinementSignalsFallbackAndClearsTheFastFrame() = runTest {
        val harness = Harness(this)
        harness.render = { call -> call.pixels.takeIf { call.maxSide == 480 } }
        harness.controller.request(workspace(), "page-1")
        advanceUntilIdle()

        assertEquals(listOf(480, 1100), harness.calls.map { it.maxSide })
        assertTrue(harness.state.unsupported)
        assertNull(harness.state.pixels)
        assertFalse(harness.state.error)
        assertFalse(harness.state.isRendering)
    }

    @Test fun unknownPageDoesNotStartWorkOrReplaceVisibleState() = runTest {
        val harness = Harness(this)
        val original = workspace()
        harness.controller.request(original, "missing")
        advanceUntilIdle()
        assertTrue(harness.calls.isEmpty())
        assertEquals(DocumentLivePreviewState(), harness.state)
        harness.controller.request(original, "page-1")
        advanceUntilIdle()
        val ready = harness.state

        harness.controller.request(original, "missing")
        advanceUntilIdle()

        assertEquals(2, harness.calls.size)
        assertEquals(ready, harness.state)
    }

    private class Harness(scope: CoroutineScope) {
        val calls = mutableListOf<RenderCall>()
        val states = mutableListOf<DocumentLivePreviewState>()
        var state = DocumentLivePreviewState()
        var render: suspend (RenderCall) -> PdfInteractivePixels? = { it.pixels }
        val controller = DocumentLivePreviewController(
            scope,
            render = { workspace, pageId, maxSide ->
                val index = calls.size + 1
                val call = RenderCall(
                    workspace, pageId, maxSide, index,
                    PdfInteractivePixels(1, 1, intArrayOf(index), 612f, 792f),
                )
                calls += call
                render(call)
            },
            onChanged = {
                state = it
                states += it
            },
        )
    }

    private data class RenderCall(
        val workspace: PdfWorkspace,
        val pageId: String,
        val maxSide: Int,
        val index: Int,
        val pixels: PdfInteractivePixels,
    ) {
        val page: PdfPageSpec get() = workspace.pages.first { it.id == pageId }
    }

    private fun workspace() = PdfWorkspace(
        documentId = 1,
        workspaceId = "workspace",
        directoryUri = "file:///workspace",
        pages = listOf(
            PdfPageSpec("page-1", "file:///source.pdf", 0),
            PdfPageSpec("page-2", "file:///source.pdf", 1),
        ),
    )

    private fun PdfWorkspace.withLayout(layout: PdfPageLayout): PdfWorkspace =
        copy(pages = pages.map { if (it.id == "page-1") it.copy(layout = layout) else it })
}
