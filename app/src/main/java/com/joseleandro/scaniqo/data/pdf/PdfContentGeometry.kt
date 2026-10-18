package com.joseleandro.scaniqo.data.pdf

import kotlin.math.abs

/** Geometria dos operadores PDF em coordenadas da página, inclusive o eixo Y invertido do Skia. */
internal data class PdfContentTransform(
    val a: Float = 1f, val b: Float = 0f, val c: Float = 0f,
    val d: Float = 1f, val e: Float = 0f, val f: Float = 0f,
) {
    fun then(next: PdfContentTransform) = PdfContentTransform(
        a * next.a + c * next.b, b * next.a + d * next.b,
        a * next.c + c * next.d, b * next.c + d * next.d,
        a * next.e + c * next.f + e, b * next.e + d * next.f + f,
    )

    fun point(x: Float, y: Float) = Pair(a * x + c * y + e, b * x + d * y + f)

    fun box(x: Float, y: Float, width: Float, height: Float): PdfContentBox? {
        if (!listOf(a, b, c, d, e, f, x, y, width, height).all(Float::isFinite)) return null
        if (!((abs(b) < .0001f && abs(c) < .0001f) || (abs(a) < .0001f && abs(d) < .0001f))) return null
        val first = point(x, y)
        val last = point(x + width, y + height)
        return PdfContentBox(minOf(first.first, last.first), minOf(first.second, last.second),
            maxOf(first.first, last.first), maxOf(first.second, last.second)).takeIf { it.valid() }
    }
}

internal data class PdfContentBox(val left: Float, val bottom: Float, val right: Float, val top: Float) {
    val width get() = right - left
    val height get() = top - bottom
    fun valid() = listOf(left, bottom, right, top).all(Float::isFinite) && width > 0f && height > 0f
    fun intersect(other: PdfContentBox): PdfContentBox? = PdfContentBox(
        maxOf(left, other.left), maxOf(bottom, other.bottom),
        minOf(right, other.right), minOf(top, other.top),
    ).takeIf { it.valid() }
    fun covers(other: PdfContentBox) = left <= other.left && bottom <= other.bottom &&
        right >= other.right && top >= other.top
}

/** Reconhece tanto re quanto m/l/l/l/h; curvas e múltiplos subcaminhos não viram retângulos. */
internal class PdfRectangularPath {
    private val points = mutableListOf<Pair<Float, Float>>()
    private var complex = false

    fun move(point: Pair<Float, Float>) {
        if (points.isNotEmpty()) complex = true
        points += point
    }

    fun line(point: Pair<Float, Float>) {
        if (points.isEmpty() || points.size >= 5) complex = true
        if (points.size < 5) points += point
    }

    fun rectangle(box: PdfContentBox?) {
        if (points.isNotEmpty() || box == null) {
            complex = true
            return
        }
        points += box.left to box.bottom
        points += box.right to box.bottom
        points += box.right to box.top
        points += box.left to box.top
    }

    fun curve() { complex = true }

    fun bounds(): PdfContentBox? {
        if (complex || points.any { !it.first.isFinite() || !it.second.isFinite() }) return null
        val vertices = if (points.size == 5 && points.first() == points.last()) points.dropLast(1) else points
        if (vertices.size != 4 || vertices.distinct().size != 4) return null
        for (index in vertices.indices) {
            val from = vertices[index]
            val to = vertices[(index + 1) % 4]
            if (abs(from.first - to.first) > .0001f && abs(from.second - to.second) > .0001f) return null
        }
        val result = PdfContentBox(vertices.minOf { it.first }, vertices.minOf { it.second },
            vertices.maxOf { it.first }, vertices.maxOf { it.second })
        return result.takeIf { it.valid() }
    }

    fun clear() { points.clear(); complex = false }
}
