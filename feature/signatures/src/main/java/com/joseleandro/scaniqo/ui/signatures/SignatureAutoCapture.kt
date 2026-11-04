package com.joseleandro.scaniqo.ui.signatures

import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class SignatureScanHint { ALIGN, HOLD_STILL, MORE_LIGHT, MOVE_INSIDE, MANUAL }

internal data class SignatureScanResult(
    val hint: SignatureScanHint = SignatureScanHint.ALIGN,
    val progress: Float = 0f,
    val shouldCapture: Boolean = false,
)

internal class SignatureInkObservation(
    val hint: SignatureScanHint,
    val ink: BooleanArray,
    val luminance: IntArray,
    val inkCount: Int,
)

internal object SignatureInkDetector {
    const val WIDTH = 192
    const val HEIGHT = 80

    fun detect(luminance: IntArray): SignatureInkObservation {
        require(luminance.size == WIDTH * HEIGHT)
        val histogram = IntArray(256)
        luminance.forEach { histogram[it.coerceIn(0, 255)]++ }
        var count = 0
        val paper = histogram.indices.first { count += histogram[it]; count >= luminance.size * 0.8f }
        val empty = BooleanArray(luminance.size)
        if (paper < 65) return SignatureInkObservation(SignatureScanHint.MORE_LIGHT, empty, luminance, 0)

        val stride = WIDTH + 1
        val integral = IntArray(stride * (HEIGHT + 1))
        for (y in 0 until HEIGHT) {
            var row = 0
            for (x in 0 until WIDTH) {
                row += luminance[y * WIDTH + x]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row
            }
        }
        val candidates = BooleanArray(luminance.size)
        for (y in 1 until HEIGHT - 1) {
            for (x in 1 until WIDTH - 1) {
                val left = (x - 6).coerceAtLeast(0)
                val right = (x + 7).coerceAtMost(WIDTH)
                val top = (y - 6).coerceAtLeast(0)
                val bottom = (y + 7).coerceAtMost(HEIGHT)
                val sum = integral[bottom * stride + right] - integral[top * stride + right] -
                    integral[bottom * stride + left] + integral[top * stride + left]
                val localPaper = sum / ((right - left) * (bottom - top))
                val index = y * WIDTH + x
                candidates[index] = localPaper - luminance[index] >= 18 &&
                    luminance[index] < paper - 26
            }
        }

        val ink = BooleanArray(luminance.size)
        val queue = IntArray(luminance.size)
        for (start in candidates.indices) {
            if (!candidates[start]) continue
            var head = 0
            var tail = 1
            queue[0] = start
            candidates[start] = false
            while (head < tail) {
                val index = queue[head++]
                val x = index % WIDTH
                val y = index / WIDTH
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until WIDTH || ny !in 0 until HEIGHT) continue
                    val next = ny * WIDTH + nx
                    if (candidates[next]) {
                        candidates[next] = false
                        queue[tail++] = next
                    }
                }
            }
            if (tail >= 10) for (i in 0 until tail) ink[queue[i]] = true
        }
        var minX = WIDTH
        var minY = HEIGHT
        var maxX = 0
        var maxY = 0
        var inkCount = 0
        var sharpCount = 0
        for (index in ink.indices) {
            if (!ink[index]) continue
            inkCount++
            val x = index % WIDTH
            val y = index / WIDTH
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
            val value = luminance[index]
            if (maxOf(luminance[index - 1], luminance[index + 1],
                    luminance[index - WIDTH], luminance[index + WIDTH]) - value >= 18) sharpCount++
        }
        val width = maxX - minX + 1
        val height = maxY - minY + 1
        val plausible = inkCount in 70..(luminance.size / 5) &&
            width >= WIDTH / 4 && height >= HEIGHT / 7 &&
            sharpCount >= inkCount / 5 &&
            inkCount.toFloat() / (width * height).coerceAtLeast(1) < 0.48f
        val hint = when {
            !plausible -> SignatureScanHint.ALIGN
            minX <= 4 || minY <= 3 || maxX >= WIDTH - 5 || maxY >= HEIGHT - 4 ->
                SignatureScanHint.MOVE_INSIDE
            else -> SignatureScanHint.HOLD_STILL
        }
        return SignatureInkObservation(hint, ink, luminance, inkCount)
    }
}

internal class SignatureAutoCaptureTracker {
    private var reference: SignatureInkObservation? = null
    private var previousTime = Long.MIN_VALUE
    private var stableSince = 0L
    private var stableFrames = 0
    private var captured = false

