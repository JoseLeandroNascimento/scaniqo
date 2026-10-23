package com.joseleandro.scaniqo.ui.documentEditor.util

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout

/** Abrir uma ferramenta não altera a geometria. O gesto de recorte decide quando remover a perspectiva. */
internal fun PdfPageLayout.withCorrectionAdjustmentsFrom(draft: PdfPageLayout): PdfPageLayout = copy(
    crop = draft.crop,
    backgroundMask = draft.backgroundMask,
    perspective = draft.perspective,
    straightenDegrees = draft.straightenDegrees,
    curvature = draft.curvature,
    rasterizeText = draft.rasterizeText ||
        (rasterizeText && imageFilter != PdfImageFilter.ORIGINAL && filterIntensity > 0f),
)
