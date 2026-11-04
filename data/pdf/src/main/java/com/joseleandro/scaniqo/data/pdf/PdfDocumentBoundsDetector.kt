package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Sugere o contorno de uma folha clara inteiramente visível, nunca a caixa do texto.
 * Trabalha em até 360 px, sem modificar a fonte. Bordas fracas, enquadramento cortado ou
 * duas folhas semelhantes são ambíguos: o usuário continua com os cantos manuais.
 */
internal object PdfDocumentBoundsDetector {
    suspend fun detect(pixels: IntArray, width: Int, height: Int): PdfPerspective? {
        currentCoroutineContext().ensureActive()
        if (width < 32 || height < 32 || width.toLong() * height != pixels.size.toLong()) return null
        val scale = minOf(1f, 360f / maxOf(width, height))
        val w = (width * scale).roundToInt().coerceAtLeast(2)
        val h = (height * scale).roundToInt().coerceAtLeast(2)
        val gray = IntArray(w * h)
        val histogram = IntArray(256)
        for (y in 0 until h) {
            if (y % 32 == 0) { currentCoroutineContext().ensureActive(); yield() }
            val sourceY = (y * (height - 1f) / (h - 1)).roundToInt()
            for (x in 0 until w) {
                val sourceX = (x * (width - 1f) / (w - 1)).roundToInt()
                val color = pixels[sourceY * width + sourceX]
                val value = ((color ushr 16 and 255) * 77 +
                    (color ushr 8 and 255) * 150 + (color and 255) * 29) ushr 8
                gray[y * w + x] = value
                histogram[value]++
            }
        }
        val bright = percentile(histogram, gray.size * 9 / 10)
        if (bright - percentile(histogram, gray.size / 10) < 36) return null
        val split = otsu(histogram, gray.size)
        val thresholds = listOf(split + 8, (split + bright) / 2, bright - 24)
            .map { it.coerceIn(1, 254) }.distinct()
        val candidates = mutableListOf<Candidate>()
        for (threshold in thresholds) {
            currentCoroutineContext().ensureActive()
            candidates += candidates(gray, w, h, threshold)
        }
        val best = candidates.maxByOrNull { it.area } ?: return null
        // Não escolhe silenciosamente uma entre duas folhas de tamanho semelhante.
        if (candidates.any {
                it.area >= best.area * .65f &&
                    hypot(it.center.x - best.center.x, it.center.y - best.center.y) >
                    minOf(w, h) * .25f
            }) return null
        val padded = best.corners.map { point ->
            val dx = point.x - best.center.x
            val dy = point.y - best.center.y
            val length = hypot(dx, dy).coerceAtLeast(1f)
            // Uma margem pequena evita aparar a borda por arredondamento da miniatura.
            PdfPoint(
                ((point.x + dx / length * 2f) / (w - 1)).coerceIn(0f, 1f),
                ((point.y + dy / length * 2f) / (h - 1)).coerceIn(0f, 1f),
            )
        }
        return PdfPerspective(padded[0], padded[1], padded[2], padded[3])
            .takeIf(PdfImageCorrections::isValidPerspective)
    }

    private suspend fun candidates(gray: IntArray, w: Int, h: Int, threshold: Int): List<Candidate> {
        val seen = BooleanArray(gray.size)
        val queue = IntArray(gray.size)
        val results = mutableListOf<Candidate>()
        for (start in gray.indices) {
            if (start % 8192 == 0) { currentCoroutineContext().ensureActive(); yield() }
            if (seen[start] || gray[start] <= threshold) continue
            var read = 0
            var size = 1
            var touchesFrame = false
            queue[0] = start
            seen[start] = true
            fun offer(index: Int) {
                if (!seen[index] && gray[index] > threshold) {
                    seen[index] = true
                    queue[size++] = index
                }
            }
            while (read < size) {
                if (read > 0 && read % 8192 == 0) { currentCoroutineContext().ensureActive(); yield() }
                val index = queue[read++]
                val x = index % w
                val y = index / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesFrame = true
                if (x > 0) offer(index - 1)
                if (x < w - 1) offer(index + 1)
                if (y > 0) offer(index - w)
                if (y < h - 1) offer(index + w)
            }
            if (touchesFrame || size < gray.size * .16f) continue
            val left = IntArray(h) { w }
            val right = IntArray(h) { -1 }
            for (offset in 0 until size) {
                val x = queue[offset] % w
                val y = queue[offset] / w
                left[y] = minOf(left[y], x)
                right[y] = maxOf(right[y], x)
            }
            val outline = buildList {
                for (y in 0 until h) if (right[y] >= 0) {
                    add(Point(left[y].toFloat(), y.toFloat()))
                    add(Point(right[y].toFloat(), y.toFloat()))
                }
            }
            val hull = convexHull(outline)
            if (hull.size < 4) continue
            val corners = simplify(hull)
            val area = area(corners)
            if (area !in gray.size * .22f..gray.size * .94f || area < area(hull) * .95f) continue
            if (corners.any { it.x < 3 || it.y < 3 || it.x > w - 4 || it.y > h - 4 }) continue
            if (!hasPaperEdges(gray, w, h, corners)) continue
            val first = corners.indices.minBy { corners[it].x / w + corners[it].y / h }
            val ordered = List(4) { corners[(it + first) % 4] }
            results += Candidate(ordered, area)
        }
        return results
    }

