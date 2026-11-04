package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Todos os pontos estão em pixels do palco, incluindo a margem em volta da folha. */
internal data class DocumentSelectionGeometry(
    val corners: List<PdfPoint>,
    val cornerHandles: List<PdfPoint>,
    val topMiddle: PdfPoint,
    val rotationHandle: PdfPoint,
)

internal typealias SignatureSelectionGeometry = DocumentSelectionGeometry

internal fun signatureSelectionGeometry(
    placement: PdfSignaturePlacement,
    pageLeft: Float,
    pageTop: Float,
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    targetSize: Float,
    targetGap: Float,
    stemLength: Float,
): SignatureSelectionGeometry = documentSelectionGeometry(
    placement.x, placement.y, placement.width, placement.height, placement.rotationDegrees,
    pageLeft, pageTop, pageWidth, pageHeight, viewportWidth, viewportHeight, targetSize, targetGap, stemLength,
)

internal fun documentSelectionGeometry(
    x: Float, y: Float, width: Float, height: Float, rotationDegrees: Float,
    pageLeft: Float, pageTop: Float, pageWidth: Float, pageHeight: Float,
    viewportWidth: Float, viewportHeight: Float,
    targetSize: Float, targetGap: Float, stemLength: Float,
): DocumentSelectionGeometry {
    val radians = Math.toRadians(rotationDegrees.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    val halfWidth = width * pageWidth / 2f
    val halfHeight = height * pageHeight / 2f
    val centerX = pageLeft + (x + width / 2f) * pageWidth
    val centerY = pageTop + (y + height / 2f) * pageHeight
    fun point(x: Float, y: Float) = PdfPoint(
        centerX + x * cosine - y * sine,
        centerY + x * sine + y * cosine,
    )
    val corners = listOf(
        point(-halfWidth, -halfHeight), point(halfWidth, -halfHeight),
        point(halfWidth, halfHeight), point(-halfWidth, halfHeight),
    )
    // Uma assinatura fina conserva seu tamanho; somente as alças se afastam.
    // sqrt(2) também separa os alvos quadrados quando a seleção está a 45 graus.
    val minimumSpan = (targetSize + targetGap) * sqrt(2f)
    val handleHalfWidth = maxOf(halfWidth, minimumSpan / 2f)
    val handleHalfHeight = maxOf(halfHeight, minimumSpan / 2f)
    val preferred = listOf(
        point(-handleHalfWidth, -handleHalfHeight), point(handleHalfWidth, -handleHalfHeight),
        point(handleHalfWidth, handleHalfHeight), point(-handleHalfWidth, handleHalfHeight),
        point(0f, -handleHalfHeight - stemLength),
    )
    val handles = separateSignatureHandles(preferred, viewportWidth, viewportHeight, targetSize, targetGap)
    return SignatureSelectionGeometry(corners, handles.take(4), point(0f, -halfHeight), handles[4])
}

internal fun selectionRotationDelta(center: PdfPoint, start: PdfPoint, pointer: PdfPoint): Float {
    val startAngle = atan2(start.y - center.y, start.x - center.x)
    val endAngle = atan2(pointer.y - center.y, pointer.x - center.x)
    return Math.toDegrees((endAngle - startAngle).toDouble()).toFloat()
}

internal fun signatureContextToolbarCenter(
    geometry: SignatureSelectionGeometry,
    viewportWidth: Float,
    viewportHeight: Float,
    toolbarWidth: Float,
    toolbarHeight: Float,
    handleTargetSize: Float,
    targetGap: Float,
): PdfPoint {
    val halfWidth = minOf(toolbarWidth, viewportWidth) / 2f
    val halfHeight = minOf(toolbarHeight, viewportHeight) / 2f
    val left = halfWidth
    val right = maxOf(left, viewportWidth - halfWidth)
    val top = halfHeight
    val bottom = maxOf(top, viewportHeight - halfHeight)
    val boundsLeft = geometry.corners.minOf { it.x }
    val boundsRight = geometry.corners.maxOf { it.x }
    val boundsTop = geometry.corners.minOf { it.y }
    val boundsBottom = geometry.corners.maxOf { it.y }
    val centerX = (boundsLeft + boundsRight) / 2f
    val centerY = (boundsTop + boundsBottom) / 2f
    val below = boundsBottom + halfHeight + targetGap
    val above = boundsTop - halfHeight - targetGap
    val spacingX = halfWidth + handleTargetSize / 2f + targetGap
    val spacingY = halfHeight + handleTargetSize / 2f + targetGap
    val handles = geometry.cornerHandles + geometry.rotationHandle
    val xs = mutableSetOf(centerX, boundsLeft - halfWidth - targetGap, boundsRight + halfWidth + targetGap, left, right)
    val ys = mutableSetOf(above, below, centerY, top, bottom)
    handles.forEach { handle ->
        xs += handle.x - spacingX
        xs += handle.x + spacingX
        ys += handle.y - spacingY
        ys += handle.y + spacingY
    }
    val candidatesX = xs.map { it.coerceIn(left, right) }.distinct()
    val candidatesY = ys.map { it.coerceIn(top, bottom) }.distinct()
    var bestFree: PdfPoint? = null
    var bestFreeRank = Int.MAX_VALUE
    var bestFreeDistance = Float.MAX_VALUE
    var bestHandlesFree: PdfPoint? = null
    var leastContentOverlap = Float.MAX_VALUE
    var bestHandlesFreeDistance = Float.MAX_VALUE
    var bestFallback = PdfPoint(centerX.coerceIn(left, right), below.coerceIn(top, bottom))
    var leastOverlap = Float.MAX_VALUE
    var bestFallbackDistance = Float.MAX_VALUE
    for (x in candidatesX) {
        for (y in candidatesY) {
            val candidate = PdfPoint(x, y)
            val handleOverlap = handles.sumOf { handle ->
                val width = (spacingX - abs(x - handle.x)).coerceAtLeast(0f)
                val height = (spacingY - abs(y - handle.y)).coerceAtLeast(0f)
                (width * height).toDouble()
            }.toFloat()
            val contentOverlap =
                (minOf(x + halfWidth + targetGap, boundsRight) - maxOf(x - halfWidth - targetGap, boundsLeft)).coerceAtLeast(0f) *
                (minOf(y + halfHeight + targetGap, boundsBottom) - maxOf(y - halfHeight - targetGap, boundsTop)).coerceAtLeast(0f)
            val verticalDistance = minOf(abs(y - above), abs(y - below))
            val distance = (x - centerX) * (x - centerX) + verticalDistance * verticalDistance
            if (handleOverlap < .01f) {
                val rank = if (y <= above + .01f || y >= below - .01f) 0 else 1
                if (contentOverlap < .01f &&
                    (rank < bestFreeRank || (rank == bestFreeRank && distance < bestFreeDistance))
                ) {
                    bestFree = candidate
                    bestFreeRank = rank
                    bestFreeDistance = distance
                }
                if (contentOverlap < leastContentOverlap ||
                    (contentOverlap == leastContentOverlap && distance < bestHandlesFreeDistance)
                ) {
                    bestHandlesFree = candidate
                    leastContentOverlap = contentOverlap
                    bestHandlesFreeDistance = distance
                }
            }
            val overlap = handleOverlap * 4f + contentOverlap
            if (overlap < leastOverlap || (overlap == leastOverlap && distance < bestFallbackDistance)) {
                bestFallback = candidate
                leastOverlap = overlap
                bestFallbackDistance = distance
            }
        }
    }
    return bestFree ?: bestHandlesFree ?: bestFallback
}

/** Mantém alvos inteiros no palco e procura a posição livre mais próxima antes de afastá-los. */
private fun separateSignatureHandles(
    preferred: List<PdfPoint>,
    viewportWidth: Float,
    viewportHeight: Float,
    targetSize: Float,
    gap: Float,
): List<PdfPoint> {
    val half = targetSize / 2f
    val left = minOf(half, viewportWidth / 2f)
    val right = maxOf(left, viewportWidth - half)
    val top = minOf(half, viewportHeight / 2f)
    val bottom = maxOf(top, viewportHeight - half)
    val spacing = targetSize + gap
    val placed = mutableListOf<PdfPoint>()
    for (desired in preferred) {
        val x = desired.x.coerceIn(left, right)
        val y = desired.y.coerceIn(top, bottom)
        val xs = mutableListOf(left, right, x)
        val ys = mutableListOf(top, bottom, y)
        var gridX = left
        while (gridX <= right) { xs += gridX; gridX += spacing }
        var gridY = top
        while (gridY <= bottom) { ys += gridY; gridY += spacing }
        placed.forEach {
            xs += (it.x - spacing).coerceIn(left, right)
            xs += (it.x + spacing).coerceIn(left, right)
            ys += (it.y - spacing).coerceIn(top, bottom)
            ys += (it.y + spacing).coerceIn(top, bottom)
        }
        val candidates = xs.distinct().flatMap { candidateX -> ys.distinct().map { PdfPoint(candidateX, it) } }
        fun distance(point: PdfPoint): Float =
            (point.x - desired.x) * (point.x - desired.x) + (point.y - desired.y) * (point.y - desired.y)
        val free = candidates.filter { candidate ->
            placed.all { abs(candidate.x - it.x) >= spacing - .01f || abs(candidate.y - it.y) >= spacing - .01f }
        }.minByOrNull(::distance)
        // Se a área nem comporta cinco alvos, conserva os círculos visíveis e maximiza a separação.
        val next = free ?: candidates.maxByOrNull { candidate ->
            placed.minOfOrNull { (candidate.x - it.x) * (candidate.x - it.x) + (candidate.y - it.y) * (candidate.y - it.y) }
                ?: Float.MAX_VALUE
        } ?: PdfPoint(x, y)
        placed += next
    }
    val collides = placed.indices.any { first ->
        (first + 1 until placed.size).any { second ->
            abs(placed[first].x - placed[second].x) < spacing - .01f &&
                abs(placed[first].y - placed[second].y) < spacing - .01f
        }
    }
    if (!collides) return placed
    // A escolha local pode ocupar o centro e bloquear a quinta alça. Uma grade livre
    // recupera a separação em palcos compactos que ainda comportam os cinco alvos.
    val grid = mutableListOf<PdfPoint>()
    var gridY = top
    while (gridY <= bottom) {
        var gridX = left
        while (gridX <= right) {
            grid += PdfPoint(gridX, gridY)
            gridX += spacing
        }
        gridY += spacing
    }
    if (grid.size < preferred.size) return placed
    return preferred.map { desired ->
        val closest = grid.minBy { (it.x - desired.x) * (it.x - desired.x) + (it.y - desired.y) * (it.y - desired.y) }
        grid.remove(closest)
        closest
    }
}
