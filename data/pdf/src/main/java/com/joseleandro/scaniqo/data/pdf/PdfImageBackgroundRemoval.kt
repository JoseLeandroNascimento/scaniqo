package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.isValidBackgroundMask
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Apaga apenas o entorno da folha, sem retificar, esticar ou filtrar seu conteúdo.
 * A máscara usa coordenadas da fonte; o recorte somente muda a janela de amostragem.
 * Pixels internos são copiados exatamente. Só a borda recebe cobertura subpixel.
 * Quando a captura ocupa parte do PDF, todo conteúdo fora dela permanece intacto.
 */
internal object PdfImageBackgroundRemoval {
    suspend fun apply(
        pixels: IntArray,
        width: Int,
        height: Int,
        mask: PdfPerspective?,
        crop: PdfCropBounds? = null,
        captureBounds: PdfCropBounds? = null,
    ): IntArray {
        currentCoroutineContext().ensureActive()
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
        if (mask == null || !mask.isValidBackgroundMask()) return pixels
        val bounds = crop?.takeIf { it.isValid() } ?: PdfCropBounds()
        if (captureBounds != null && !captureBounds.isValid()) return pixels
        val capture = captureBounds ?: PdfCropBounds()
        fun column(value: Float) = ceil(
            ((value - bounds.left) / (bounds.right - bounds.left) * width - .5f).toDouble(),
        ).toInt().coerceIn(0, width)
        fun row(value: Float) = ceil(
            ((value - bounds.top) / (bounds.bottom - bounds.top) * height - .5f).toDouble(),
        ).toInt().coerceIn(0, height)
        val captureStart = column(capture.left)
        val captureEnd = column(capture.right)
        val captureTop = row(capture.top)
        val captureBottom = row(capture.bottom)
        if (captureStart >= captureEnd || captureTop >= captureBottom) return pixels
        val corners = listOf(mask.topLeft, mask.topRight, mask.bottomRight, mask.bottomLeft)
        val xs = corners.map { (it.x - bounds.left) / (bounds.right - bounds.left) * width }
        val ys = corners.map { (it.y - bounds.top) / (bounds.bottom - bounds.top) * height }
        val output = if (captureBounds == null) IntArray(pixels.size) { WHITE } else pixels.copyOf()
        val first = FloatArray(2)
        val second = FloatArray(2)
        for (y in captureTop until captureBottom) {
            if ((y - captureTop) % 32 == 0) { currentCoroutineContext().ensureActive(); yield() }
            val offset = y * width
            if (captureBounds != null) output.fill(WHITE, offset + captureStart, offset + captureEnd)
            val hasFirst = interval(xs, ys, y + .25f, first)
            val hasSecond = interval(xs, ys, y + .75f, second)
            if (!hasFirst && !hasSecond) continue
            val minimum = minOf(if (hasFirst) first[0] else Float.POSITIVE_INFINITY,
                if (hasSecond) second[0] else Float.POSITIVE_INFINITY)
            val maximum = maxOf(if (hasFirst) first[1] else Float.NEGATIVE_INFINITY,
                if (hasSecond) second[1] else Float.NEGATIVE_INFINITY)
            val start = floor(minimum - .75f).toInt().coerceIn(captureStart, captureEnd)
            val end = (ceil(maximum - .25f).toInt() + 1).coerceIn(captureStart, captureEnd)
            val fullStart = if (hasFirst && hasSecond) {
                ceil(maxOf(first[0], second[0]) - .25f).toInt().coerceIn(start, end)
            } else end
            val fullEnd = if (hasFirst && hasSecond) {
                (floor(minOf(first[1], second[1]) - .75f).toInt() + 1).coerceIn(fullStart, end)
            } else end
            pixels.copyInto(output, offset + fullStart, offset + fullStart, offset + fullEnd)
            fun paintEdge(x: Int) {
                var coverage = 0
                if (hasFirst) {
                    if (x + .25f in first[0]..first[1]) coverage++
                    if (x + .75f in first[0]..first[1]) coverage++
                }
                if (hasSecond) {
                    if (x + .25f in second[0]..second[1]) coverage++
                    if (x + .75f in second[0]..second[1]) coverage++
                }
                output[offset + x] = blendWithPaper(pixels[offset + x], coverage)
            }
            for (x in start until fullStart) paintEdge(x)
            for (x in fullEnd until end) paintEdge(x)
        }
        return output
    }

    private fun interval(xs: List<Float>, ys: List<Float>, y: Float, result: FloatArray): Boolean {
        var left = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        for (i in 0..3) {
            val next = (i + 1) % 4
            val y1 = ys[i]
            val y2 = ys[next]
            if (y >= minOf(y1, y2) && y < maxOf(y1, y2)) {
                val x = xs[i] + (y - y1) / (y2 - y1) * (xs[next] - xs[i])
                left = minOf(left, x)
                right = maxOf(right, x)
            }
        }
        result[0] = left
        result[1] = right
        return left < right
    }

    private fun blendWithPaper(color: Int, coverage: Int): Int {
        if (coverage == 4) return color
        if (coverage == 0) return WHITE
        fun channel(shift: Int) =
            (((color ushr shift and 255) * coverage + 255 * (4 - coverage) + 2) / 4) shl shift
        return (255 shl 24) or channel(16) or channel(8) or channel(0)
    }

    private const val WHITE = -1
}
