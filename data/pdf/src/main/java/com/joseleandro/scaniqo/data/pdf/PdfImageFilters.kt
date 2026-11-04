package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Filtros ARGB determinísticos; o chamador fornece uma cópia da captura original. */
internal object PdfImageFilters {
    /** Compatibilidade com os filtros pontuais antigos. */
    suspend fun apply(pixels: IntArray, filter: PdfImageFilter) {
        require(filter in setOf(
            PdfImageFilter.ORIGINAL, PdfImageFilter.AUTO, PdfImageFilter.GRAYSCALE,
            PdfImageFilter.BLACK_WHITE
        )) { "Informe a largura e a altura para tratar a imagem." }
        apply(pixels, filter, pixels.size, if (pixels.isEmpty()) 0 else 1)
    }

    suspend fun apply(
        pixels: IntArray,
        filter: PdfImageFilter,
        width: Int,
        height: Int,
        intensity: Float = 1f
    ) {
        currentCoroutineContext().ensureActive()
        require(width >= 0 && height >= 0 && width.toLong() * height == pixels.size.toLong()) {
            "As dimensões não correspondem à imagem."
        }
        require(intensity.isFinite() && intensity in 0f..1f) { "Intensidade inválida." }
        if (filter == PdfImageFilter.ORIGINAL || pixels.isEmpty() || intensity == 0f) return
        // Filtros pontuais misturam a intensidade no próprio pixel, sem duplicar a página inteira.
        if (filter == PdfImageFilter.AUTO || filter == PdfImageFilter.GRAYSCALE ||
            filter == PdfImageFilter.BLACK_WHITE) {
            adjustTones(pixels, filter, intensity)
            return
        }
        if (filter == PdfImageFilter.WHITEN) {
            whitenPaper(pixels, intensity)
            return
        }
        val original = if (intensity < 1f) pixels.copyOf() else null
        when (filter) {
            PdfImageFilter.ORIGINAL -> Unit
            PdfImageFilter.AUTO, PdfImageFilter.GRAYSCALE, PdfImageFilter.BLACK_WHITE ->
                adjustTones(pixels, filter)
            PdfImageFilter.ENHANCE -> {
                removeIsolatedNoise(pixels, width, height)
                smoothPreservingEdges(pixels, width, height)
                normalizeLighting(pixels, width, height, shadowOnly = true)
                adjustTones(pixels, PdfImageFilter.AUTO)
                sharpen(pixels, width, height)
            }
            PdfImageFilter.SHADOW_REMOVAL ->
                normalizeLighting(pixels, width, height, shadowOnly = true)
            PdfImageFilter.ILLUMINATION ->
                normalizeLighting(pixels, width, height, shadowOnly = false)
            PdfImageFilter.WHITEN -> whitenPaper(pixels)
            PdfImageFilter.ADAPTIVE_BLACK_WHITE -> adaptiveThreshold(pixels, width, height)
            PdfImageFilter.DENOISE -> {
                removeIsolatedNoise(pixels, width, height)
                smoothPreservingEdges(pixels, width, height)
            }
            PdfImageFilter.SHARPEN -> sharpen(pixels, width, height)
            PdfImageFilter.BLEED_THROUGH -> {
                normalizeLighting(pixels, width, height, shadowOnly = false)
                for (index in pixels.indices) {
                    if (index % 16_384 == 0) currentCoroutineContext().ensureActive()
                    val pixel = pixels[index]
                    val light = luminance(pixel)
                    // Atenuação conservadora de tinta fraca; nunca promete distinguir frente e verso.
                    val fade = ((light - 150) / 85f).coerceIn(0f, 1f)
                    pixels[index] = mix(pixel, gray(pixel and -0x1000000, 255), fade * fade)
                }
            }
        }
        if (original != null) {
            for (index in pixels.indices) {
                if (index % 16_384 == 0) currentCoroutineContext().ensureActive()
                pixels[index] = mix(original[index], pixels[index], intensity)
            }
        }
    }

