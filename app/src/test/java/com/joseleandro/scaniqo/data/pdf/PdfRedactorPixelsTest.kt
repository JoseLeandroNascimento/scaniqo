package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.joseleandro.scaniqo.domain.model.PdfRedactionArea
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfRedactorPixelsTest {
    @Test fun redactionOverwritesEverySensitivePixelIncludingFractionalEdges() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            PdfRedactor.redactPixels(bitmap, listOf(PdfRedactionArea("page", .205f, .305f, .203f, .103f)))
            for (y in 30..40) for (x in 20..40) assertEquals("Pixel $x,$y", Color.BLACK, bitmap.getPixel(x, y))
            assertEquals(Color.RED, bitmap.getPixel(19, 30))
            assertEquals(Color.RED, bitmap.getPixel(41, 40))
            assertEquals(Color.RED, bitmap.getPixel(20, 29))
        } finally { bitmap.recycle() }
    }

    @Test fun fullPageMaskCannotLeaveUnredactedBorders() {
        val bitmap = Bitmap.createBitmap(37, 53, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            PdfRedactor.redactPixels(bitmap, listOf(PdfRedactionArea("page", 0f, 0f, 1f, 1f)))
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                assertEquals(Color.BLACK, bitmap.getPixel(x, y))
            }
        } finally { bitmap.recycle() }
    }
}
