package com.joseleandro.scaniqo.ui.signatures.crop

import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import kotlin.math.abs
import kotlin.math.hypot

internal enum class SignatureCropHandle {
    TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, MOVE,
}

internal object SignatureCropGeometry {
    val fullImage = PdfSignatureCrop(0f, 0f, 1f, 1f)
    val minimumSize = .04f

    fun drag(
        crop: PdfSignatureCrop,
        handle: SignatureCropHandle,
        deltaX: Float,
        deltaY: Float,
    ): PdfSignatureCrop {
        val source = crop.takeIf { it.isValid } ?: fullImage
        if (!deltaX.isFinite() || !deltaY.isFinite()) return source
        if (handle == SignatureCropHandle.MOVE) {
            val x = deltaX.coerceIn(-source.left, 1f - source.right)
            val y = deltaY.coerceIn(-source.top, 1f - source.bottom)
            return PdfSignatureCrop(source.left + x, source.top + y, source.right + x, source.bottom + y)
        }
        val minWidth = minOf(minimumSize, source.right - source.left)
        val minHeight = minOf(minimumSize, source.bottom - source.top)
        val left = if (handle in leftHandles) {
            (source.left + deltaX).coerceIn(0f, source.right - minWidth)
        } else source.left
        val right = if (handle in rightHandles) {
            (source.right + deltaX).coerceIn(source.left + minWidth, 1f)
        } else source.right
        val top = if (handle in topHandles) {
            (source.top + deltaY).coerceIn(0f, source.bottom - minHeight)
        } else source.top
        val bottom = if (handle in bottomHandles) {
            (source.bottom + deltaY).coerceIn(source.top + minHeight, 1f)
        } else source.bottom
        return PdfSignatureCrop(left, top, right, bottom)
    }

    fun handleAt(
        crop: PdfSignatureCrop,
        x: Float,
        y: Float,
        imageWidth: Float,
        imageHeight: Float,
        touchRadius: Float,
    ): SignatureCropHandle? {
        if (!crop.isValid || !listOf(x, y, imageWidth, imageHeight, touchRadius).all { it.isFinite() } ||
            imageWidth <= 0f || imageHeight <= 0f || touchRadius <= 0f
        ) return null
        val left = crop.left * imageWidth
        val right = crop.right * imageWidth
        val top = crop.top * imageHeight
        val bottom = crop.bottom * imageHeight
        val corners = listOf(
            Triple(SignatureCropHandle.TOP_LEFT, left, top),
            Triple(SignatureCropHandle.TOP_RIGHT, right, top),
            Triple(SignatureCropHandle.BOTTOM_RIGHT, right, bottom),
            Triple(SignatureCropHandle.BOTTOM_LEFT, left, bottom),
        )
        corners.minByOrNull { hypot(x - it.second, y - it.third) }
            ?.takeIf { hypot(x - it.second, y - it.third) <= touchRadius }
            ?.let { return it.first }
        val edges = buildList {
            if (x in left..right) {
                add(SignatureCropHandle.TOP to abs(y - top))
                add(SignatureCropHandle.BOTTOM to abs(y - bottom))
            }
            if (y in top..bottom) {
                add(SignatureCropHandle.LEFT to abs(x - left))
                add(SignatureCropHandle.RIGHT to abs(x - right))
            }
        }
        edges.minByOrNull { it.second }?.takeIf { it.second <= touchRadius }?.let { return it.first }
        return SignatureCropHandle.MOVE.takeIf { x in left..right && y in top..bottom }
    }

    private val leftHandles = setOf(SignatureCropHandle.LEFT, SignatureCropHandle.TOP_LEFT, SignatureCropHandle.BOTTOM_LEFT)
    private val rightHandles = setOf(SignatureCropHandle.RIGHT, SignatureCropHandle.TOP_RIGHT, SignatureCropHandle.BOTTOM_RIGHT)
    private val topHandles = setOf(SignatureCropHandle.TOP, SignatureCropHandle.TOP_LEFT, SignatureCropHandle.TOP_RIGHT)
    private val bottomHandles = setOf(SignatureCropHandle.BOTTOM, SignatureCropHandle.BOTTOM_LEFT, SignatureCropHandle.BOTTOM_RIGHT)
}
