package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint

/** A interface edita somente a captura; a receita mantém coordenadas da página fonte. */
internal object DocumentCaptureCoordinates {
    fun localCrop(pageCrop: PdfCropBounds?, imageBounds: PdfCropBounds): PdfCropBounds {
        if (pageCrop == null || !pageCrop.isValid() || !imageBounds.isValid()) return PdfCropBounds()
        val width = imageBounds.right - imageBounds.left
        val height = imageBounds.bottom - imageBounds.top
        return PdfCropBounds(
            ((pageCrop.left - imageBounds.left) / width).coerceIn(0f, 1f),
            ((pageCrop.top - imageBounds.top) / height).coerceIn(0f, 1f),
            ((pageCrop.right - imageBounds.left) / width).coerceIn(0f, 1f),
            ((pageCrop.bottom - imageBounds.top) / height).coerceIn(0f, 1f),
        ).takeIf(PdfCropBounds::isValid) ?: PdfCropBounds()
    }

    fun pageCrop(localCrop: PdfCropBounds, imageBounds: PdfCropBounds): PdfCropBounds? {
        if (localCrop.isIdentity()) return null
        if (!localCrop.isValid() || !imageBounds.isValid()) return null
        val topLeft = pagePoint(PdfPoint(localCrop.left, localCrop.top), imageBounds)
        val bottomRight = pagePoint(PdfPoint(localCrop.right, localCrop.bottom), imageBounds)
        return PdfCropBounds(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    fun localMask(pageMask: PdfPerspective?, imageBounds: PdfCropBounds): PdfPerspective {
        if (pageMask == null || !imageBounds.isValid()) return PdfPerspective()
        return PdfPerspective(
            localPoint(pageMask.topLeft, imageBounds),
            localPoint(pageMask.topRight, imageBounds),
            localPoint(pageMask.bottomRight, imageBounds),
            localPoint(pageMask.bottomLeft, imageBounds),
        ).takeIf { it.isValidSelection() } ?: PdfPerspective()
    }

    fun pageMask(localMask: PdfPerspective, imageBounds: PdfCropBounds): PdfPerspective? {
        if (localMask == PdfPerspective()) return null
        if (!localMask.isValidSelection() || !imageBounds.isValid()) return null
        return PdfPerspective(
            pagePoint(localMask.topLeft, imageBounds),
            pagePoint(localMask.topRight, imageBounds),
            pagePoint(localMask.bottomRight, imageBounds),
            pagePoint(localMask.bottomLeft, imageBounds),
        )
    }

    private fun localPoint(point: PdfPoint, bounds: PdfCropBounds) = PdfPoint(
        ((point.x - bounds.left) / (bounds.right - bounds.left)).coerceIn(0f, 1f),
        ((point.y - bounds.top) / (bounds.bottom - bounds.top)).coerceIn(0f, 1f),
    )

    private fun pagePoint(point: PdfPoint, bounds: PdfCropBounds) = PdfPoint(
        bounds.left + point.x * (bounds.right - bounds.left),
        bounds.top + point.y * (bounds.bottom - bounds.top),
    )
}
