package com.joseleandro.scaniqo.ui.documentSignature

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.roundToInt

@Preview(name = "Lupa assinatura ativa · documento e tinta", widthDp = 360, heightDp = 480, showBackground = true)
@Preview(name = "Lupa assinatura ativa · palco largo", widthDp = 640, heightDp = 360, showBackground = true)
@Preview(name = "Lupa assinatura · árabe", widthDp = 360, heightDp = 480, locale = "ar", showBackground = true)
@Composable
private fun DocumentSignatureMagnifierPreview() {
    val document = remember {
        Bitmap.createBitmap(600, 848, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = android.graphics.Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(android.graphics.Color.WHITE)
            paint.color = 0xFF29313B.toInt()
            paint.textSize = 27f
            canvas.drawText("TERMO DE RECEBIMENTO", 65f, 95f, paint)
            paint.textSize = 18f
            canvas.drawText("Documento 042 · 17 de outubro", 65f, 138f, paint)
            paint.color = 0xFFACB3BA.toInt()
            paint.strokeWidth = 2f
            for (line in 0..6) canvas.drawLine(65f, 195f + line * 36f, 535f, 195f + line * 36f, paint)
            canvas.drawLine(100f, 616f, 510f, 616f, paint)
            paint.color = 0xFF535E6A.toInt()
            paint.textSize = 18f
            canvas.drawText("Assinatura do responsável", 170f, 650f, paint)
        }.asImageBitmap()
    }
    val signature = remember {
        Bitmap.createBitmap(500, 140, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = android.graphics.Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF263D78.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path().apply {
                moveTo(25f, 112f)
                cubicTo(150f, -25f, 135f, 130f, 50f, 105f)
                cubicTo(195f, 65f, 90f, 20f, 160f, 85f)
                cubicTo(200f, 120f, 190f, 5f, 220f, 85f)
                cubicTo(280f, 150f, 240f, 30f, 300f, 80f)
                cubicTo(360f, 120f, 370f, 5f, 400f, 90f)
                cubicTo(440f, 125f, 450f, 45f, 475f, 70f)
                moveTo(65f, 125f)
                cubicTo(205f, 108f, 330f, 112f, 460f, 105f)
            }
            canvas.drawPath(path, paint)
        }.asImageBitmap()
    }
    val placement = PdfSignaturePlacement(
        "preview", "preview", x = .22f, y = .63f, width = .52f, height = .09f,
        rotationDegrees = -5f, stretchToBounds = true,
    )
    ScaniqoTheme {
        Surface {
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft) {
                val density = LocalDensity.current
                val width = minOf(maxWidth - 48.dp, (maxHeight - 48.dp) * 595f / 842f)
                val height = width * 842f / 595f
                val pageWidth = with(density) { width.toPx() }
                val pageHeight = with(density) { height.toPx() }
                val left = (constraints.maxWidth - pageWidth) / 2f
                val top = (constraints.maxHeight - pageHeight) / 2f
                Image(document, null, Modifier.absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(width, height))
                Image(signature, null, Modifier.absoluteOffset {
                    IntOffset((left + placement.x * pageWidth).roundToInt(), (top + placement.y * pageHeight).roundToInt())
                }.size(width * placement.width, height * placement.height)
                    .graphicsLayer { rotationZ = placement.rotationDegrees }, contentScale = ContentScale.FillBounds)
                DocumentSignatureMagnifier(
                    document, signature, placement, PdfPoint(.74f, .72f),
                    Rect(left, top, left + pageWidth, top + pageHeight),
                    IntSize(constraints.maxWidth, constraints.maxHeight),
                )
            }
        }
    }
}