    private fun hasPaperEdges(gray: IntArray, w: Int, h: Int, points: List<Point>): Boolean {
        val distance = maxOf(3f, minOf(w, h) * .012f)
        fun sample(x: Float, y: Float): Int? {
            val ix = x.roundToInt()
            val iy = y.roundToInt()
            return if (ix in 0 until w && iy in 0 until h) gray[iy * w + ix] else null
        }
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % 4]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = hypot(dx, dy)
            if (length < minOf(w, h) * .18f) return false
            val nx = -dy / length * distance
            val ny = dx / length * distance
            var supported = 0
            var contrast = 0
            for (step in 1..24) {
                val t = step / 25f
                val x = a.x + dx * t
                val y = a.y + dy * t
                val inside = sample(x + nx, y + ny) ?: return false
                val outside = sample(x - nx, y - ny) ?: return false
                val difference = inside - outside
                if (difference >= 18) supported++
                contrast += difference
            }
            if (supported < 18 || contrast < 24 * 24) return false
        }
        return true
    }

    private fun convexHull(points: List<Point>): List<Point> {
        val sorted = points.distinct().sortedWith(compareBy<Point> { it.x }.thenBy { it.y })
        if (sorted.size < 4) return sorted
        fun half(items: List<Point>): MutableList<Point> {
            val result = mutableListOf<Point>()
            for (point in items) {
                while (result.size >= 2 && cross(result[result.lastIndex - 1], result.last(), point) <= 0f) {
                    result.removeAt(result.lastIndex)
                }
                result += point
            }
            return result
        }
        val lower = half(sorted)
        val upper = half(sorted.asReversed())
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private fun simplify(hull: List<Point>): List<Point> {
        val result = hull.toMutableList()
        // Remover até quatro vértices de modo guloso pode trocar um canto verdadeiro
        // pelo degrau vizinho de uma borda inclinada. Preserve candidatos suficientes
        // e maximize a área externa antes de validar contraste e formato.
        while (result.size > 16) {
            val index = result.indices.minBy { i ->
                abs(cross(result[(i + result.size - 1) % result.size], result[i], result[(i + 1) % result.size]))
            }
            result.removeAt(index)
        }
        var best = result.take(4)
        var bestArea = 0f
        for (a in 0 until result.size - 3) {
            for (b in a + 1 until result.size - 2) {
                for (c in b + 1 until result.size - 1) {
                    for (d in c + 1 until result.size) {
                        val candidateArea = cross(result[a], result[b], result[c]) +
                            cross(result[a], result[c], result[d])
                        if (candidateArea > bestArea) {
                            bestArea = candidateArea
                            best = listOf(result[a], result[b], result[c], result[d])
                        }
                    }
                }
            }
        }
        return best
    }

    private fun area(points: List<Point>): Float = abs(points.indices.sumOf { index ->
        val a = points[index]
        val b = points[(index + 1) % points.size]
        (a.x * b.y - a.y * b.x).toDouble()
    }.toFloat()) / 2f

    private fun cross(a: Point, b: Point, c: Point): Float =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun percentile(histogram: IntArray, rank: Int): Int {
        var count = 0
        for (value in histogram.indices) {
            count += histogram[value]
            if (count > rank) return value
        }
        return 255
    }

    private fun otsu(histogram: IntArray, total: Int): Int {
        val sum = histogram.indices.sumOf { it.toLong() * histogram[it] }.toDouble()
        var backgroundCount = 0
        var backgroundSum = 0.0
        var maximum = -1.0
        var threshold = 0
        for (value in 0..254) {
            backgroundCount += histogram[value]
            backgroundSum += value.toDouble() * histogram[value]
            if (backgroundCount == 0 || backgroundCount == total) continue
            val foregroundCount = total - backgroundCount
            val difference = backgroundSum / backgroundCount - (sum - backgroundSum) / foregroundCount
            val variance = backgroundCount.toDouble() * foregroundCount * difference * difference
            if (variance > maximum) { maximum = variance; threshold = value }
        }
        return threshold
    }

    private data class Point(val x: Float, val y: Float)
    private data class Candidate(val corners: List<Point>, val area: Float) {
        val center = Point(corners.sumOf { it.x.toDouble() }.toFloat() / 4f,
            corners.sumOf { it.y.toDouble() }.toFloat() / 4f)
    }
}
