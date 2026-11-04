package com.joseleandro.scaniqo.data.signatures

import java.util.Random
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structured camera-like fixtures with known ink coverage, not a visual-only comparison. */
class SignaturePhotographMattingTest {
    @Test fun curvedBlueHandwritingCrossingAHardShadowKeepsInkAndRejectsPaper() {
        assertMatte(photograph(hardShadow = true, noise = true, blemishes = true))
    }

    @Test fun curvedBlackHandwritingSurvivesGradientAndCameraNoise() {
        assertMatte(photograph(ink = intArrayOf(22, 25, 29), hardShadow = false, noise = true))
    }

    @Test fun thinBlueInkSurvivesDeepShadowAndSteepIlluminationGradient() {
        assertMatte(
            photograph(
                hardShadow = true,
                shadowTransmission = .24,
                gradientDepth = .42,
                strokeScale = .7,
                noise = true,
                blemishes = true,
            ),
        )
    }

    @Test fun thinBlackInkSurvivesDeepShadowAndSteepIlluminationGradient() {
        assertMatte(
            photograph(
                ink = intArrayOf(22, 25, 29),
                hardShadow = true,
                shadowTransmission = .24,
                gradientDepth = .42,
                strokeScale = .7,
                noise = true,
            ),
        )
    }

    @Test fun deepShadowAndSteepGradientWithoutInkAreRejected() {
        assertQualityFailure(
            photograph(
                hardShadow = true,
                shadowTransmission = .24,
                gradientDepth = .42,
                noise = true,
                blemishes = true,
                includeInk = false,
            ),
        )
    }

    @Test fun broadSoftShadowWithoutInkIsRejected() {
        assertQualityFailure(photograph(gradientDepth = .6, noise = true, includeInk = false))
    }

    @Test fun hardShadowWithoutHandwritingCannotBeSavedAsASignature() {
        val photo = photograph(hardShadow = true, noise = true, includeInk = false)
        assertQualityFailure(photo)
    }

    @Test fun dustAndPaperBlemishesAloneCannotBecomeOpaqueBlots() {
        val photo = photograph(noise = true, blemishes = true, includeInk = false)
        assertQualityFailure(photo)
    }

    @Test fun severelyDefocusedHandwritingRequestsAnotherPhoto() {
        val photo = photograph(noise = false)
        val blurred = blur(photo.pixels, photo.width, photo.height, radius = 6)
        assertQualityFailure(photo.copy(pixels = blurred))
    }

    @Test fun largePhotoCompletesWithAlphaAndBoundedProcessingTime() {
        for (width in listOf(1280, 2560)) {
            val photo = photograph(width = width, hardShadow = true, noise = true, blemishes = true)
            val start = System.nanoTime()
            assertMatte(photo)
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            println("Signature matting " + width + "x" + width / 2 + ": " + seconds + " s")
            assertTrue("Processing a bounded photo took " + seconds + " s", seconds < 30.0)
        }
    }

    private fun assertMatte(photo: Photo) {
        val result = SignatureBackgroundRemover.remove(photo.pixels.copyOf(), photo.width, photo.height)
        var expectedCore = 0
        var retainedCore = 0
        var foregroundAlpha = 0L
        var spuriousAlpha = 0L
        var coloredCore = 0
        for (y in 0 until photo.height) for (x in 0 until photo.width) {
            if (photo.coverage[y * photo.width + x] < .85f) continue
            expectedCore++
            val rx = x - result.sourceLeft
            val ry = y - result.sourceTop
            if (rx !in 0 until result.width || ry !in 0 until result.height) continue
            val output = result.pixels[ry * result.width + rx]
            if (output ushr 24 >= 160) {
                retainedCore++
                if ((output and 255) > ((output ushr 16) and 255) * 1.8) coloredCore++
            }
        }
        for (y in 0 until result.height) for (x in 0 until result.width) {
            val alpha = result.pixels[y * result.width + x] ushr 24
            val sourceX = x + result.sourceLeft
            val sourceY = y + result.sourceTop
            val coverage = if (sourceX in 0 until photo.width && sourceY in 0 until photo.height) {
                photo.coverage[sourceY * photo.width + sourceX]
            } else 0f
            if (coverage > .01f) foregroundAlpha += alpha else spuriousAlpha += alpha
        }
        assertTrue("Lost ink core: " + retainedCore + "/" + expectedCore, retainedCore >= expectedCore * .94)
        assertTrue(
            "Paper/shadow alpha: " + spuriousAlpha + ", ink alpha: " + foregroundAlpha,
            spuriousAlpha < foregroundAlpha * .025,
        )
        assertTrue(result.pixels.any { it ushr 24 == 0 })
        assertTrue(result.pixels.any { it ushr 24 in 1..254 })
        if (photo.blueInk) assertTrue("Blue ink lost its hue", coloredCore >= retainedCore * .95)
    }

    private fun assertQualityFailure(photo: Photo) {
        val result = runCatching {
            SignatureBackgroundRemover.remove(photo.pixels, photo.width, photo.height)
        }
        assertTrue("A poor photograph unexpectedly produced a savable image", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("refaça a foto com foco"))
    }

    private data class Photo(
        val pixels: IntArray,
        val coverage: FloatArray,
        val width: Int,
        val height: Int,
        val blueInk: Boolean,
    )

