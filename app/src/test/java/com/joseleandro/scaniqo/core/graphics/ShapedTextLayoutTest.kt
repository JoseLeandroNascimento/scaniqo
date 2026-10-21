package com.joseleandro.scaniqo.core.graphics

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapedTextLayoutTest {
    @Test fun mixedArabicKeepsLogicalTextAndUsesRtlParagraphShaping() {
        val text = "الاسم: ليلى 123 / PDF"
        val shaped = shape(text)
        assertEquals(text, shaped.layout.text.toString())
        assertTrue(shaped.isRtl)
        assertEquals(-1, shaped.layout.getParagraphDirection(0))
        assertTrue(shaped.layout.isRtlCharAt(0))
        assertFalse(shaped.layout.isRtlCharAt(text.indexOf("PDF")))
    }

    @Test fun paragraphDirectionDoesNotFollowTheAppLanguage() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA"))
            val latin = shape("Name: Leandro 123")
            assertFalse(latin.isRtl)
            assertEquals(1, latin.layout.getParagraphDirection(0))
            assertTrue(shape("ليلى 123").isRtl)
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test fun cjkCyrillicAndCombiningCharactersAreNotReplaced() {
        for (text in listOf("姓名：王小明", "Имя: Мария", "Jose\u0301 👩🏽‍💻")) {
            val shaped = shape(text)
            assertEquals(text, shaped.layout.text.toString())
            assertTrue(shaped.width > 0f && shaped.width <= 250f)
            assertTrue(shaped.height > 0f && shaped.height <= 70f)
        }
    }

    @Test fun longMultilineTextFitsEvenAVerySmallBoxWithoutDroppingLines() {
        val text = ("中文 العربية Русский\n".repeat(60)).trimEnd()
        val shaped = ShapedTextLayout.create(text, 72f, Color.BLACK, .5f, .2f)
        assertEquals(60, shaped.layout.lineCount)
        assertEquals(text, shaped.layout.text.toString())
        assertTrue(shaped.width <= .5001f)
        assertTrue(shaped.height <= .2001f)
    }

    @Test fun rtlTextDrawsAtTheRightOfItsPhysicalBoxWithTransparentBackground() {
        val shaped = shape("مرحبا")
        val bitmap = Bitmap.createBitmap(320, 100, Bitmap.Config.ARGB_8888)
        try {
            shaped.draw(Canvas(bitmap), 10f, 10f, availableWidth = 300f)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.any { Color.alpha(it) > 0 })
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
            val firstInkX = pixels.indices.filter { Color.alpha(pixels[it]) > 0 }
                .minOf { it % bitmap.width }
            assertTrue(firstInkX > 10)
        } finally {
            bitmap.recycle()
        }
    }

    private fun shape(text: String) =
        ShapedTextLayout.create(text, 24f, Color.BLACK, 250f, 70f)
}
