package com.joseleandro.scaniqo.ui.documentCommon.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises scheduling/cancellation under scroll pressure, not native PDF rendering speed. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentPageRenderLoadTest {
    @Test fun tenThousandRequestsDropCancelledPagesAndBoundActiveDecoders() = runTest {
        val gate = DocumentPageRenderGate()
        val release = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        var active = 0
        var peak = 0
        val jobs = List(10_000) { index ->
            launch {
                gate.render {
                    active++
                    peak = maxOf(peak, active)
                    started += index
                    try {
                        release.await()
                    } finally {
                        active--
                    }
                }
            }
        }
        runCurrent()
        assertEquals(listOf(0, 1), started)
        // Pages scrolled off-screen while waiting must never open a native renderer.
        for (index in 2 until 9002) jobs[index].cancel()
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1000, started.size)
        assertTrue(started.none { it in 2 until 9002 })
        assertEquals(2, peak)
        assertEquals(0, active)
        assertTrue(jobs.all { it.isCompleted })
        assertEquals(7, gate.render { 7 })
        println("LOAD_METRIC rendering requests=10000 cancelledBeforeDecode=9000 executed=1000 peakConcurrent=2")
    }
}