    private fun photograph(
        width: Int = 384,
        ink: IntArray = intArrayOf(20, 42, 116),
        hardShadow: Boolean = false,
        noise: Boolean = false,
        blemishes: Boolean = false,
        includeInk: Boolean = true,
        shadowTransmission: Double = .49,
        gradientDepth: Double = .12,
        strokeScale: Double = 1.0,
    ): Photo {
        val height = width / 2
        val scale = width / 384.0
        val coverage = FloatArray(width * height)
        fun segment(x0: Double, y0: Double, x1: Double, y1: Double, radius: Double) {
            val ax = x0 * scale
            val ay = y0 * scale
            val bx = x1 * scale
            val by = y1 * scale
            val r = radius * scale * strokeScale
            val dx = bx - ax
            val dy = by - ay
            val denominator = max(.00001, dx * dx + dy * dy)
            for (y in max(0, floor(min(ay, by) - r - 1).toInt())..min(height - 1, (max(ay, by) + r + 1).toInt())) {
                for (x in max(0, floor(min(ax, bx) - r - 1).toInt())..min(width - 1, (max(ax, bx) + r + 1).toInt())) {
                    val t = (((x - ax) * dx + (y - ay) * dy) / denominator).coerceIn(0.0, 1.0)
                    val distance = kotlin.math.hypot(x - ax - t * dx, y - ay - t * dy)
                    val alpha = (r + .5 - distance).coerceIn(0.0, 1.0).toFloat()
                    coverage[y * width + x] = max(coverage[y * width + x], alpha)
                }
            }
        }
        fun curve(points: DoubleArray, radius: Double = 1.25) {
            var previousX = points[0]
            var previousY = points[1]
            for (step in 1..80) {
                val t = step / 80.0
                val s = 1 - t
                val x = s * s * s * points[0] + 3 * s * s * t * points[2] +
                    3 * s * t * t * points[4] + t * t * t * points[6]
                val y = s * s * s * points[1] + 3 * s * s * t * points[3] +
                    3 * s * t * t * points[5] + t * t * t * points[7]
                segment(previousX, previousY, x, y, radius)
                previousX = x
                previousY = y
            }
        }
        if (includeInk) {
            curve(doubleArrayOf(45.0, 120.0, 60.0, 35.0, 96.0, 145.0, 103.0, 62.0))
            curve(doubleArrayOf(103.0, 62.0, 70.0, 151.0, 127.0, 142.0, 141.0, 88.0))
            curve(doubleArrayOf(141.0, 88.0, 109.0, 111.0, 169.0, 145.0, 184.0, 85.0))
            curve(doubleArrayOf(184.0, 85.0, 173.0, 147.0, 217.0, 77.0, 231.0, 111.0))
            curve(doubleArrayOf(231.0, 111.0, 242.0, 145.0, 270.0, 65.0, 293.0, 103.0))
            curve(doubleArrayOf(72.0, 149.0, 131.0, 145.0, 238.0, 154.0, 310.0, 142.0), radius = .65)
            segment(196.0, 70.0, 197.0, 70.0, radius = .9)
        }
        val random = Random(8123)
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val px = x / scale
            val py = y / scale
            var illumination = .97 - .12 * px / 384 - .08 * py / 192
            illumination -= gradientDepth *
                exp(-((px - 270) * (px - 270) + (py - 140) * (py - 140)) / 6000)
            if (hardShadow) {
                val edge = 173 + .22 * (py - 96)
                illumination *= shadowTransmission +
                    (1 - shadowTransmission) * ((px - edge + 1.5) / 3).coerceIn(0.0, 1.0)
            }
            var blemish = 0.0
            if (blemishes) {
                blemish = 45 * exp(-((px - 39) * (px - 39) + (py - 34) * (py - 34)) / 70)
                if ((px - 335) * (px - 335) + (py - 37) * (py - 37) < 11 * 11) blemish += 55
            }
            val commonNoise = if (noise) random.nextGaussian() * 1.3 else 0.0
            val blockArtifact = if (noise) ((x / 8 + y / 8) % 3 - 1) * .8 else 0.0
            val alpha = coverage[y * width + x]
            var color = 255 shl 24
            for (channel in 0..2) {
                val paper = intArrayOf(242, 236, 221)[channel]
                val cameraNoise = commonNoise + blockArtifact + if (noise) random.nextGaussian() * .7 else 0.0
                val value = (
                    (paper * (1 - alpha) + ink[channel] * alpha) * illumination -
                        blemish * (1 - alpha) + cameraNoise
                    ).roundToInt().coerceIn(0, 255)
                color = color or (value shl (16 - channel * 8))
            }
            pixels[y * width + x] = color
        }
        if (blemishes) {
            for ((x, y) in listOf(20 to 74, 350 to 155, 31 to 170, 325 to 80, 110 to 24)) {
                pixels[(y * scale).toInt() * width + (x * scale).toInt()] = 0xFF222222.toInt()
            }
        }
        return Photo(pixels, coverage, width, height, ink[2] > ink[0] * 2)
    }

    private fun blur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        var source = pixels
        repeat(3) {
            val horizontal = IntArray(source.size)
            val output = IntArray(source.size)
            for (y in 0 until height) for (x in 0 until width) {
                var red = 0; var green = 0; var blue = 0; var count = 0
                for (nx in max(0, x - radius)..min(width - 1, x + radius)) {
                    val color = source[y * width + nx]
                    red += (color ushr 16) and 255; green += (color ushr 8) and 255; blue += color and 255
                    count++
                }
                horizontal[y * width + x] = (255 shl 24) or (red / count shl 16) or
                    (green / count shl 8) or (blue / count)
            }
            for (y in 0 until height) for (x in 0 until width) {
                var red = 0; var green = 0; var blue = 0; var count = 0
                for (ny in max(0, y - radius)..min(height - 1, y + radius)) {
                    val color = horizontal[ny * width + x]
                    red += (color ushr 16) and 255; green += (color ushr 8) and 255; blue += color and 255
                    count++
                }
                output[y * width + x] = (255 shl 24) or (red / count shl 16) or
                    (green / count shl 8) or (blue / count)
            }
            source = output
        }
        return source
    }
}
