package com.joseleandro.scaniqo.core.graphics

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import kotlin.math.ceil
import kotlin.math.min

internal data class ShapedTextLine(
    val text: String,
    val left: Float,
    val baseline: Float,
    val width: Float,
)

/**
 * Shapes whole paragraphs, including bidi runs and fallback fonts, before fitting them.
 * Layout is measured at a fixed scale so the preview and PDF use the same line metrics.
 */
internal class ShapedTextLayout private constructor(
    internal val layout: StaticLayout,
    private val scale: Float,
    val isRtl: Boolean,
    private val horizontalAlignment: PdfTextHorizontalAlignment,
    private val leftBound: Float,
    private val measuredWidth: Float,
) {
    /** Box measured at the requested font size, before a height constraint scales it down. */
    val naturalWidth: Float get() = measuredWidth / METRIC_SCALE
    val naturalHeight: Float get() = layout.height / METRIC_SCALE
    val width: Float get() = measuredWidth * scale
    val height: Float get() = layout.height * scale
    val fontSize: Float get() = layout.paint.textSize * scale
    val lineAdvance: Float get() = if (layout.lineCount > 1) {
        (layout.getLineBaseline(1) - layout.getLineBaseline(0)) * scale
    } else (layout.paint.fontMetrics.descent - layout.paint.fontMetrics.ascent) * scale

    val lines: List<ShapedTextLine> get() = (0 until layout.lineCount).map { index ->
        ShapedTextLine(
            text = layout.text.subSequence(layout.getLineStart(index), layout.getLineEnd(index))
                .toString().trimEnd('\n', '\r'),
            left = (layout.getLineLeft(index) - leftBound) * scale,
            baseline = layout.getLineBaseline(index) * scale,
            width = layout.getLineWidth(index) * scale,
        )
    }

    fun horizontalOffset(availableWidth: Float): Float {
        val space = (availableWidth - width).coerceAtLeast(0f)
        return when (horizontalAlignment) {
            PdfTextHorizontalAlignment.START -> if (isRtl) space else 0f
            PdfTextHorizontalAlignment.CENTER -> space / 2f
            PdfTextHorizontalAlignment.END -> if (isRtl) 0f else space
        }
    }

    fun verticalOffset(availableHeight: Float, alignment: PdfTextVerticalAlignment): Float {
        val space = (availableHeight - height).coerceAtLeast(0f)
        return when (alignment) {
            PdfTextVerticalAlignment.TOP -> 0f
            PdfTextVerticalAlignment.CENTER -> space / 2f
            PdfTextVerticalAlignment.BOTTOM -> space
        }
    }

    fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
        availableWidth: Float = width,
        availableHeight: Float = height,
        verticalAlignment: PdfTextVerticalAlignment = PdfTextVerticalAlignment.TOP,
    ) {
        val save = canvas.save()
        try {
            canvas.translate(
                left + horizontalOffset(availableWidth),
                top + verticalOffset(availableHeight, verticalAlignment),
            )
            canvas.scale(scale, scale)
            canvas.translate(-leftBound, 0f)
            layout.draw(canvas)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    companion object {
        private const val METRIC_SCALE = 4f

        fun create(
            text: String,
            fontSize: Float,
            color: Int,
            maxWidth: Float,
            maxHeight: Float,
            typeface: Typeface = Typeface.DEFAULT,
            wrap: Boolean = false,
            horizontalAlignment: PdfTextHorizontalAlignment = PdfTextHorizontalAlignment.START,
        ): ShapedTextLayout {
            require(text.length <= 2000)
            require(fontSize.isFinite() && fontSize > 0f && fontSize <= 160f)
            require(maxWidth.isFinite() && maxWidth > 0f && maxHeight.isFinite() && maxHeight > 0f)
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.color = color
                this.typeface = typeface
                textSize = fontSize * METRIC_SCALE
            }
            // All callers shape in PDF points; preview density cannot change line breaks.
            val naturalWidth = ceil(Layout.getDesiredWidth(normalized, paint).toDouble())
                .toInt().coerceAtLeast(1) + 1
            val layoutWidth = if (wrap) {
                minOf(naturalWidth, (maxWidth * METRIC_SCALE + .001f).toInt().coerceAtLeast(1))
            } else naturalWidth
            val alignment = when (horizontalAlignment) {
                PdfTextHorizontalAlignment.START -> Layout.Alignment.ALIGN_NORMAL
                PdfTextHorizontalAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                PdfTextHorizontalAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
            }
            val builder = StaticLayout.Builder.obtain(normalized, 0, normalized.length, paint, layoutWidth)
                .setAlignment(alignment)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setIncludePad(true)
                .setLineSpacing(0f, 1.2f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUseLineSpacingFromFallbacks(true)
            val layout = builder.build()
            // A glyph wider than the wrapping column still needs to fit instead of being clipped.
            val leftBound = if (wrap) minOf(0f, (0 until layout.lineCount).minOf(layout::getLineLeft)) else 0f
            val rightBound = if (wrap) {
                maxOf(layout.width.toFloat(), (0 until layout.lineCount).maxOf(layout::getLineRight))
            } else layout.width.toFloat()
            val measuredWidth = (rightBound - leftBound).coerceAtLeast(1f)
            val scale = min(1f / METRIC_SCALE, min(
                maxWidth / measuredWidth,
                maxHeight / layout.height.coerceAtLeast(1),
            ))
            return ShapedTextLayout(
                layout = layout,
                scale = scale,
                isRtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(normalized, 0, normalized.length),
                horizontalAlignment = horizontalAlignment,
                leftBound = leftBound, measuredWidth = measuredWidth,
            )
        }
    }
}
