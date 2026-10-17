package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import java.io.File

/** Fontes da sessão são imutáveis; tamanho e data também invalidam substituições externas. */
internal data class PdfSourceStamp(val path: String, val size: Long, val modifiedAt: Long) {
    companion object {
        fun from(file: File) = PdfSourceStamp(file.canonicalPath, file.length(), file.lastModified())
    }
}

/** Somente propriedades que alteram os pixels: mover/girar a folha não reaplica o filtro. */
internal data class PdfRasterKey(
    val source: PdfSourceStamp,
    val pageIndex: Int,
    val filter: PdfImageFilter,
    val intensity: Float,
    val straightenDegrees: Float,
    val perspective: PdfPerspective?,
    val curvature: Float,
    val maxSide: Int = 1800,
    val jpegQuality: Float = .9f,
) {
    companion object {
        fun from(
            file: File,
            page: PdfPageSpec,
            maxSide: Int = 1800,
            jpegQuality: Float = .9f,
        ) = PdfRasterKey(
            PdfSourceStamp.from(file), page.sourcePageIndex,
            page.layout.imageFilter, page.layout.filterIntensity,
            page.layout.straightenDegrees, page.layout.perspective, page.layout.curvature, maxSide, jpegQuality,
        )
    }
}

/** OCR, senha e compressão não alteram a composição visual da prévia. */
internal data class PdfPreviewKey(
    val pages: List<PdfPageSpec>,
    val showPageNumbers: Boolean,
) {
    companion object {
        fun from(workspace: PdfWorkspace) = PdfPreviewKey(
            workspace.pages.map { it.copy(signatures = it.signatures.toList()) },
            workspace.showPageNumbers,
        )
    }
}