    fun reset() {
        reference = null
        previousTime = Long.MIN_VALUE
        stableFrames = 0
        captured = false
    }

    fun update(observation: SignatureInkObservation, timestampMs: Long): SignatureScanResult {
        if (captured) return SignatureScanResult(SignatureScanHint.HOLD_STILL, 1f)
        val anchor = reference
        val stable = observation.hint == SignatureScanHint.HOLD_STILL &&
            anchor?.hint == SignatureScanHint.HOLD_STILL &&
            timestampMs > previousTime && timestampMs - previousTime <= MAX_FRAME_GAP_MS &&
            remainsStill(anchor, observation)
        if (!stable) {
            reference = observation
            stableSince = timestampMs
            stableFrames = 0
        }
        previousTime = timestampMs
        if (observation.hint != SignatureScanHint.HOLD_STILL) return SignatureScanResult(observation.hint)
        stableFrames++
        val progress = ((timestampMs - stableSince) / HOLD_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        val capture = progress >= 1f && stableFrames >= MIN_STABLE_FRAMES
        if (capture) captured = true
        return SignatureScanResult(SignatureScanHint.HOLD_STILL, progress, capture)
    }

    private fun remainsStill(anchor: SignatureInkObservation, current: SignatureInkObservation): Boolean {
        if (anchor.inkCount == 0 || current.inkCount == 0) return false
        val width = SignatureInkDetector.WIDTH
        val height = SignatureInkDetector.HEIGHT
        var anchorX = 0L
        var anchorY = 0L
        var currentX = 0L
        var currentY = 0L
        for (i in current.ink.indices) {
            if (anchor.ink[i]) {
                anchorX += i % width
                anchorY += i / width
            }
            if (current.ink[i]) {
                currentX += i % width
                currentY += i / width
            }
        }
        val offsetX = currentX.toFloat() / current.inkCount - anchorX.toFloat() / anchor.inkCount
        val offsetY = currentY.toFloat() / current.inkCount - anchorY.toFloat() / anchor.inkCount
        // Allow natural hand movement within a fixed window; larger repositioning starts a new hold.
        if (abs(offsetX) > MAX_JITTER_PIXELS || abs(offsetY) > MAX_JITTER_PIXELS) return false
        val dx = offsetX.roundToInt()
        val dy = offsetY.roundToInt()
        var matchedAnchorInk = 0
        var matchedCurrentInk = 0
        var lightDifference = 0L
        var comparedPixels = 0
        for (y in maxOf(0, -dy) until minOf(height, height - dy)) {
            for (x in maxOf(0, -dx) until minOf(width, width - dx)) {
                val before = y * width + x
                val after = (y + dy) * width + x + dx
                // Sampling, slight rotation and focus changes move stroke edges by a few pixels.
                if (anchor.ink[before] && hasInkNear(current.ink, x + dx, y + dy)) matchedAnchorInk++
                if (current.ink[after] && hasInkNear(anchor.ink, x, y)) matchedCurrentInk++
                lightDifference += abs(anchor.luminance[before] - current.luminance[after])
                comparedPixels++
            }
        }
        return matchedAnchorInk.toFloat() / anchor.inkCount >= MIN_STROKE_MATCH &&
            matchedCurrentInk.toFloat() / current.inkCount >= MIN_STROKE_MATCH &&
            lightDifference.toFloat() / comparedPixels < MAX_LIGHT_DIFFERENCE
    }

    private fun hasInkNear(ink: BooleanArray, x: Int, y: Int): Boolean {
        val width = SignatureInkDetector.WIDTH
        val height = SignatureInkDetector.HEIGHT
        for (ny in maxOf(0, y - STROKE_MATCH_RADIUS)..minOf(height - 1, y + STROKE_MATCH_RADIUS)) {
            for (nx in maxOf(0, x - STROKE_MATCH_RADIUS)..minOf(width - 1, x + STROKE_MATCH_RADIUS)) {
                if (ink[ny * width + nx]) return true
            }
        }
        return false
    }

    private companion object {
        const val HOLD_DURATION_MS = 200L
        const val MAX_FRAME_GAP_MS = 450L
        const val MIN_STABLE_FRAMES = 3
        const val MAX_JITTER_PIXELS = 6f
        const val STROKE_MATCH_RADIUS = 2
        const val MIN_STROKE_MATCH = 0.8f
        const val MAX_LIGHT_DIFFERENCE = 12f
    }
}
