package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter

/** Retorna false sem modificar pixels quando a operação não puder ser acelerada. */
internal fun interface PdfFilterAcceleration {
    suspend fun tryApply(
        pixels: IntArray,
        filter: PdfImageFilter,
        width: Int,
        height: Int,
        intensity: Float,
    ): Boolean
}

/** A preferência é consultada a cada operação; desligar não exige reabrir o editor. */
internal class PdfImageFilterProcessor(
    private val advancedProcessingEnabled: () -> Boolean = { false },
    private val acceleration: PdfFilterAcceleration = GlesPdfFilterAcceleration(),
) {
    suspend fun apply(
        pixels: IntArray, filter: PdfImageFilter, width: Int, height: Int, intensity: Float,
    ) = PdfImageFilters.apply(
        pixels, filter, width, height, intensity,
        acceleration = acceleration.takeIf { advancedProcessingEnabled() },
    )
}
