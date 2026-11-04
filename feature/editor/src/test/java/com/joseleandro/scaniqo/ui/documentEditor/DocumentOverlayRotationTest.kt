package com.joseleandro.scaniqo.ui.documentEditor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import com.joseleandro.scaniqo.ui.documentEditor.components.drawOverlayDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentOverlayRotationTest {
    @Test fun composePreviewRotatesVisibleInkAroundTheSameA4Center() {
        val overlay = PdfOverlay(
            "check", PdfOverlayKind.CHECKMARK, x = .3f, y = .35f,
            width = .24f, height = .12f, color = Color.BLACK,
        )
        val original = previewInkBounds(overlay)
        val rotated = previewInkBounds(overlay.copy(rotationDegrees = 90f))
        val centerX = (overlay.x + overlay.width / 2f) * 595f
        val centerY = (overlay.y + overlay.height / 2f) * 842f
        assertEquals(centerX - (original.bottom - centerY), rotated.left, 2f)
        assertEquals(centerY + (original.left - centerX), rotated.top, 2f)
        assertEquals(centerX - (original.top - centerY), rotated.right, 2f)
        assertEquals(centerY + (original.right - centerX), rotated.bottom, 2f)
    }

    private fun previewInkBounds(overlay: PdfOverlay): Rect {
        val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        try {
            CanvasDrawScope().draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = ComposeCanvas(bitmap.asImageBitmap()),
                size = Size(595f, 842f),
            ) {
                drawOverlayDraft(overlay, 1, 1)
            }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var left = bitmap.width
            var top = bitmap.height
            var right = -1
            var bottom = -1
            pixels.forEachIndexed { index, pixel ->
                if (Color.alpha(pixel) > 32) {
                    val x = index % bitmap.width
                    val y = index / bitmap.width
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
            assertTrue("The preview must contain visible ink", right >= left && bottom >= top)
            return Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
        } finally {
            bitmap.recycle()
        }
    }

}
