package com.joseleandro.scaniqo.data.signatures

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Paper matting with noise-aware, multiscale stroke evidence. A shadow step is not a stroke:
 * ink must contrast with paper on opposite sides, then belong to a coherent, sufficiently sharp
 * component. No erosion is applied to the retained strokes or their antialiased edges.
 * Ambiguous photographs are rejected instead of returning broad opaque stains.
 */
internal object SignatureBackgroundRemover {
    data class Result(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val sourceLeft: Int = 0,
        val sourceTop: Int = 0,
    )

    fun remove(
        pixels: IntArray,
        width: Int,
        height: Int,
        checkCancelled: () -> Unit = {},
    ): Result {
        require(width > 0 && height > 0 && pixels.size == width * height)
        val transparentPixels = pixels.count { it ushr 24 <= 2 }
        if (transparentPixels > pixels.size / 20 && !containsOpaquePaper(pixels, width, height)) {
            return trim(pixels, width, height, checkCancelled)
        }
        val noiseFloor = estimateNoiseFloor(pixels, width, height, checkCancelled)
        val background = Background(pixels, width, height, checkCancelled)
        val strength = ByteArray(pixels.size)
        val paperColors = IntArray(pixels.size)
        val mask = ByteArray(pixels.size)
        val paper = FloatArray(3)
        val maximumRadius = (minOf(width, height) / 8).coerceIn(8, 40)
        val radii = (listOf(1, 2, 3, 5, 8, 12, 18, 26, maximumRadius))
            .filter { it <= maximumRadius }.distinct().sorted()
        val directions = arrayOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
        val minimumSlope = maxOf(2.5f, noiseFloor * .65f)
        for (y in 0 until height) {
            checkCancelled()
            for (x in 0 until width) {
                val index = y * width + x
                val color = pixels[index]
                if (color ushr 24 <= 2) continue
                background.at(x, y, paper)
                if (maxOf(
                    abs(red(color) - paper[0]), abs(green(color) - paper[1]),
                    abs(blue(color) - paper[2]),
                ) <= noiseFloor) continue
                var bestContrast = 0
                var bestPaper = 0
                var bestChannel = 0
                for (radius in radii) {
                    for ((dx, dy) in directions) {
                        val ax = x + radius * dx
                        val ay = y + radius * dy
                        val bx = x - radius * dx
                        val by = y - radius * dy
                        // Clamping would turn a crop edge into invented evidence of paper.
                        if (ax !in 0 until width || bx !in 0 until width ||
                            ay !in 0 until height || by !in 0 until height
                        ) continue
                        val a = pixels[ay * width + ax]
                        val b = pixels[by * width + bx]
                        if (a ushr 24 <= 2 || b ushr 24 <= 2) continue
                        for (channel in 0..2) {
                            val shift = 16 - channel * 8
                            val center = (color ushr shift) and 255
                            val first = (a ushr shift) and 255
                            val second = (b ushr shift) and 255
                            val dark = if (center < paper[channel] - noiseFloor) {
                                minOf(first - center, second - center)
                            } else 0
                            val light = if (center > paper[channel] + noiseFloor) {
                                minOf(center - first, center - second)
                            } else 0
                            val contrast = maxOf(dark, light)
                            // Contrast and sharpness must come from the same scale. Otherwise
                            // small camera noise lends its slope to a broad, high-contrast shadow
                            // and the bright paper can outvote the actual ink polarity.
                            if (contrast > bestContrast && contrast >= minimumSlope * radius) {
                                bestContrast = contrast
                                bestChannel = shift
                                bestPaper = if (dark >= light) {
                                    if (first <= second) a else b
                                } else {
                                    if (first >= second) a else b
                                }
                            }
                        }
                    }
                }
                if (bestContrast <= noiseFloor) continue
                val observed = (color ushr bestChannel) and 255
                val estimated = (bestPaper ushr bestChannel) and 255
                // Normalize illumination so a pen stroke crossing a shadow stays equally opaque.
                val range = if (observed <= estimated) estimated else 255 - estimated
                strength[index] = (bestContrast * 255 / maxOf(32, range)).coerceIn(1, 255).toByte()
                paperColors[index] = bestPaper
                val darkInk = observed <= estimated
                mask[index] = if (darkInk) 1 else 6
            }
        }
        // Paper gaps between nearby strokes can look like light ink at a shadow boundary.
        // Choose the polarity only after rejecting broad, soft and incoherent components.
        val thickness = ByteArray(mask.size)
        val queue = IntArray(mask.size)
        val darkEvidence = selectCoherentStrokes(
            pixels, paperColors, mask, thickness, queue, width, height, noiseFloor,
            candidate = 1, accepted = ACCEPTED, checkCancelled,
        )
        val lightEvidence = selectCoherentStrokes(
            pixels, paperColors, mask, thickness, queue, width, height, noiseFloor,
            candidate = 6, accepted = LIGHT_ACCEPTED, checkCancelled,
        )
        require(maxOf(darkEvidence, lightEvidence) > minOf(darkEvidence, lightEvidence) * 1.25) {
            QUALITY_ERROR
        }
        val accepted = if (darkEvidence >= lightEvidence) ACCEPTED else LIGHT_ACCEPTED
        for (index in mask.indices) {
            if (index % width == 0) checkCancelled()
            mask[index] = if (mask[index] == accepted) ACCEPTED else 0
        }
        for (y in 0 until height) {
            checkCancelled()
            for (x in 0 until width) {
                val index = y * width + x
                if (mask[index] != ACCEPTED) {
                    pixels[index] = 0
                    continue
                }
                val distance = strength[index].toInt() and 255
                var peak = distance
                for (nearY in maxOf(0, y - 2)..minOf(height - 1, y + 2)) {
                    for (nearX in maxOf(0, x - 2)..minOf(width - 1, x + 2)) {
                        val near = nearY * width + nearX
                        if (mask[near] == ACCEPTED) peak = maxOf(peak, strength[near].toInt() and 255)
                    }
                }
                val color = pixels[index]
                val localPaper = paperColors[index]
                var largestDifference = 0
                var range = 255
                for (shift in intArrayOf(16, 8, 0)) {
                    val inkChannel = (color ushr shift) and 255
                    val paperChannel = (localPaper ushr shift) and 255
                    val difference = abs(inkChannel - paperChannel)
                    if (difference > largestDifference) {
                        largestDifference = difference
                        range = if (inkChannel <= paperChannel) paperChannel else 255 - paperChannel
                    }
                }
                val normalizedNoise = noiseFloor * 255f / maxOf(32, range)
                val coverage = if (distance >= peak * .9f) 1f else {
                    ((distance - normalizedNoise) / maxOf(1f, peak - normalizedNoise)).coerceIn(0f, 1f)
                }
                val alpha = ((color ushr 24) * coverage).roundToInt().coerceIn(0, 255)
                if (alpha == 0) {
                    pixels[index] = 0
                    continue
                }
                fun unmatted(channel: Int, paperChannel: Int): Int =
                    ((channel - (1f - coverage) * paperChannel) / coverage)
                        .roundToInt().coerceIn(0, 255)
                pixels[index] = (alpha shl 24) or
                    (unmatted(red(color), red(localPaper)) shl 16) or
                    (unmatted(green(color), green(localPaper)) shl 8) or
                    unmatted(blue(color), blue(localPaper))
            }
        }
        return trim(pixels, width, height, checkCancelled)
    }

