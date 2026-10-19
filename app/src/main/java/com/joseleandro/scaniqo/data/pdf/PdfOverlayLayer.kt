package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlin.math.min

/** Camada vetorial exportável; a receita mantém os elementos editáveis dentro do app. */
internal object PdfOverlayLayer {
    fun draw(stream: PDPageContentStream, overlays: List<PdfOverlay>, pageNumber: Int, count: Int) {
        require(overlays.size <= 100 && overlays.all(PdfOverlay::isValid))
        overlays.forEach { overlay ->
            stream.saveGraphicsState()
            try {
                val alpha = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = overlay.opacity
                    strokingAlphaConstant = overlay.opacity
                }
                stream.setGraphicsStateParameters(alpha)
                val red = ((overlay.color ushr 16) and 255) / 255f
                val green = ((overlay.color ushr 8) and 255) / 255f
                val blue = (overlay.color and 255) / 255f
                stream.setNonStrokingColor(red, green, blue)
                stream.setStrokingColor(red, green, blue)
                val x = overlay.x * PdfPageGeometry.WIDTH
                val top = (1f - overlay.y) * PdfPageGeometry.HEIGHT
                val width = overlay.width * PdfPageGeometry.WIDTH
                val height = overlay.height * PdfPageGeometry.HEIGHT
                stream.setLineWidth(2f)
                when (overlay.kind) {
                    PdfOverlayKind.HIGHLIGHT -> {
                        stream.addRect(x, top - height, width, height)
                        stream.fill()
                    }
                    PdfOverlayKind.UNDERLINE -> {
                        stream.moveTo(x, top - height)
                        stream.lineTo(x + width, top - height)
                        stream.stroke()
                    }
                    PdfOverlayKind.CHECKMARK -> {
                        stream.moveTo(x, top - height * .5f)
                        stream.lineTo(x + width * .35f, top - height)
                        stream.lineTo(x + width, top)
                        stream.stroke()
                    }
                    PdfOverlayKind.ARROW -> {
                        stream.moveTo(x, top - height * .5f)
                        stream.lineTo(x + width, top - height * .5f)
                        stream.lineTo(x + width * .8f, top - height * .15f)
                        stream.moveTo(x + width, top - height * .5f)
                        stream.lineTo(x + width * .8f, top - height * .85f)
                        stream.stroke()
                    }
                    PdfOverlayKind.DRAWING -> {
                        overlay.points.firstOrNull()?.let { point ->
                            stream.moveTo(point.x * PdfPageGeometry.WIDTH, (1f - point.y) * PdfPageGeometry.HEIGHT)
                            overlay.points.drop(1).forEach {
                                stream.lineTo(it.x * PdfPageGeometry.WIDTH, (1f - it.y) * PdfPageGeometry.HEIGHT)
                            }
                            stream.stroke()
                        }
                    }
                    else -> {
                        if (overlay.kind == PdfOverlayKind.STAMP) {
                            stream.addRect(x, top - height, width, height)
                            stream.stroke()
                        }
                        val content = overlay.text.replace("{pagina}", pageNumber.toString())
                            .replace("{total}", count.toString())
                        drawText(stream, overlay, content, x, top, width, height)
                    }
                }
            } finally {
                stream.restoreGraphicsState()
            }
        }
    }

    private fun drawText(
        stream: PDPageContentStream, overlay: PdfOverlay, content: String,
        x: Float, top: Float, width: Float, height: Float,
    ) {
        val font = PDType1Font.HELVETICA
        val clean = content.map { char ->
            if (char == '\n') char else try {
                font.encode(char.toString())
                char
            } catch (_: IllegalArgumentException) { '?' }
        }.joinToString("")
        val padding = if (overlay.kind == PdfOverlayKind.STAMP) 6f else 0f
        val lines = clean.lines()
        val maxWidth = lines.maxOfOrNull { font.getStringWidth(it) / 1000f } ?: 0f
        val size = min(overlay.fontSize, min(
            (width - padding * 2).coerceAtLeast(1f) / maxWidth.coerceAtLeast(.1f),
            (height - padding * 2).coerceAtLeast(1f) / (lines.size * 1.2f),
        )).coerceAtLeast(1f)
        stream.beginText()
        stream.setFont(font, size)
        stream.setLeading(size * 1.2f)
        stream.newLineAtOffset(x + padding, top - padding - size)
        lines.forEachIndexed { index, line ->
            if (index > 0) stream.newLine()
            stream.showText(line)
        }
        stream.endText()
    }
}