    private suspend fun adjustTones(
        pixels: IntArray, filter: PdfImageFilter, intensity: Float = 1f
    ) {
        // Cinza não precisa de histograma; o caminho anterior varria a página duas vezes.
        if (filter == PdfImageFilter.GRAYSCALE) {
            for (index in pixels.indices) {
                if (index and 16_383 == 0) yield()
                val original = pixels[index]
                val filtered = gray(original and -0x1000000, luminance(original))
                pixels[index] = if (intensity == 1f) filtered else mix(original, filtered, intensity)
            }
            return
        }
        val histogram = IntArray(256)
        for (index in pixels.indices) {
            if (index and 16_383 == 0) yield()
            histogram[luminance(pixels[index])]++
        }
        if (filter == PdfImageFilter.BLACK_WHITE) {
            val threshold = otsuThreshold(histogram, pixels.size)
            for (index in pixels.indices) {
                if (index and 16_383 == 0) yield()
                val original = pixels[index]
                val filtered = gray(original and -0x1000000,
                    if (luminance(original) <= threshold) 0 else 255)
                pixels[index] = if (intensity == 1f) filtered else mix(original, filtered, intensity)
            }
            return
        }
        val low = percentile(histogram, pixels.size, .001)
        val high = percentile(histogram, pixels.size, .99)
        if (high - low < 16) return
        // A mesma curva exata é calculada só 256 vezes, em vez de três divisões por pixel.
        val tones = IntArray(256) { stretch(it, low, high) }
        for (index in pixels.indices) {
            if (index and 16_383 == 0) yield()
            val original = pixels[index]
            val filtered = (original and -0x1000000) or
                (tones[(original ushr 16) and 255] shl 16) or
                (tones[(original ushr 8) and 255] shl 8) or tones[original and 255]
            pixels[index] = if (intensity == 1f) filtered else mix(original, filtered, intensity)
        }
    }

    /**
     * Estima o papel pelo percentil 90 em blocos e interpola a iluminação entre seus centros.
     * Texto escuro não domina a estimativa; memória proporcional à grade, sem janelas gigantes.
     */
    private suspend fun normalizeLighting(
        pixels: IntArray, width: Int, height: Int, shadowOnly: Boolean
    ) {
        val cell = (minOf(width, height) / 12).coerceIn(16, 96)
        val columns = (width + cell - 1) / cell
        val rows = (height + cell - 1) / cell
        val background = IntArray(columns * rows)
        val histogram = IntArray(256)
        for (row in 0 until rows) {
            currentCoroutineContext().ensureActive()
            for (column in 0 until columns) {
                if (column and 7 == 0) yield()
                histogram.fill(0)
                val left = column * cell
                val top = row * cell
                val right = minOf(left + cell, width)
                val bottom = minOf(top + cell, height)
                for (y in top until bottom) for (x in left until right) {
                    histogram[luminance(pixels[y * width + x])]++
                }
                background[row * columns + column] =
                    percentile(histogram, (right - left) * (bottom - top), .90)
            }
        }
        val brightest = background.maxOrNull() ?: return
        // Mantém páginas uniformes e desenhos de alto contraste como estão no automático.
        if (shadowOnly && brightest - (background.minOrNull() ?: brightest) < 14) return
        val target = if (shadowOnly) brightest.coerceAtLeast(210) else 248
        // Interpolação horizontal depende apenas da grade, não de cada linha da captura.
        val horizontalBackground = FloatArray(rows * width)
        for (row in 0 until rows) {
            yield()
            for (x in 0 until width) {
                val gx = ((x + .5f) / cell - .5f).coerceIn(0f, (columns - 1).toFloat())
                val x0 = gx.toInt()
                val x1 = minOf(x0 + 1, columns - 1)
                horizontalBackground[row * width + x] = lerp(
                    background[row * columns + x0].toFloat(),
                    background[row * columns + x1].toFloat(), gx - x0,
                )
            }
        }
        for (y in 0 until height) {
            currentCoroutineContext().ensureActive()
            if (y and 15 == 0) yield()
            val gy = ((y + .5f) / cell - .5f).coerceIn(0f, (rows - 1).toFloat())
            val y0 = gy.toInt()
            val upper = y0 * width
            val lower = minOf(y0 + 1, rows - 1) * width
            for (x in 0 until width) {
                val local = lerp(horizontalBackground[upper + x],
                    horizontalBackground[lower + x], gy - y0).coerceAtLeast(40f)
                val gain = (target / local).coerceIn(1f, 2.5f)
                val index = y * width + x
                pixels[index] = channels(pixels[index]) { (it * gain).roundToInt() }
            }
        }
    }

