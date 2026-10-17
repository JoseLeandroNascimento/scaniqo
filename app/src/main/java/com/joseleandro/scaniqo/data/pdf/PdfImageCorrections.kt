package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Correções geométricas feitas em uma única reamostragem para evitar perdas sucessivas. */
internal object PdfImageCorrections {
    /**
     * Retifica os quatro cantos, endireita a captura e compensa uma curvatura manual.
     * Curvatura é uma aproximação parabólica para livros, não uma reconstrução 3D da folha.
     * A imagem original não é modificada; regiões fora da captura recebem papel branco.
     */
    suspend fun apply(
        pixels: IntArray, width: Int, height: Int, layout: PdfPageLayout
    ): IntArray {
        currentCoroutineContext().ensureActive()
        validateDimensions(pixels, width, height)
        if (pixels.isEmpty()) return pixels
        val selection = layout.perspective
        if (selection != null && !isValidPerspective(selection)) return pixels
        // Os cantos padrão não exigem reamostrar milhões de pixels idênticos.
        val perspective = selection?.takeUnless(::isIdentityPerspective)
        val degrees = layout.straightenDegrees.takeIf { it.isFinite() }?.coerceIn(-15f, 15f) ?: 0f
        // O controle −1..1 representa até 35% de deslocamento no centro da folha.
        val curvature = (layout.curvature.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f) * .35f
        if (perspective == null && degrees == 0f && curvature == 0f) return pixels
        val transform = perspective?.let(::squareToQuadrilateral)
        val result = IntArray(pixels.size)
        val angle = Math.toRadians(degrees.toDouble())
        val cosine = cos(angle)
        val sine = sin(angle)
        val centerX = (width - 1) / 2.0
        val centerY = (height - 1) / 2.0
        for (y in 0 until height) {
            currentCoroutineContext().ensureActive()
            if (y and 7 == 0) yield()
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                // Mapeamento inverso: cada pixel do destino busca seu ponto correspondente na fonte.
                var sourceX = cosine * dx + sine * dy + centerX
                var sourceY = -sine * dx + cosine * dy + centerY
                var u = if (width > 1) sourceX / (width - 1) else 0.0
                var v = if (height > 1) sourceY / (height - 1) else 0.0
                v += curvature * (1 - (2 * u - 1) * (2 * u - 1))
                if (transform != null) {
                    // Não revela conteúdo externo aos cantos escolhidos ao girar a folha recortada.
                    if (u < -.0001 || u > 1.0001 || v < -.0001 || v > 1.0001) {
                        result[y * width + x] = WHITE
                        continue
                    }
                    val denominator = transform[6] * u + transform[7] * v + 1
                    if (abs(denominator) < 1e-8) {
                        result[y * width + x] = WHITE
                        continue
                    }
                    val mappedU = (transform[0] * u + transform[1] * v + transform[2]) / denominator
                    v = (transform[3] * u + transform[4] * v + transform[5]) / denominator
                    u = mappedU
                }
                sourceX = u * (width - 1)
                sourceY = v * (height - 1)
                result[y * width + x] = sampleBilinear(pixels, width, height, sourceX, sourceY)
            }
        }
        return result
    }

    /** Comparação exata: pequenos ajustes reais continuam preservados na exportação. */
    fun isIdentityPerspective(value: PdfPerspective): Boolean =
        value.topLeft.x == 0f && value.topLeft.y == 0f &&
            value.topRight.x == 1f && value.topRight.y == 0f &&
            value.bottomRight.x == 1f && value.bottomRight.y == 1f &&
            value.bottomLeft.x == 0f && value.bottomLeft.y == 1f

    /** Rejeita cantos invertidos, cruzados, degenerados ou fora da captura. */
    fun isValidPerspective(value: PdfPerspective): Boolean {
        val points = listOf(value.topLeft, value.topRight, value.bottomRight, value.bottomLeft)
        if (points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) {
            return false
        }
        var doubleArea = 0f
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % 4]
            val c = points[(index + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (cross <= .001f) return false
            doubleArea += a.x * b.y - b.x * a.y
        }
        return doubleArea >= .04f
    }

    /**
     * Procura linhas horizontais de texto por projeções em uma miniatura limitada.
     * Retorna o ângulo corretivo, ou zero quando a amostra/ganho não permite uma sugestão segura.
     */
    suspend fun estimateStraightenDegrees(pixels: IntArray, width: Int, height: Int): Float {
        currentCoroutineContext().ensureActive()
        validateDimensions(pixels, width, height)
        if (width < 24 || height < 24) return 0f
        val step = maxOf(1, maxOf((width + 319) / 320, (height + 419) / 420))
        val sampleWidth = (width + step - 1) / step
        val sampleHeight = (height + step - 1) / step
        // Vetores primitivos evitam criar Pair e dois Float encaixotados para cada ponto de tinta.
        val maximumPoints = (sampleWidth * sampleHeight * .35f).toInt()
        val pointsX = FloatArray(maximumPoints)
        val pointsY = FloatArray(maximumPoints)
        var pointCount = 0
        for (y in 0 until height step step) {
            currentCoroutineContext().ensureActive()
            if ((y / step) and 15 == 0) yield()
            for (x in 0 until width step step) {
                val pixel = pixels[y * width + x]
                if (pixel ushr 24 < 128) continue
                val luminance = (77 * ((pixel ushr 16) and 255) +
                    150 * ((pixel ushr 8) and 255) + 29 * (pixel and 255)) ushr 8
                if (luminance < 150) {
                    if (pointCount == maximumPoints) return 0f
                    pointsX[pointCount] = x.toFloat() / step - sampleWidth / 2f
                    pointsY[pointCount] = y.toFloat() / step - sampleHeight / 2f
                    pointCount++
                }
            }
        }
        val coverage = pointCount.toFloat() / (sampleWidth * sampleHeight)
        if (pointCount < 32 || coverage !in .004f.. .35f) return 0f
        val bins = FloatArray(sampleHeight + sampleWidth + 4)
        val offset = sampleWidth / 2f + 1
        var bestScore = 0.0
        var baseline = 0.0
        var bestAngle = 0f
        // Resolução de meio grau; a quantidade de candidatos é fixa e a fonte permanece intocada.
        for (halfDegree in -24..24) {
            yield()
            bins.fill(0f)
            val angle = Math.toRadians(halfDegree * .5)
            val sine = sin(angle).toFloat()
            val cosine = cos(angle).toFloat()
            for (point in 0 until pointCount) {
                val projection = sine * pointsX[point] + cosine * pointsY[point] +
                    sampleHeight / 2f + offset
                val index = floor(projection).toInt()
                val remainder = projection - index
                if (index >= 0 && index + 1 < bins.size) {
                    bins[index] += 1 - remainder
                    bins[index + 1] += remainder
                }
            }
            val score = bins.sumOf { it.toDouble() * it }
            if (halfDegree == 0) baseline = score
            if (score > bestScore) {
                bestScore = score
                bestAngle = halfDegree * .5f
            }
        }
        return if (bestScore >= baseline * 1.08 && abs(bestAngle) >= .5f) bestAngle else 0f
    }

    /** Homografia que leva o retângulo normalizado aos quatro cantos da captura. */
    private fun squareToQuadrilateral(value: PdfPerspective): DoubleArray {
        val x0 = value.topLeft.x.toDouble()
        val y0 = value.topLeft.y.toDouble()
        val x1 = value.topRight.x.toDouble()
        val y1 = value.topRight.y.toDouble()
        val x2 = value.bottomRight.x.toDouble()
        val y2 = value.bottomRight.y.toDouble()
        val x3 = value.bottomLeft.x.toDouble()
        val y3 = value.bottomLeft.y.toDouble()
        val dx1 = x1 - x2
        val dx2 = x3 - x2
        val dx3 = x0 - x1 + x2 - x3
        val dy1 = y1 - y2
        val dy2 = y3 - y2
        val dy3 = y0 - y1 + y2 - y3
        val determinant = dx1 * dy2 - dx2 * dy1
        val g = if (abs(dx3) + abs(dy3) < 1e-8) 0.0 else
            (dx3 * dy2 - dx2 * dy3) / determinant
        val h = if (abs(dx3) + abs(dy3) < 1e-8) 0.0 else
            (dx1 * dy3 - dx3 * dy1) / determinant
        return doubleArrayOf(x1 - x0 + g * x1, x3 - x0 + h * x3, x0,
            y1 - y0 + g * y1, y3 - y0 + h * y3, y0, g, h)
    }

    private fun sampleBilinear(
        pixels: IntArray, width: Int, height: Int, x: Double, y: Double
    ): Int {
        if (!x.isFinite() || !y.isFinite() || x < -.0001 || y < -.0001 ||
            x > width - 1 + .0001 || y > height - 1 + .0001) return WHITE
        val boundedX = x.coerceIn(0.0, (width - 1).toDouble())
        val boundedY = y.coerceIn(0.0, (height - 1).toDouble())
        val x0 = boundedX.toInt()
        val y0 = boundedY.toInt()
        val x1 = minOf(x0 + 1, width - 1)
        val y1 = minOf(y0 + 1, height - 1)
        val fx = boundedX - x0
        val fy = boundedY - y0
        val topLeft = pixels[y0 * width + x0]
        if (fx == 0.0 && fy == 0.0) return topLeft
        val topRight = pixels[y0 * width + x1]
        val bottomLeft = pixels[y1 * width + x0]
        val bottomRight = pixels[y1 * width + x1]
        val oppositeX = 1 - fx
        val oppositeY = 1 - fy
        val opaque = (topLeft and topRight and bottomLeft and bottomRight) ushr 24 == 255
        var result = if (opaque) -0x1000000 else 0
        // Capturas do renderizador são opacas; o alfa não precisa ser interpolado nesses casos.
        for (channel in 0 until if (opaque) 3 else 4) {
            val shift = channel * 8
            val top = ((topLeft ushr shift) and 255) * oppositeX +
                ((topRight ushr shift) and 255) * fx
            val bottom = ((bottomLeft ushr shift) and 255) * oppositeX +
                ((bottomRight ushr shift) and 255) * fx
            result = result or ((top * oppositeY + bottom * fy).roundToInt().coerceIn(0, 255) shl shift)
        }
        return result
    }

    private fun validateDimensions(pixels: IntArray, width: Int, height: Int) {
        require(width >= 0 && height >= 0 && width.toLong() * height == pixels.size.toLong()) {
            "As dimensões não correspondem à captura."
        }
    }

    private const val WHITE = -1
}
