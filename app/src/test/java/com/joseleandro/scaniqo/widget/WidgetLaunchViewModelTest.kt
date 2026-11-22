package com.joseleandro.scaniqo.widget

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetLaunchViewModelTest {
    @Test
    fun pendingRequestSurvivesRecreationWhileOnboardingOrAnOperationBlocksDispatch() {
        val savedState = SavedStateHandle()
        val viewModel = WidgetLaunchViewModel(savedState)
        val destination = WidgetDestination(WidgetAction.OPEN_DOCUMENT, 7)
        viewModel.enqueue(destination)
        val pending = checkNotNull(viewModel.pending.value)

        val restored = WidgetLaunchViewModel(copySavedState(savedState))

        assertEquals(pending, restored.pending.value)
        assertEquals(destination, restored.consume(pending))
        assertNull(restored.pending.value)
    }

    @Test
    fun consumedScannerAndPickerRequestsAreNotReplayedAfterRecreation() {
        listOf(WidgetAction.SCAN, WidgetAction.IMPORT).forEach { action ->
            val savedState = SavedStateHandle()
            val viewModel = WidgetLaunchViewModel(savedState)
            viewModel.enqueue(WidgetDestination(action))
            val pending = checkNotNull(viewModel.pending.value)

            assertEquals(WidgetDestination(action), viewModel.consume(pending))
            assertNull(viewModel.consume(pending))
            assertNull(WidgetLaunchViewModel(copySavedState(savedState)).pending.value)
        }
    }

    @Test
    fun latestTapReplacesDeferredRequestAndStaleCallbackCannotConsumeIt() {
        val viewModel = WidgetLaunchViewModel(SavedStateHandle())
        viewModel.enqueue(WidgetDestination(WidgetAction.SCAN))
        val previous = checkNotNull(viewModel.pending.value)

        viewModel.enqueue(WidgetDestination(WidgetAction.SEARCH))
        val latest = checkNotNull(viewModel.pending.value)

        assertNull(viewModel.consume(previous))
        assertEquals(latest, viewModel.pending.value)
        assertEquals(WidgetDestination(WidgetAction.SEARCH), viewModel.consume(latest))
    }

    @Test
    fun tappingTheSameActionAgainProducesANewRequest() {
        val viewModel = WidgetLaunchViewModel(SavedStateHandle())
        val destination = WidgetDestination(WidgetAction.IMPORT)
        viewModel.enqueue(destination)
        val previous = checkNotNull(viewModel.pending.value)
        viewModel.consume(previous)

        viewModel.enqueue(destination)
        val next = checkNotNull(viewModel.pending.value)

        assertNotEquals(previous.id, next.id)
        assertNull(viewModel.consume(previous))
        assertEquals(destination, viewModel.consume(next))
    }

    private fun copySavedState(savedState: SavedStateHandle) =
        SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) })
}