    private suspend fun whitenPaper(pixels: IntArray, intensity: Float = 1f) {
        val amounts = FloatArray(256) { value ->
            val amount = ((value - 145) / 100f).coerceIn(0f, 1f)
            amount * amount
        }
        for (index in pixels.indices) {
            if (index and 16_383 == 0) yield()
            val pixel = pixels[index]
            val filtered = mix(pixel, gray(pixel and -0x1000000, 255), amounts[luminance(pixel)])
            pixels[index] = if (intensity == 1f) filtered else mix(pixel, filtered, intensity)
        }
    }

    /** Média local em duas passagens deslizantes: O(pixels), independente do raio escolhido. */
    private suspend fun adaptiveThreshold(pixels: IntArray, width: Int, height: Int) {
        val radius = (minOf(width, height) / 32).coerceIn(2, 40)
        val horizontal = FloatArray(pixels.size)
        val rowLuminance = IntArray(width)
        val horizontalCounts = IntArray(width) {
            minOf(width - 1, it + radius) - maxOf(0, it - radius) + 1
        }
        for (y in 0 until height) {
            currentCoroutineContext().ensureActive()
            if (y and 15 == 0) yield()
            val offset = y * width
            for (x in 0 until width) rowLuminance[x] = luminance(pixels[offset + x])
            var sum = 0
            for (x in 0..minOf(radius, width - 1)) sum += rowLuminance[x]
            for (x in 0 until width) {
                horizontal[offset + x] = sum.toFloat() / horizontalCounts[x]
                if (x - radius >= 0) sum -= rowLuminance[x - radius]
                if (x + radius + 1 < width) sum += rowLuminance[x + radius + 1]
            }
        }
        // Percorre linhas contíguas para aproveitar o cache, mantendo a ordem exata das somas.
        val columnSums = FloatArray(width)
        for (y in 0..minOf(radius, height - 1)) {
            val offset = y * width
            for (x in 0 until width) columnSums[x] += horizontal[offset + x]
        }
        for (y in 0 until height) {
            currentCoroutineContext().ensureActive()
            if (y and 15 == 0) yield()
            val count = minOf(height - 1, y + radius) - maxOf(0, y - radius) + 1
            val offset = y * width
            val previousOffset = (y - radius) * width
            val nextOffset = (y + radius + 1) * width
            for (x in 0 until width) {
                val mean = columnSums[x] / count
                val pixel = pixels[offset + x]
                val threshold = mean - maxOf(5f, mean * .065f)
                pixels[offset + x] = gray(pixel and -0x1000000,
                    if (luminance(pixel) < threshold) 0 else 255)
                if (y - radius >= 0) columnSums[x] -= horizontal[previousOffset + x]
                if (y + radius + 1 < height) columnSums[x] += horizontal[nextOffset + x]
            }
        }
    }

    /** Suavização bilateral 3×3: vizinhos de cor diferente não borram as bordas do texto. */
    private suspend fun smoothPreservingEdges(pixels: IntArray, width: Int, height: Int) {
        neighborhoodRows(pixels, width, height) { previous, current, next, x ->
            val center = current[x]
            if (previous[x - 1] == center && previous[x] == center && previous[x + 1] == center &&
                current[x - 1] == center && current[x + 1] == center &&
                next[x - 1] == center && next[x] == center && next[x + 1] == center) {
                center
            } else {
                val centerAlpha = center ushr 24
                val centerRed = channel(center, 16)
                val centerGreen = channel(center, 8)
                val centerBlue = channel(center, 0)
                var total = 160
                var red = centerRed * total
                var green = centerGreen * total
                var blue = centerBlue * total
                for (rowIndex in 0..2) {
                    val row = when (rowIndex) { 0 -> previous; 1 -> current; else -> next }
                    for (dx in -1..1) {
                        if (rowIndex == 1 && dx == 0) continue
                        val neighbor = row[x + dx]
                        if (neighbor ushr 24 != centerAlpha) continue
                        val neighborRed = channel(neighbor, 16)
                        val neighborGreen = channel(neighbor, 8)
                        val neighborBlue = channel(neighbor, 0)
                        val difference = maxOf(abs(centerRed - neighborRed),
                            abs(centerGreen - neighborGreen), abs(centerBlue - neighborBlue))
                        if (difference >= 40) continue
                        val weight = 40 - difference
                        total += weight
                        red += neighborRed * weight
                        green += neighborGreen * weight
                        blue += neighborBlue * weight
                    }
                }
                (center and -0x1000000) or ((red / total) shl 16) or
                    ((green / total) shl 8) or (blue / total)
            }
        }
    }