    /** Robust high-frequency noise estimate; smooth gradients and sparse pen edges do not raise it. */
    private fun estimateNoiseFloor(
        pixels: IntArray, width: Int, height: Int, checkCancelled: () -> Unit,
    ): Int {
        val histogram = IntArray(256)
        var count = 0
        val step = maxOf(2, kotlin.math.sqrt(pixels.size / 30_000.0).toInt())
        for (y in 1 until height - 1 step step) {
            checkCancelled()
            for (x in 1 until width - 1 step step) {
                val index = y * width + x
                if (pixels[index] ushr 24 < 250) continue
                var deviation = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    val average = (
                        ((pixels[index - 1] ushr shift) and 255) +
                            ((pixels[index + 1] ushr shift) and 255) +
                            ((pixels[index - width] ushr shift) and 255) +
                            ((pixels[index + width] ushr shift) and 255)
                        ) / 4
                    deviation = maxOf(deviation, abs(((pixels[index] ushr shift) and 255) - average))
                }
                histogram[deviation]++
                count++
            }
        }
        var accumulated = 0
        for (noise in histogram.indices) {
            accumulated += histogram[noise]
            if (accumulated > count / 2) return maxOf(NOISE_FLOOR, noise * 3)
        }
        return NOISE_FLOOR
    }

    private fun selectCoherentStrokes(
        pixels: IntArray,
        paper: IntArray,
        mask: ByteArray,
        thickness: ByteArray,
        queue: IntArray,
        width: Int,
        height: Int,
        noiseFloor: Int,
        candidate: Byte,
        accepted: Byte,
        checkCancelled: () -> Unit,
    ): Long {
        // Capped distance-to-background catches a broad attached blot without eroding any stroke.
        for (y in 0 until height) {
            checkCancelled()
            for (x in 0 until width) {
                val i = y * width + x
                thickness[i] = if (mask[i] == candidate) {
                    val left = if (x == 0) 0 else thickness[i - 1].toInt()
                    val up = if (y == 0) 0 else thickness[i - width].toInt()
                    minOf(63, minOf(left, up) + 1).toByte()
                } else 0
            }
        }
        for (y in height - 1 downTo 0) {
            checkCancelled()
            for (x in width - 1 downTo 0) {
                val i = y * width + x
                if (mask[i] == candidate) {
                    val right = if (x == width - 1) 0 else thickness[i + 1].toInt()
                    val down = if (y == height - 1) 0 else thickness[i + width].toInt()
                    thickness[i] = minOf(thickness[i].toInt(), minOf(right, down) + 1).toByte()
                }
            }
        }
        var retained = 0
        var evidence = 0L
        var hasBroadBlot = false
        var largest = 0
        var hasSmallDetails = false
        val minimumSpan = maxOf(5, minOf(width, height) / 45)
        val maximumThickness = maxOf(12, minOf(width, height) / 25)
        for (start in mask.indices) {
            if (start % width == 0) checkCancelled()
            if (mask[start] != candidate) continue
            var tail = 1
            var head = 0
            queue[0] = start
            mask[start] = VISITED
            var left = width
            var right = 0
            var top = height
            var bottom = 0
            var peak = 0
            var gradient = 0
            var thickest = 0
            while (head < tail) {
                if (head % 4096 == 0) checkCancelled()
                val index = queue[head++]
                val x = index % width
                val y = index / width
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
                val color = pixels[index]
                val localPaper = paper[index]
                peak = maxOf(peak, colorDistance(color, localPaper))
                thickest = maxOf(thickest, thickness[index].toInt())
                for (ny in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
                    for (nx in maxOf(0, x - 1)..minOf(width - 1, x + 1)) {
                        val near = ny * width + nx
                        gradient = maxOf(gradient, colorDistance(color, pixels[near]))
                        if (mask[near] == candidate) {
                            mask[near] = VISITED
                            queue[tail++] = near
                        }
                    }
                }
            }
            val boxWidth = right - left + 1
            val boxHeight = bottom - top + 1
            val shortSide = minOf(boxWidth, boxHeight)
            val longSide = maxOf(boxWidth, boxHeight)
            val compactBlot = shortSide > maxOf(8, minOf(width, height) / 35) &&
                longSide < shortSide * 3 && tail > boxWidth.toLong() * boxHeight * .6
            val coherent = tail >= 4 && longSide >= minimumSpan &&
                peak >= maxOf(12, (noiseFloor * 1.8f).roundToInt())
            val sharp = gradient - noiseFloor >= peak * .22f
            val keep = coherent && sharp && !compactBlot
            if (keep && thickest > maximumThickness) hasBroadBlot = true
            val detailSize = maxOf(12, (minOf(width, height) / 40).let { it * it })
            val state = when {
                keep -> accepted
                tail <= detailSize && peak >= noiseFloor * 3 && sharp && !compactBlot -> SMALL_DETAIL
                else -> REJECTED
            }
            if (state == SMALL_DETAIL) hasSmallDetails = true
            for (offset in 0 until tail) mask[queue[offset]] = state
            if (keep) {
                evidence += tail.toLong() * peak * peak
                retained += tail
                largest = maxOf(largest, tail)
            }
        }
        // Compute distance once instead of searching up to 97 x 97 neighbours for every dust pixel.
        // The capped eight-connected distance preserves the previous square neighbourhood exactly.
        // Reuse the thickness buffer, which is no longer needed after component selection.
        if (hasSmallDetails) {
            val detailRadius = (minOf(width, height) / 10).coerceIn(10, 48)
            distanceToInk(mask, thickness, width, height, detailRadius + 1, accepted, checkCancelled)
            for (index in mask.indices) {
                if (index % width == 0) checkCancelled()
                if (mask[index] != SMALL_DETAIL) continue
                if (thickness[index].toInt() <= detailRadius) {
                    mask[index] = accepted
                    retained++
                } else {
                    mask[index] = REJECTED
                }
            }
        }
        // Distances were fixed before accepting details, so a chain of dust cannot recruit itself.
        return if (!hasBroadBlot && retained >= 8 && largest >= 4 && retained < mask.size * .45) {
            evidence
        } else 0L
    }

    /** Two raster passes compute capped Chebyshev distance using constant memory per pixel. */
    private fun distanceToInk(
        mask: ByteArray,
        distance: ByteArray,
        width: Int,
        height: Int,
        limit: Int,
        accepted: Byte,
        checkCancelled: () -> Unit,
    ) {
        for (y in 0 until height) {
            checkCancelled()
            for (x in 0 until width) {
                val index = y * width + x
                if (mask[index] == accepted) {
                    distance[index] = 0
                    continue
                }
                var nearest = limit
                if (x > 0) nearest = minOf(nearest, distance[index - 1].toInt() + 1)
                if (y > 0) {
                    nearest = minOf(nearest, distance[index - width].toInt() + 1)
                    if (x > 0) nearest = minOf(nearest, distance[index - width - 1].toInt() + 1)
                    if (x + 1 < width) nearest = minOf(nearest, distance[index - width + 1].toInt() + 1)
                }
                distance[index] = nearest.toByte()
            }
        }
        for (y in height - 1 downTo 0) {
            checkCancelled()
            for (x in width - 1 downTo 0) {
                val index = y * width + x
                var nearest = distance[index].toInt()
                if (x + 1 < width) nearest = minOf(nearest, distance[index + 1].toInt() + 1)
                if (y + 1 < height) {
                    nearest = minOf(nearest, distance[index + width].toInt() + 1)
                    if (x > 0) nearest = minOf(nearest, distance[index + width - 1].toInt() + 1)
                    if (x + 1 < width) nearest = minOf(nearest, distance[index + width + 1].toInt() + 1)
                }
                distance[index] = nearest.toByte()
            }
        }
    }

    private fun colorDistance(a: Int, b: Int) =
        maxOf(abs(red(a) - red(b)), abs(green(a) - green(b)), abs(blue(a) - blue(b)))

    /**
     * A PNG can have a transparent outer border while its photograph remains opaque inside.
     * Only sparse, already matted artwork bypasses extraction; a filled paper rectangle does not.
     */
    private fun containsOpaquePaper(pixels: IntArray, width: Int, height: Int): Boolean {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        var opaque = 0
        var lowestRed = 255
        var highestRed = 0
        var lowestGreen = 255
        var highestGreen = 0
        var lowestBlue = 255
        var highestBlue = 0
        for (y in 0 until height) for (x in 0 until width) {
            val color = pixels[y * width + x]
            if (color ushr 24 <= 2) continue
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
            if (color ushr 24 >= 250) {
                opaque++
                lowestRed = minOf(lowestRed, red(color))
                highestRed = maxOf(highestRed, red(color))
                lowestGreen = minOf(lowestGreen, green(color))
                highestGreen = maxOf(highestGreen, green(color))
                lowestBlue = minOf(lowestBlue, blue(color))
                highestBlue = maxOf(highestBlue, blue(color))
            }
        }
        val area = (right - left + 1).toLong() * (bottom - top + 1)
        val hasDifferentInk = maxOf(
            highestRed - lowestRed, highestGreen - lowestGreen, highestBlue - lowestBlue,
        ) > 16
        return area > 0 && minOf(right - left + 1, bottom - top + 1) >= 8 &&
            opaque >= area * .9 && hasDifferentInk
    }

    private fun trim(
        pixels: IntArray,
        width: Int,
        height: Int,
        checkCancelled: () -> Unit,
    ): Result {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            checkCancelled()
            for (x in 0 until width) {
                if (pixels[y * width + x] ushr 24 > 0) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        require(right >= left && bottom >= top) {
            "Nenhum traço foi encontrado. Ajuste o recorte ou use uma imagem mais nítida."
        }
        // Add a genuinely transparent gutter even when the crop touches the ink.
        val outputWidth = right - left + 1 + PADDING * 2
        val outputHeight = bottom - top + 1 + PADDING * 2
        val output = IntArray(outputWidth * outputHeight)
        for (y in top..bottom) {
            checkCancelled()
            pixels.copyInto(
                output,
                (y - top + PADDING) * outputWidth + PADDING,
                y * width + left,
                y * width + right + 1,
            )
        }
        return Result(output, outputWidth, outputHeight, left - PADDING, top - PADDING)
    }

    private class Background(
        private val pixels: IntArray,
        private val width: Int,
        private val height: Int,
        checkCancelled: () -> Unit,
    ) {
        private val spacing = (minOf(width, height) / 5).coerceIn(24, 64)
        private val columns = maxOf(2, ceil((width - 1).toFloat() / spacing).toInt() + 1)
        private val rows = maxOf(2, ceil((height - 1).toFloat() / spacing).toInt() + 1)
        private val grid = Array(columns * rows) { FloatArray(3) }

        init {
            for (gy in 0 until rows) {
                checkCancelled()
                val y = (gy.toFloat() * (height - 1) / (rows - 1)).roundToInt()
                for (gx in 0 until columns) {
                    val x = (gx.toFloat() * (width - 1) / (columns - 1)).roundToInt()
                    grid[gy * columns + gx] = estimate(x, y)
                }
            }
        }

        fun at(x: Int, y: Int, output: FloatArray) {
            val gx = x.toFloat() * (columns - 1) / maxOf(1, width - 1)
            val gy = y.toFloat() * (rows - 1) / maxOf(1, height - 1)
            val ix = gx.toInt().coerceIn(0, columns - 2)
            val iy = gy.toInt().coerceIn(0, rows - 2)
            val tx = gx - ix
            val ty = gy - iy
            for (channel in 0..2) {
                val upper = grid[iy * columns + ix][channel] * (1 - tx) +
                    grid[iy * columns + ix + 1][channel] * tx
                val lower = grid[(iy + 1) * columns + ix][channel] * (1 - tx) +
                    grid[(iy + 1) * columns + ix + 1][channel] * tx
                output[channel] = upper * (1 - ty) + lower * ty
            }
        }

        private fun estimate(centerX: Int, centerY: Int): FloatArray {
            val radius = spacing
            val step = maxOf(1, radius / 16)
            val redHistogram = IntArray(256)
            val greenHistogram = IntArray(256)
            val blueHistogram = IntArray(256)
            var samples = 0
            fun visit(action: (Int, Int, Int) -> Unit) {
                for (y in maxOf(0, centerY - radius)..minOf(height - 1, centerY + radius) step step) {
                    for (x in maxOf(0, centerX - radius)..minOf(width - 1, centerX + radius) step step) {
                        val color = pixels[y * width + x]
                        if (color ushr 24 >= 250) action(x, y, color)
                    }
                }
            }
            visit { _, _, color ->
                redHistogram[red(color)]++
                greenHistogram[green(color)]++
                blueHistogram[blue(color)]++
                samples++
            }
            fun median(histogram: IntArray): Float {
                var count = 0
                for (i in histogram.indices) {
                    count += histogram[i]
                    if (count > samples / 2) return i.toFloat()
                }
                return 255f
            }
            val median = floatArrayOf(
                median(redHistogram), median(greenHistogram), median(blueHistogram),
            )
            var plane = Array(3) { channel -> floatArrayOf(median[channel], 0f, 0f) }
            repeat(2) { pass ->
                val fit = ColourPlaneFit()
                visit { x, y, color ->
                    val dx = (x - centerX).toFloat() / radius
                    val dy = (y - centerY).toFloat() / radius
                    var deviation = 0f
                    for (channel in 0..2) {
                        val observed = (color ushr (16 - channel * 8)) and 255
                        deviation = maxOf(deviation, abs(observed - (
                            plane[channel][0] + dx * plane[channel][1] + dy * plane[channel][2]
                        )))
                    }
                    if (deviation <= if (pass == 0) 32f else 7f) {
                        fit.add(dx, dy, color)
                    }
                }
                if (fit.count >= 6) plane = fit.solve(plane)
            }
            return FloatArray(3) { plane[it][0].coerceIn(0f, 255f) }
        }
    }

    private class ColourPlaneFit {
        var count = 0
        private var sx = 0.0
        private var sy = 0.0
        private var sxx = 0.0
        private var sxy = 0.0
        private var syy = 0.0
        private val sc = DoubleArray(3)
        private val sxc = DoubleArray(3)
        private val syc = DoubleArray(3)

        fun add(x: Float, y: Float, color: Int) {
            count++
            sx += x
            sy += y
            sxx += x.toDouble() * x
            sxy += x.toDouble() * y
            syy += y.toDouble() * y
            for (channel in 0..2) {
                val observed = (color ushr (16 - channel * 8)) and 255
                sc[channel] += observed
                sxc[channel] += x.toDouble() * observed
                syc[channel] += y.toDouble() * observed
            }
        }

        fun solve(fallback: Array<FloatArray>): Array<FloatArray> {
            val vx = sxx - sx * sx / count
            val vy = syy - sy * sy / count
            val cov = sxy - sx * sy / count
            val determinant = vx * vy - cov * cov
            if (abs(determinant) < 0.00001) return fallback
            return Array(3) { channel ->
                val cx = sxc[channel] - sx * sc[channel] / count
                val cy = syc[channel] - sy * sc[channel] / count
                val bx = (cx * vy - cy * cov) / determinant
                val by = (cy * vx - cx * cov) / determinant
                floatArrayOf(
                    ((sc[channel] - bx * sx - by * sy) / count).toFloat(),
                    bx.toFloat(),
                    by.toFloat(),
                )
            }
        }
    }

    private fun red(color: Int) = (color ushr 16) and 255
    private fun green(color: Int) = (color ushr 8) and 255
    private fun blue(color: Int) = color and 255
    private const val ACCEPTED: Byte = 3
    private const val VISITED: Byte = 2
    private const val REJECTED: Byte = 4
    private const val SMALL_DETAIL: Byte = 5
    private const val LIGHT_ACCEPTED: Byte = 7
    private const val QUALITY_ERROR =
        "Não foi possível separar a assinatura do papel. Recorte mais perto, melhore a iluminação ou refaça a foto com foco."
    private const val NOISE_FLOOR = 5
    private const val PADDING = 4
}
