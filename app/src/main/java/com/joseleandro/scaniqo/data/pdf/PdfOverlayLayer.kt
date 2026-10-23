package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.joseleandro.scaniqo.core.graphics.ShapedTextLayout
import com.joseleandro.scaniqo.domain.model.PdfCheckmarkGeometry
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlin.math.min
import kotlin.math.sqrt

/** Preserva vetores quando possível e usa texto moldado pelo Android para outros alfabetos. */
internal object PdfOverlayLayer {
    fun draw(document: PDDocument, stream: PDPageContentStream, overlays: List<PdfOverlay>, pageNumber: Int, count: Int) {
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
                        stream.setLineWidth(PdfCheckmarkGeometry.strokeWidth(width, height))
                        stream.setLineCapStyle(1)
                        stream.setLineJoinStyle(1)
                        PdfCheckmarkGeometry.trace(
                            x, top, width, -height,
                            stream::moveTo, stream::lineTo, stream::curveTo,
                        )
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
                        drawText(document, stream, overlay, content, x, top, width, height)
                    }
                }
            } finally {
                stream.restoreGraphicsState()
            }
        }
    }

    private fun drawText(
        document: PDDocument, stream: PDPageContentStream, overlay: PdfOverlay, content: String,
        x: Float, top: Float, width: Float, height: Float,
    ) {
        val font = PDType1Font.HELVETICA
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val vectorText = try {
            font.encode(normalized.replace("\n", ""))
            true
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!vectorText) {
            drawShapedText(document, stream, overlay, normalized, x, top, width, height)
            return
        }
        if (overlay.wrapText || overlay.autoSizeText ||
            overlay.textHorizontalAlignment != PdfTextHorizontalAlignment.START ||
            overlay.textVerticalAlignment != PdfTextVerticalAlignment.TOP
        ) {
            drawAlignedVectorText(stream, overlay, normalized, x, top, width, height)
            return
        }
        val padding = if (overlay.kind == PdfOverlayKind.STAMP) 6f else 0f
        val lines = normalized.lines()
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

    /** Uses the preview's line breaks and placement while retaining searchable Latin text. */
    private fun drawAlignedVectorText(
        stream: PDPageContentStream, overlay: PdfOverlay, content: String,
        x: Float, top: Float, width: Float, height: Float,
    ) {
        val padding = if (overlay.kind == PdfOverlayKind.STAMP) min(6f, min(width, height) / 4f) else 0f
        val innerWidth = (width - padding * 2).coerceAtLeast(.001f)
        val innerHeight = (height - padding * 2).coerceAtLeast(.001f)
        val layout = ShapedTextLayout.create(
            text = content, fontSize = overlay.fontSize, color = overlay.color,
            maxWidth = innerWidth, maxHeight = innerHeight,
            wrap = overlay.wrapText, horizontalAlignment = overlay.textHorizontalAlignment,
        )
        val font = PDType1Font.HELVETICA
        val offsetX = layout.horizontalOffset(innerWidth)
        val offsetY = layout.verticalOffset(innerHeight, overlay.textVerticalAlignment)
        stream.beginText()
        stream.setFont(font, layout.fontSize)
        layout.lines.forEach { line ->
            if (line.text.isNotEmpty()) {
                val vectorWidth = font.getStringWidth(line.text) / 1000f * layout.fontSize
                // Android and Helvetica have different advances; match the measured line width.
                stream.setHorizontalScaling(if (vectorWidth > 0f) line.width / vectorWidth * 100f else 100f)
                stream.setTextMatrix(Matrix.getTranslateInstance(
                    x + padding + offsetX + line.left,
                    top - padding - offsetY - line.baseline,
                ))
                stream.showText(line.text)
            }
        }
        stream.endText()
    }

    private fun drawShapedText(
        document: PDDocument, stream: PDPageContentStream, overlay: PdfOverlay, content: String,
        x: Float, top: Float, width: Float, height: Float,
    ) {
        // At most 2 million pixels (8 MB ARGB) per element, released before the next one.
        val rasterScale = min(3f, min(
            2048f / maxOf(width, height),
            sqrt(2_000_000f / (width * height)),
        ))
        val bitmap = Bitmap.createBitmap(
            (width * rasterScale).toInt().coerceAtLeast(1),
            (height * rasterScale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            val padding = if (overlay.kind == PdfOverlayKind.STAMP) {
                min(6f, min(width, height) / 4f)
            } else 0f
            val innerWidth = (width - padding * 2).coerceAtLeast(.001f)
            val innerHeight = (height - padding * 2).coerceAtLeast(.001f)
            val layout = ShapedTextLayout.create(
                text = content, fontSize = overlay.fontSize,
                // The PDF graphics state applies opacity once, to the whole shaped image.
                color = overlay.color or (0xFF shl 24),
                maxWidth = innerWidth, maxHeight = innerHeight,
                wrap = overlay.wrapText, horizontalAlignment = overlay.textHorizontalAlignment,
            )
            val canvas = Canvas(bitmap)
            canvas.scale(bitmap.width / width, bitmap.height / height)
            layout.draw(
                canvas, padding, padding, availableWidth = innerWidth, availableHeight = innerHeight,
                verticalAlignment = overlay.textVerticalAlignment,
            )
            val image = LosslessFactory.createFromImage(document, bitmap)
            stream.drawImage(image, x, top - height, width, height)
        } finally {
            bitmap.recycle()
        }
    }
}