    /** Soma deslizante 3×3: cada luminância é calculada uma vez, sem copiar a página inteira. */
    private suspend fun sharpen(pixels: IntArray, width: Int, height: Int) {
        if (width < 3 || height < 3) return
        var previous = IntArray(width) { luminance(pixels[it]) }
        var current = IntArray(width) { luminance(pixels[width + it]) }
        var next = IntArray(width) { luminance(pixels[width * 2 + it]) }
        for (y in 1 until height - 1) {
            currentCoroutineContext().ensureActive()
            if (y and 15 == 0) yield()
            var total = 0
            for (x in 0..2) total += previous[x] + current[x] + next[x]
            val offset = y * width
            for (x in 1 until width - 1) {
                val difference = current[x] - total / 9
                val detail = if (abs(difference) < 3) 0 else
                    (difference * .6f).roundToInt().coerceIn(-24, 24)
                if (detail != 0) pixels[offset + x] = channels(pixels[offset + x]) { it + detail }
                if (x + 2 < width) {
                    total += previous[x + 2] + current[x + 2] + next[x + 2]
                    total -= previous[x - 1] + current[x - 1] + next[x - 1]
                }
            }
            val reusable = previous
            previous = current
            current = next
            next = reusable
            if (y + 2 < height) {
                val nextOffset = (y + 2) * width
                for (x in 0 until width) next[x] = luminance(pixels[nextOffset + x])
            }
        }
    }

    /** Três linhas originais permitem escrever no destino usando memória O(largura). */
    private suspend inline fun neighborhoodRows(
        pixels: IntArray, width: Int, height: Int,
        operation: (IntArray, IntArray, IntArray, Int) -> Int
    ) {
        if (width < 3 || height < 3) return
        var previous = pixels.copyOfRange(0, width)
        var current = pixels.copyOfRange(width, width * 2)
        var next = pixels.copyOfRange(width * 2, width * 3)
        for (y in 1 until height - 1) {
            currentCoroutineContext().ensureActive()
            if (y and 15 == 0) yield()
            val offset = y * width
            for (x in 1 until width - 1) pixels[offset + x] = operation(previous, current, next, x)
            val reusable = previous
            previous = current
            current = next
            next = reusable
            if (y + 2 < height) pixels.copyInto(next, 0, (y + 2) * width, (y + 3) * width)
        }
    }

    private fun mix(original: Int, filtered: Int, amount: Float): Int =
        (original and -0x1000000) or
            (lerp(channel(original, 16).toFloat(), channel(filtered, 16).toFloat(), amount).roundToInt() shl 16) or
            (lerp(channel(original, 8).toFloat(), channel(filtered, 8).toFloat(), amount).roundToInt() shl 8) or
            lerp(channel(original, 0).toFloat(), channel(filtered, 0).toFloat(), amount).roundToInt()

    private inline fun channels(pixel: Int, transform: (Int) -> Int): Int =
        (pixel and -0x1000000) or (transform(channel(pixel, 16)).coerceIn(0, 255) shl 16) or
            (transform(channel(pixel, 8)).coerceIn(0, 255) shl 8) or transform(channel(pixel, 0)).coerceIn(0, 255)

    private fun channel(pixel: Int, shift: Int): Int = (pixel ushr shift) and 255

    private fun lerp(first: Float, second: Float, amount: Float): Float =
        first + (second - first) * amount

