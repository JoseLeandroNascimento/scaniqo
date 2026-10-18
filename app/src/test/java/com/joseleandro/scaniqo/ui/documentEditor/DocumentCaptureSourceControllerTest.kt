package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfCaptureSource
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentCaptureSourceController
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentCaptureSourceState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentCaptureSourceControllerTest {
    private val workspace = PdfWorkspace(1, "session", "directory", listOf(
        PdfPageSpec("first", "source.pdf", 0), PdfPageSpec("second", "source.pdf", 1),
    ))
    private fun source(color: Int = 7) = PdfCaptureSource(
        PdfInteractivePixels(2, 2, IntArray(4) { color }, 600f, 400f),
        PdfCropBounds(.1f, .25f, .9f, .75f),
    )

    @Test fun rawCaptureIsReusedAcrossDraftChangesButNotSourceReplacement() = runTest {
        var loads = 0
        var state = DocumentCaptureSourceState()
        val capture = source()
        val controller = DocumentCaptureSourceController(this, { _, _ -> loads++; capture }) { state = it }
        controller.request(workspace, "first")
        advanceUntilIdle()
        controller.request(workspace.copy(pages = workspace.pages.map {
            it.copy(layout = PdfPageLayout(crop = PdfCropBounds(.2f, .3f, .7f, .6f)))
        }), "first")
        advanceUntilIdle()
        assertEquals(1, loads)
        assertSame(capture, state.source)

        controller.request(workspace.copy(pages = listOf(workspace.pages.first().copy(sourceUri = "new.pdf"))), "first")
        advanceUntilIdle()
        assertEquals(2, loads)
    }

    @Test fun lateResultCannotReplaceTheNewPage() = runTest {
        var state = DocumentCaptureSourceState()
        val gate = CompletableDeferred<Unit>()
        val controller = DocumentCaptureSourceController(this, { _, id ->
            if (id == "first") withContext(NonCancellable) { gate.await() }
            source(if (id == "first") 1 else 2)
        }) { state = it }
        controller.request(workspace, "first")
        runCurrent()
        controller.request(workspace, "second")
        runCurrent()
        assertEquals("second", state.pageId)
        assertEquals(2, state.source!!.pixels.pixels.first())

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("second", state.pageId)
        assertEquals(2, state.source!!.pixels.pixels.first())
    }

    @Test fun clearingProtectedContentRejectsInFlightSourceEvenIfLoaderIgnoresCancellation() = runTest {
        var state = DocumentCaptureSourceState()
        val gate = CompletableDeferred<Unit>()
        val controller = DocumentCaptureSourceController(this, { _, _ ->
            withContext(NonCancellable) { gate.await() }
            source()
        }) { state = it }
        controller.request(workspace, "first")
        runCurrent()
        controller.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(DocumentCaptureSourceState(), state)
    }

    @Test fun unsupportedPageIsNotRetriedOnEveryRecompositionAndCanBeRetriedExplicitly() = runTest {
        var state = DocumentCaptureSourceState()
        var loads = 0
        val controller = DocumentCaptureSourceController(this, { _, _ ->
            loads++
            if (loads == 1) null else source()
        }) { state = it }
        controller.request(workspace, "first")
        advanceUntilIdle()
        assertTrue(state.unsupported)
        repeat(3) { controller.request(workspace, "first") }
        advanceUntilIdle()
        assertEquals(1, loads)

        controller.request(workspace, "first", force = true)
        advanceUntilIdle()
        assertNotNull(state.source)
        assertFalse(state.unsupported)
    }

    @Test fun aFailedLoadCanRecoverWithoutKeepingThePreviousPagesPixels() = runTest {
        var state = DocumentCaptureSourceState()
        var fail = true
        val controller = DocumentCaptureSourceController(this, { _, _ ->
            if (fail) error("Read failure")
            source()
        }) { state = it }
        controller.request(workspace, "first")
        advanceUntilIdle()
        assertTrue(state.error)
        assertNull(state.source)
        fail = false
        controller.request(workspace, "first")
        advanceUntilIdle()
        assertFalse(state.error)
        assertNotNull(state.source)
    }

    @Test fun switchingSessionsWithTheSamePageIdLoadsANewCapture() = runTest {
        var loads = 0
        val controller = DocumentCaptureSourceController(this, { _, _ -> loads++; source() }) {}
        controller.request(workspace, "first")
        advanceUntilIdle()
        controller.request(workspace.copy(workspaceId = "new-session"), "first")
        advanceUntilIdle()
        assertEquals(2, loads)
    }
}
