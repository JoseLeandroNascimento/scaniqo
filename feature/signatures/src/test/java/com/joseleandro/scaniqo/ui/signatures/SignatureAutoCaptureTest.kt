package com.joseleandro.scaniqo.ui.signatures

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SignatureAutoCaptureTest {
    @Test fun blankPaperAndSmoothShadowsNeverLookLikeInk() {
        val blank = IntArray(W * H) { 235 }
        val shadow = IntArray(W * H) { index -> 100 + (index % W) * 135 / W }
        val edge = IntArray(W * H) { if (it % W < W / 2) 110 else 235 }
        for (frame in listOf(blank, shadow, edge)) {
            assertEquals(SignatureScanHint.ALIGN, SignatureInkDetector.detect(frame).hint)
        }
    }

    @Test fun isolatedSensorNoiseDoesNotBecomeASignature() {
        val frame = IntArray(W * H) { 235 }
        for (y in 8 until H - 8 step 6) for (x in 8 until W - 8 step 6) frame[y * W + x] = 30
        assertEquals(SignatureScanHint.ALIGN, SignatureInkDetector.detect(frame).hint)
    }

    @Test fun horizontalRuleDoesNotTriggerCapture() {
        val frame = IntArray(W * H) { 235 }
        for (x in 20 until W - 20) for (y in 39..41) frame[y * W + x] = 35
        assertEquals(SignatureScanHint.ALIGN, SignatureInkDetector.detect(frame).hint)
    }

    @Test fun clearCurvedStrokesAreDetectedOnUnevenlyLitPaper() {
        val frame = signature(shadow = true)
        val observation = SignatureInkDetector.detect(frame)
        assertEquals(SignatureScanHint.HOLD_STILL, observation.hint)
        assertTrue(observation.inkCount > 70)
    }

    @Test fun defocusedStrokesDoNotFinishDetection() {
        val sharp = signature()
        val blurred = IntArray(W * H)
        for (y in 0 until H) for (x in 0 until W) {
            var total = 0
            var count = 0
            for (dy in -5..5) for (dx in -5..5) {
                val sx = (x + dx).coerceIn(0, W - 1)
                val sy = (y + dy).coerceIn(0, H - 1)
                total += sharp[sy * W + sx]
                count++
            }
            blurred[y * W + x] = total / count
        }
        assertEquals(SignatureScanHint.ALIGN, SignatureInkDetector.detect(blurred).hint)
    }

    @Test fun veryDarkPaperAsksForLight() {
        assertEquals(
            SignatureScanHint.MORE_LIGHT,
            SignatureInkDetector.detect(IntArray(W * H) { 35 }).hint,
        )
    }

    @Test fun croppedInkMustBeMovedInsideBeforeAutomaticCapture() {
        val frame = signature(shiftX = -24)
        assertEquals(SignatureScanHint.MOVE_INSIDE, SignatureInkDetector.detect(frame).hint)
    }

    @Test fun stableSignatureCapturesExactlyOnceAfterTwoHundredMilliseconds() {
        val tracker = SignatureAutoCaptureTracker()
        val observation = SignatureInkDetector.detect(signature())
        val results = (0..20).map { tracker.update(observation, it * 100L) }
        assertFalse(results.take(2).any { it.shouldCapture })
        assertTrue(results[2].shouldCapture)
        assertEquals(1, results.count { it.shouldCapture })
        assertEquals(1f, results.last().progress, 0f)
    }

    @Test fun naturalHandTremorsDoNotRestartTheCountdown() {
        val tracker = SignatureAutoCaptureTracker()
        val offsets = listOf(0 to 0, 4 to -3, -4 to 3, 5 to -4, 0 to 0)
        val results = offsets.mapIndexed { index, (x, y) ->
            tracker.update(SignatureInkDetector.detect(signature(shiftX = x, shiftY = y)), index * 100L)
        }
        for (i in 0..2) assertEquals(i / 2f, results[i].progress, 0.001f)
        assertTrue(results[2].shouldCapture)
        assertEquals(1, results.count { it.shouldCapture })
    }

    @Test fun smallRotationAndScaleChangesDoNotRestartTheCountdown() {
        val tracker = SignatureAutoCaptureTracker()
        val frames = listOf(signature(), transformedSignature(1.5, 1.02), transformedSignature(-1.5, 0.98))
        val results = frames.mapIndexed { i, frame ->
            val observation = SignatureInkDetector.detect(frame)
            assertEquals(SignatureScanHint.HOLD_STILL, observation.hint)
            tracker.update(observation, i * 100L)
        }
        assertFalse(results.take(2).any { it.shouldCapture })
        assertTrue(results.last().shouldCapture)
    }

    @Test fun smallExposureCorrectionsDoNotRestartTheCountdown() {
        val tracker = SignatureAutoCaptureTracker()
        val original = signature()
        val results = (0..2).map { i ->
            val frame = IntArray(original.size) { (original[it] + i * 5).coerceAtMost(255) }
            tracker.update(SignatureInkDetector.detect(frame), i * 100L)
        }
        assertTrue(results.last().shouldCapture)
    }

    @Test fun gentleMovementInsideTheGuideCanCapture() {
        val tracker = SignatureAutoCaptureTracker()
        val results = (0..2).map { i ->
            tracker.update(SignatureInkDetector.detect(signature(shiftX = i, shiftY = i)), i * 100L)
        }
        assertFalse(results.take(2).any { it.shouldCapture })
        assertTrue(results.last().shouldCapture)
    }

    @Test fun largerContinuousMovementCannotAccumulateJitterTolerance() {
        for (horizontal in listOf(true, false)) {
            val tracker = SignatureAutoCaptureTracker()
            val results = (0..4).map { i ->
                val frame = if (horizontal) signature(shiftX = i * 4) else signature(shiftY = i * 4)
                tracker.update(SignatureInkDetector.detect(frame), i * 100L)
            }
            assertTrue(results.all { it.hint == SignatureScanHint.HOLD_STILL })
            assertFalse(results.any { it.shouldCapture })
        }
    }

    @Test fun elapsedTimeAloneCannotReplaceThreeValidFrames() {
        val tracker = SignatureAutoCaptureTracker()
        val observation = SignatureInkDetector.detect(signature())
        for (time in listOf(0L, 300L)) {
            assertFalse(tracker.update(observation, time).shouldCapture)
        }
        assertTrue(tracker.update(observation, 600L).shouldCapture)
    }

    @Test fun movementWithSameInkCoverageRestartsTheHoldPeriod() {
        val tracker = SignatureAutoCaptureTracker()
        val original = SignatureInkDetector.detect(signature())
        val moved = SignatureInkDetector.detect(signature(shiftX = 8))
        for (i in 0..1) tracker.update(original, i * 100L)
        val result = tracker.update(moved, 200)
        assertFalse(result.shouldCapture)
        assertEquals(0f, result.progress, 0f)
        assertFalse(tracker.update(moved, 300).shouldCapture)
    }

    @Test fun differentStrokesNearTheSamePositionRestartTheHoldPeriod() {
        val tracker = SignatureAutoCaptureTracker()
        val frame = signature()
        val original = SignatureInkDetector.detect(frame)
        val mirrored = SignatureInkDetector.detect(IntArray(frame.size) { i ->
            frame[(i / W) * W + W - 1 - i % W]
        })
        assertEquals(SignatureScanHint.HOLD_STILL, mirrored.hint)
        for (i in 0..1) tracker.update(original, i * 100L)
        val result = tracker.update(mirrored, 200)
        assertFalse(result.shouldCapture)
        assertEquals(0f, result.progress, 0f)
    }

    @Test fun blankFrameBetweenCandidatesRestartsTheHoldPeriod() {
        val tracker = SignatureAutoCaptureTracker()
        val observation = SignatureInkDetector.detect(signature())
        for (i in 0..1) tracker.update(observation, i * 100L)
        tracker.update(SignatureInkDetector.detect(IntArray(W * H) { 235 }), 200)
        assertEquals(0f, tracker.update(observation, 300).progress, 0f)
    }

    @Test fun largeLightChangeWaitsForExposureToSettle() {
        val tracker = SignatureAutoCaptureTracker()
        val frame = signature()
        val observation = SignatureInkDetector.detect(frame)
        for (i in 0..1) tracker.update(observation, i * 100L)
        val bright = SignatureInkDetector.detect(IntArray(frame.size) { (frame[it] + 20).coerceAtMost(255) })
        assertEquals(0f, tracker.update(bright, 200).progress, 0f)
    }

    @Test fun longFrameGapsAndOutOfOrderTimestampsCannotFinishAnOldCountdown() {
        for (time in listOf(3000L, 0L, 100L)) {
            val tracker = SignatureAutoCaptureTracker()
            val observation = SignatureInkDetector.detect(signature())
            for (i in 0..1) tracker.update(observation, i * 100L)
            val result = tracker.update(observation, time)
            assertFalse(result.shouldCapture)
            assertEquals(0f, result.progress, 0f)
        }
    }

    @Test fun lifecycleResetRequiresAnEntireNewHoldPeriod() {
        val tracker = SignatureAutoCaptureTracker()
        val observation = SignatureInkDetector.detect(signature())
        for (i in 0..1) tracker.update(observation, i * 100L)
        tracker.reset()
        assertEquals(0f, tracker.update(observation, 200).progress, 0f)
        assertFalse(tracker.update(observation, 300).shouldCapture)
        assertTrue(tracker.update(observation, 400).shouldCapture)
    }

    private fun transformedSignature(rotationDegrees: Double, scale: Double): IntArray {
        val source = signature()
        val angle = Math.toRadians(rotationDegrees)
        val cosine = cos(angle)
        val sine = sin(angle)
        return IntArray(W * H) { i ->
            val x = (i % W - W / 2.0) / scale
            val y = (i / W - H / 2.0) / scale
            val sourceX = (x * cosine + y * sine + W / 2.0).roundToInt()
            val sourceY = (-x * sine + y * cosine + H / 2.0).roundToInt()
            if (sourceX in 0 until W && sourceY in 0 until H) source[sourceY * W + sourceX] else 230
        }
    }

    private fun signature(shiftX: Int = 0, shiftY: Int = 0, shadow: Boolean = false): IntArray {
        val frame = IntArray(W * H) { if (shadow) 135 + (it % W) * 100 / W else 230 }
        for (x in 25..165) {
            val centerY = (40 + 12 * sin(x * 0.19) + 5 * sin(x * 0.07)).toInt() + shiftY
            for (dy in -1..1) {
                val px = x + shiftX
                val py = centerY + dy
                if (px in 0 until W && py in 0 until H) frame[py * W + px] = 35
            }
        }
        return frame
    }

    private companion object {
        const val W = SignatureInkDetector.WIDTH
        const val H = SignatureInkDetector.HEIGHT
    }
}
