package com.joseleandro.scaniqo.ui.documentEditor.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfCaptureSource
import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfInteractivePixels
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentCaptureSourceState
import com.joseleandro.scaniqo.ui.documentEditor.util.captureMagnifierTransform
import com.joseleandro.scaniqo.ui.documentEditor.util.corners
import com.joseleandro.scaniqo.ui.documentEditor.util.placeCaptureMagnifier
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

/** Amplia os pixels originais, sem máscara/alças e sem alocar outro bitmap por movimento. */
@Composable
internal fun DocumentCaptureMagnifier(
    image: ImageBitmap,
    focus: CaptureMagnifierFocus,
    imageSize: IntSize,
    viewportSize: IntSize,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val placement = with(density) {
        placeCaptureMagnifier(
            viewportWidth = viewportSize.width.toFloat(),
            viewportHeight = viewportSize.height.toFloat(),
            fingerX = (viewportSize.width - imageSize.width) / 2f + focus.pointer.x * imageSize.width,
            fingerY = (viewportSize.height - imageSize.height) / 2f + focus.pointer.y * imageSize.height,
            desiredWidth = 112.dp.toPx(),
            desiredHeight = 96.dp.toPx(),
            margin = 8.dp.toPx(),
            clearance = 40.dp.toPx(),
            minimumWidth = 72.dp.toPx(),
            minimumHeight = 64.dp.toPx(),
        )
    } ?: return // Em palcos mínimos, o gesto continua sem cobrir o dedo ou os controles.
    val shape = RoundedCornerShape(12.dp)
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.primary
    val crosshair = androidx.compose.ui.graphics.Color.Black
    Canvas(
        modifier
            .offset { IntOffset(placement.left.roundToInt(), placement.top.roundToInt()) }
            .size(with(density) { placement.width.toDp() }, with(density) { placement.height.toDp() })
            .shadow(6.dp, shape)
            .clip(shape)
            .background(surface)
            .border(2.dp, outline, shape)
            .clearAndSetSemantics {},
    ) {
        val transform = captureMagnifierTransform(
            focus.point, imageSize.width.toFloat(), imageSize.height.toFloat(),
            image.width, image.height, size.width, size.height,
        ) ?: return@Canvas
        withTransform({
            translate(transform.translateX, transform.translateY)
            scale(transform.scaleX, transform.scaleY, pivot = Offset.Zero)
        }) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(image.width, image.height),
                filterQuality = FilterQuality.Medium,
            )
        }
        val arm = 13.dp.toPx()
        val gap = 4.dp.toPx()
        val segments = listOf(
            Offset(-arm, 0f) to Offset(-gap, 0f),
            Offset(gap, 0f) to Offset(arm, 0f),
            Offset(0f, -arm) to Offset(0f, -gap),
            Offset(0f, gap) to Offset(0f, arm),
        )
        segments.forEach { (start, end) ->
            drawLine(androidx.compose.ui.graphics.Color.White, center + start, center + end, 3.dp.toPx())
            drawLine(crosshair, center + start, center + end, 1.dp.toPx())
        }
        drawCircle(androidx.compose.ui.graphics.Color.White, 2.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        drawCircle(outline, 2.dp.toPx(), center, style = Stroke(1.dp.toPx()))
    }
}

@Preview(name = "Lupa ativa · captura horizontal", widthDp = 640, heightDp = 320, showBackground = true)
@Preview(name = "Lupa ativa · captura horizontal pequena", widthDp = 360, heightDp = 320, fontScale = 1.3f)
@Composable
private fun DocumentCaptureMagnifierLandscapePreview() {
    val pixels = remember {
        val bitmap = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0xFFD3CAB9.toInt())
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(35f, 22f, 565f, 278f, paint)
        paint.color = 0xFF252A34.toInt()
        paint.textSize = 26f
        canvas.drawText("CAPTURA ORIGINAL", 65f, 75f, paint)
        paint.textSize = 20f
        canvas.drawText("Documento 042 · Total R$ 128,50", 65f, 115f, paint)
        paint.color = 0xFF747A84.toInt()
        paint.strokeWidth = 3f
        for (line in 0..4) {
            canvas.drawLine(65f, 150f + line * 21f, 510f - line * 30f, 150f + line * 21f, paint)
        }
        val colors = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        PdfInteractivePixels(bitmap.width, bitmap.height, colors, 600f, 300f).also { bitmap.recycle() }
    }
    val crop = PdfCropBounds(.15f, .28f, .9f, .9f)
    ScaniqoTheme {
        Surface {
            DocumentSourceSelectionEditor(
                page = PdfPageSpec("magnifier", "preview", 0),
                points = crop.corners(),
                enabled = true,
                pageNumber = 1,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                sourceState = DocumentCaptureSourceState(
                    pageId = "magnifier",
                    source = PdfCaptureSource(pixels, PdfCropBounds()),
                ),
                magnifierFocus = CaptureMagnifierFocus(PdfPoint(.15f, .28f), PdfPoint(.15f, .28f)),
                corners = {},
            )
        }
    }
}