    /**
     * Remove apenas impulsos isolados de alto contraste sobre uma vizinhança 3×3 uniforme.
     * Um vizinho semelhante ao centro protege traços conectados, inclusive linhas de um pixel.
     * Não aplica desfoque geral nem tenta reconstruir detalhes perdidos ou manchas grandes.
     * Três linhas originais mantêm o resultado independente da ordem, usando memória O(largura).
     */
    private suspend fun removeIsolatedNoise(pixels: IntArray, width: Int, height: Int) {
        if (width < 3 || height < 3) return
        var previous = pixels.copyOfRange(0, width)
        var current = pixels.copyOfRange(width, width * 2)
        var next = pixels.copyOfRange(width * 2, width * 3)
        val neighbors = IntArray(8)
        for (y in 1 until height - 1) {
            currentCoroutineContext().ensureActive()
            if (y and 15 == 0) yield()
            for (x in 1 until width - 1) {
                if (x % 1024 == 0) currentCoroutineContext().ensureActive()
                val center = current[x]
                val alpha = center ushr 24
                if (alpha == 0) continue
                neighbors[0] = previous[x - 1]
                neighbors[1] = previous[x]
                neighbors[2] = previous[x + 1]
                neighbors[3] = current[x - 1]
                neighbors[4] = current[x + 1]
                neighbors[5] = next[x - 1]
                neighbors[6] = next[x]
                neighbors[7] = next[x + 1]
                val centerLuminance = luminance(center)
                var minimum = 255
                var maximum = 0
                var red = 0
                var green = 0
                var blue = 0
                var supported = false
                for (neighbor in neighbors) {
                    val value = luminance(neighbor)
                    if (neighbor ushr 24 != alpha ||
                        abs(value - centerLuminance) <= SAME_STROKE_TOLERANCE) {
                        supported = true
                        break
                    }
                    minimum = minOf(minimum, value)
                    maximum = maxOf(maximum, value)
                    red += (neighbor ushr 16) and 255
                    green += (neighbor ushr 8) and 255
                    blue += neighbor and 255
                }
                if (supported || maximum - minimum > MAX_BACKGROUND_VARIATION) continue
                val replacement = (center and -0x1000000) or
                    ((red / 8) shl 16) or ((green / 8) shl 8) or (blue / 8)
                if (abs(centerLuminance - luminance(replacement)) >= MIN_ISOLATED_DIFFERENCE) {
                    pixels[y * width + x] = replacement
                }
            }
            // As próximas leituras usam as linhas anteriores ao filtro, nunca pixels já tratados.
            val reusable = previous
            previous = current
            current = next
            next = reusable
            if (y + 2 < height) pixels.copyInto(next, 0, (y + 2) * width, (y + 3) * width)
        }
    }

    private const val SAME_STROKE_TOLERANCE = 32
    private const val MAX_BACKGROUND_VARIATION = 20
    private const val MIN_ISOLATED_DIFFERENCE = 48

    private fun luminance(pixel: Int): Int =
        (77 * ((pixel ushr 16) and 255) + 150 * ((pixel ushr 8) and 255) +
            29 * (pixel and 255) + 128) ushr 8

    private fun gray(alpha: Int, value: Int): Int =
        alpha or (value shl 16) or (value shl 8) or value

    private fun stretch(value: Int, low: Int, high: Int): Int =
        ((value - low) * 255 / (high - low)).coerceIn(0, 255)

    private fun percentile(histogram: IntArray, count: Int, percentile: Double): Int {
        val target = ceil(count * percentile).toInt().coerceAtLeast(1)
        var accumulated = 0
        histogram.forEachIndexed { value, frequency ->
            accumulated += frequency
            if (accumulated >= target) return value
        }
        return 255
    }

    /** O limiar acompanha a luminosidade da captura, em vez de fixar preto/branco em 128. */
    private fun otsuThreshold(histogram: IntArray, count: Int): Int {
        val total = histogram.indices.sumOf { it.toDouble() * histogram[it] }
        var weight = 0
        var sum = 0.0
        var largestVariance = -1.0
        var threshold = 127
        histogram.forEachIndexed { value, frequency ->
            weight += frequency
            if (weight == 0) return@forEachIndexed
            val remaining = count - weight
            if (remaining == 0) return threshold
            sum += value.toDouble() * frequency
            val difference = sum / weight - (total - sum) / remaining
            val variance = weight.toDouble() * remaining * difference * difference
            if (variance > largestVariance) {
                largestVariance = variance
                threshold = value
            }
        }
        return threshold
    }
}
