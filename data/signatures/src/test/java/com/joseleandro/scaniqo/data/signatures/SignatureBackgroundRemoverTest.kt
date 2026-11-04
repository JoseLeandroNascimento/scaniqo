package com.joseleandro.scaniqo.data.signatures

import kotlin.math.exp
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureBackgroundRemoverTest {
    @Test fun whitePaperBecomesRealAlphaAndExcessMarginsAreTrimmed() {
        val image = fixture { _, _ -> rgb(255, 255, 255) }
        for (x in 40..159) image[60 * WIDTH + x] = rgb(24, 35, 48)
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        assertEquals(128, result.width)
        assertEquals(9, result.height)
        assertEquals(0, result.pixels.first() ushr 24)
        assertEquals(rgb(24, 35, 48), result.pixels[4 * result.width + 4])
        assertEquals(120, result.pixels.count { it ushr 24 > 0 })
    }

    @Test fun coloredPaperIsRemovedWhileBlueInkRetainsItsHue() {
        val image = fixture { _, _ -> rgb(200, 220, 160) }
        for (x in 40..159) for (y in 58..62) image[y * WIDTH + x] = rgb(20, 50, 145)
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        assertEquals(128, result.width)
        assertEquals(13, result.height)
        assertTrue(result.pixels.any { it ushr 24 == 0 })
        assertTrue(result.pixels.filter { it ushr 24 > 0 }.all { it == rgb(20, 50, 145) })
    }

    @Test fun darkUniformPaperAlsoAllowsLighterInk() {
        val image = fixture { _, _ -> rgb(42, 51, 67) }
        for (x in 40..159) image[60 * WIDTH + x] = rgb(230, 220, 200)
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        assertEquals(128, result.width)
        assertEquals(rgb(230, 220, 200), result.pixels[4 * result.width + 4])
    }

    @Test fun smoothShadowAndUnevenIlluminationAreRemoved() {
        val image = fixture { x, y ->
            val shadow = 24 * exp(-((x - 110.0) * (x - 110.0) / 5000.0))
            val paper = (250 - x * 0.15 - y * 0.12 - shadow).roundToInt()
            rgb(paper, paper - 3, paper - 7)
        }
        for (x in 40..159) for (y in 58..62) image[y * WIDTH + x] = rgb(28, 35, 50)
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        assertEquals(128, result.width)
        assertEquals(13, result.height)
        assertEquals(600, result.pixels.count { it ushr 24 > 0 })
    }

    @Test fun faintOnePixelStrokeSurvivesWithoutAnErosionStep() {
        val image = fixture { _, _ -> rgb(255, 255, 255) }
        for (x in 40..159) image[60 * WIDTH + x] = rgb(230, 230, 230)
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        assertEquals(120, result.pixels.count { it ushr 24 > 0 })
        assertEquals(rgb(230, 230, 230), result.pixels[4 * result.width + 40])
    }

    @Test fun antialiasedEdgesHaveFractionalAlphaWithoutWhiteFringes() {
        val image = fixture { _, _ -> rgb(255, 255, 255) }
        for (x in 40..159) {
            image[59 * WIDTH + x] = rgb(137, 152, 202) // 50% of blue ink over white.
            image[60 * WIDTH + x] = rgb(20, 50, 150)
            image[61 * WIDTH + x] = rgb(137, 152, 202)
        }
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        val edge = result.pixels[4 * result.width + 30]
        assertTrue(edge ushr 24 in 115..140)
        assertTrue(((edge ushr 16) and 255) < 30)
        assertTrue(((edge ushr 8) and 255) < 60)
        assertTrue((edge and 255) in 140..160)
        assertEquals(rgb(20, 50, 150), result.pixels[5 * result.width + 30])
    }

    @Test fun transparentInputRetainsItsOriginalSoftAlphaAndInkColor() {
        val image = IntArray(WIDTH * HEIGHT)
        for (x in 40..159) image[60 * WIDTH + x] = 0x60203070
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        assertEquals(120, result.pixels.count { it ushr 24 > 0 })
        assertTrue(result.pixels.filter { it ushr 24 > 0 }.all { it == 0x60203070 })
        assertFalse(result.pixels.any { it ushr 24 == 255 })
    }

    @Test fun transparentBorderDoesNotBypassRemovalOfOpaquePaperInside() {
        val image = IntArray(WIDTH * HEIGHT)
        for (y in 10 until HEIGHT - 10) for (x in 10 until WIDTH - 10) {
            image[y * WIDTH + x] = rgb(200, 220, 160)
        }
        for (x in 40..159) image[60 * WIDTH + x] = rgb(20, 50, 145)
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        assertEquals(128, result.width)
        assertEquals(9, result.height)
        assertEquals(120, result.pixels.count { it ushr 24 > 0 })
        assertTrue(result.pixels.filter { it ushr 24 > 0 }.all { it == rgb(20, 50, 145) })
    }

    @Test fun aSmallDetachedPenDotIsKeptWhileDistantDustIsRemoved() {
        val image = fixture { _, _ -> rgb(255, 255, 255) }
        for (x in 40..159) image[60 * WIDTH + x] = rgb(20, 50, 145)
        for (y in 51..52) for (x in 98..99) image[y * WIDTH + x] = rgb(20, 50, 145)
        image[20 * WIDTH + 20] = rgb(20, 20, 20)
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        for (y in 51..52) for (x in 98..99) {
            val actual = result.pixels[(y - result.sourceTop) * result.width + x - result.sourceLeft]
            assertEquals(rgb(20, 50, 145), actual)
        }
        assertEquals(124, result.pixels.count { it ushr 24 > 0 })
    }

    @Test fun detachedDotAtDiagonalLimitIsKeptWithoutRecruitingAChainOfDust() {
        val image = fixture { _, _ -> rgb(255, 255, 255) }
        for (x in 40..159) image[60 * WIDTH + x] = rgb(20, 50, 145)
        // The neighbourhood radius is 12; use a diagonal neighbour at exactly that distance.
        image[48 * WIDTH + 28] = rgb(20, 50, 145)
        image[38 * WIDTH + 18] = rgb(20, 50, 145)
        val result = SignatureBackgroundRemover.remove(image, WIDTH, HEIGHT)
        val dot = result.pixels[(48 - result.sourceTop) * result.width + 28 - result.sourceLeft]
        assertEquals(rgb(20, 50, 145), dot)
        assertEquals(121, result.pixels.count { it ushr 24 > 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankImageCannotBecomeASavableSignature() {
        SignatureBackgroundRemover.remove(fixture { _, _ -> rgb(230, 220, 200) }, WIDTH, HEIGHT)
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun cancellationIsObservedDuringProcessing() {
        SignatureBackgroundRemover.remove(fixture { _, _ -> rgb(255, 255, 255) }, WIDTH, HEIGHT) {
            throw kotlinx.coroutines.CancellationException("cancelled")
        }
    }

    private fun fixture(color: (Int, Int) -> Int) =
        IntArray(WIDTH * HEIGHT) { color(it % WIDTH, it / WIDTH) }

    private fun rgb(red: Int, green: Int, blue: Int) =
        (255 shl 24) or (red shl 16) or (green shl 8) or blue

    private companion object {
        const val WIDTH = 200
        const val HEIGHT = 120
    }
}
