package com.joseleandro.scaniqo.core.graphics

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.min

/**
 * Shapes whole paragraphs, including bidi runs and fallback fonts, before fitting them.
 * Layout is measured at a fixed scale so the preview and PDF use the same line metrics.
 */
internal class ShapedTextLayout private constructor(
    internal val layout: StaticLayout,
    private val scale: Float,
    val isRtl: Boolean,
) {
    val width: Float get() = layout.width * scale
    val height: Float get() = layout.height * scale
    val fontSize: Float get() = layout.paint.textSize * scale
    val lineAdvance: Float get() = if (layout.lineCount > 1) {
        (layout.getLineBaseline(1) - layout.getLineBaseline(0)) * scale
    } else (layout.paint.fontMetrics.descent - layout.paint.fontMetrics.ascent) * scale

    fun draw(canvas: Canvas, left: Float, top: Float, availableWidth: Float = width) {
        val save = canvas.save()
        try {
            // Document coordinates remain physical; only the paragraph aligns to its own direction.
            val offset = if (isRtl) (availableWidth - width).coerceAtLeast(0f) else 0f
            canvas.translate(left + offset, top)
            canvas.scale(scale, scale)
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
            // Fit explicit lines as a block, without introducing different wraps at export density.
            val naturalWidth = ceil(Layout.getDesiredWidth(normalized, paint).toDouble())
                .toInt().coerceAtLeast(1) + 1
            val builder = StaticLayout.Builder.obtain(normalized, 0, normalized.length, paint, naturalWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setIncludePad(true)
                .setLineSpacing(0f, 1.2f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUseLineSpacingFromFallbacks(true)
            val layout = builder.build()
            val scale = min(1f / METRIC_SCALE, min(
                maxWidth / layout.width,
                maxHeight / layout.height.coerceAtLeast(1),
            ))
            return ShapedTextLayout(
                layout = layout,
                scale = scale,
                isRtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(normalized, 0, normalized.length),
            )
        }
    }
}
