package com.joseleandro.scaniqo.data.pdf

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.max

/** Sinais conservadores: sugerem revisão, nunca excluem nem alteram páginas automaticamente. */
internal class PdfPageQualityMetrics(
    val isBlank: Boolean,
    val isBlurry: Boolean,
    val mayBeClipped: Boolean,
    val fingerprint: IntArray,
) {
    fun resembles(other: PdfPageQualityMetrics): Boolean {
        if (isBlank || other.isBlank || fingerprint.size != other.fingerprint.size) return false
        var squaredDifference = 0L
        var largeDifferences = 0
        for (index in fingerprint.indices) {
            val difference = fingerprint[index] - other.fingerprint[index]
            squaredDifference += difference.toLong() * difference
            if (abs(difference) > 12) largeDifferences++
        }
        // Similaridade estrita evita classificar formulários com respostas diferentes como iguais.
        return squaredDifference.toDouble() / fingerprint.size < 2.0 &&
            largeDifferences.toDouble() / fingerprint.size < .001
    }

    companion object {
        suspend fun measure(pixels: IntArray, width: Int, height: Int): PdfPageQualityMetrics {
            require(width >= 3 && height >= 3 && pixels.size == width * height)
            val luminance = IntArray(pixels.size) { index ->
                val color = pixels[index]
                (((color ushr 16) and 255) * 77 +
                    ((color ushr 8) and 255) * 150 + (color and 255) * 29) ushr 8
            }
            var sum = 0.0
            var sumSquares = 0.0
            var dark = 0
            var gradient = 0.0
            var laplacian = 0.0
            var edgeInk = 0
            var edgeCount = 0
            val borderX = max(1, width / 100)
            val borderY = max(1, height / 100)
            for (y in 0 until height) {
                if (y % 32 == 0) currentCoroutineContext().ensureActive()
                for (x in 0 until width) {
                    val index = y * width + x
                    val value = luminance[index]
                    sum += value
                    sumSquares += value.toDouble() * value
                    if (value < 190) dark++
                    if (x < borderX || x >= width - borderX || y < borderY || y >= height - borderY) {
                        edgeCount++
                        if (value < 120) edgeInk++
                    }
                    if (x in 1 until width - 1 && y in 1 until height - 1) {
                        val gx = luminance[index + 1] - luminance[index - 1]
                        val gy = luminance[index + width] - luminance[index - width]
                        gradient += gx.toDouble() * gx + gy.toDouble() * gy
                        val lap = luminance[index - 1] + luminance[index + 1] +
                            luminance[index - width] + luminance[index + width] - 4 * value
                        laplacian += lap.toDouble() * lap
                    }
                }
            }
            val count = luminance.size.toDouble()
            val mean = sum / count
            val variance = sumSquares / count - mean * mean
            val darkRatio = dark / count
            val blank = mean > 245 && variance < 5 && darkRatio < .0003
            val blurry = !blank && darkRatio in .003..0.5 && variance > 30 &&
                gradient / count > 2 && laplacian / gradient.coerceAtLeast(1.0) < .12
            val gridWidth = 96
            val gridHeight = 128
            val fingerprint = IntArray(gridWidth * gridHeight) { cell ->
                val x = cell % gridWidth
                val y = cell / gridWidth
                val x0 = x * width / gridWidth
                val x1 = max(x0 + 1, (x + 1) * width / gridWidth).coerceAtMost(width)
                val y0 = y * height / gridHeight
                val y1 = max(y0 + 1, (y + 1) * height / gridHeight).coerceAtMost(height)
                var total = 0L
                for (row in y0 until y1) for (column in x0 until x1) {
                    total += luminance[row * width + column]
                }
                (total / ((x1 - x0) * (y1 - y0))).toInt()
            }
            return PdfPageQualityMetrics(
                isBlank = blank,
                isBlurry = blurry,
                mayBeClipped = !blank && edgeInk.toDouble() / edgeCount.coerceAtLeast(1) > .06,
                fingerprint = fingerprint,
            )
        }
    }
}
