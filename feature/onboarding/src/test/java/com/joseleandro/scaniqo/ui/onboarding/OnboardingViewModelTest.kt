package com.joseleandro.scaniqo.ui.onboarding

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.repository.OnboardingRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeOnboardingRepository()
    private val store = ViewModelStore()
    private lateinit var model: OnboardingViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        model = OnboardingViewModel(repository)
        store.put("onboarding", model)
    }

    @After fun teardown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun openingTutorialNeverCompletesItWithoutAnExplicitAction() = runTest(dispatcher) {
        advanceUntilIdle()

        assertFalse(model.completed.value)
        assertFalse(model.isSaving.value)
        assertFalse(model.saveError.value)
        assertEquals(0, repository.writes)
    }

    @Test fun completionWaitsForPersistenceAndIgnoresRepeatedTaps() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        model.finish()
        model.finish()
        runCurrent()

        assertTrue(model.isSaving.value)
        assertFalse(model.completed.value)
        assertEquals(1, repository.writes)

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(model.completed.value)
        assertFalse(model.isSaving.value)
        assertFalse(model.saveError.value)
        assertEquals(1, repository.writes)
    }

    @Test fun failedSaveKeepsTutorialOpenAndAllowsRetry() = runTest(dispatcher) {
        repository.failNext = true
        model.finish()
        advanceUntilIdle()

        assertFalse(model.completed.value)
        assertFalse(model.isSaving.value)
        assertTrue(model.saveError.value)

        model.finish()

        assertTrue(model.isSaving.value)
        assertFalse(model.saveError.value)
        advanceUntilIdle()

        assertTrue(model.completed.value)
        assertFalse(model.isSaving.value)
        assertFalse(model.saveError.value)
        assertEquals(2, repository.writes)
    }

    @Test fun completedTutorialIsImmediatelyAvailableWhenViewModelIsRecreated() = runTest(dispatcher) {
        model.finish()
        advanceUntilIdle()

        val recreated = OnboardingViewModel(repository)
        store.put("recreated", recreated)
        assertTrue(recreated.completed.value)

        recreated.finish()
        advanceUntilIdle()
        assertEquals(1, repository.writes)
    }

    @Test fun cancellingPendingCompletionDoesNotMarkTutorialCompletedOrReportAnError() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        model.finish()
        runCurrent()

        store.clear()
        runCurrent()

        assertFalse(model.completed.value)
        assertFalse(model.isSaving.value)
        assertFalse(model.saveError.value)
    }

    private class FakeOnboardingRepository : OnboardingRepository {
        private val state = MutableStateFlow(false)
        override val completed = state.asStateFlow()
        var gate: CompletableDeferred<Unit>? = null
        var failNext = false
        var writes = 0

        override suspend fun complete() {
            writes++
            gate?.await()
            if (failNext) {
                failNext = false
                throw IOException("Simulated storage failure")
            }
            state.value = true
        }
    }
}
