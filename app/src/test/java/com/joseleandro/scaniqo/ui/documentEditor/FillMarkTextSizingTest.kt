package com.joseleandro.scaniqo.ui.documentEditor

import android.app.Application
import com.joseleandro.scaniqo.core.graphics.ShapedTextLayout
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.documentEditor.components.fittedFillMarkText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FillMarkTextSizingTest {
    private val text = PdfOverlay(
        "name", PdfOverlayKind.TEXT, text = "Maria", x = .15f, y = .2f,
        autoSizeText = true, wrapText = true,
    )

    @Test fun typingAndDeletingResizeTheBoxWithoutMovingTheTapAnchor() {
        val short = text.fittedFillMarkText()
        val long = short.copy(text = "Maria da Silva dos Santos").fittedFillMarkText()
        val deleted = long.copy(text = "Maria").fittedFillMarkText()
        assertTrue(long.width > short.width)
        assertEquals(short, deleted)
        assertEquals(text.x, long.x, .00001f)
        assertEquals(text.y, long.y, .00001f)
        assertEquals(text.fontSize, shape(long).fontSize, .01f)
    }

    @Test fun requestedLargerFontGrowsTheBoxAsWellAsTheVisibleText() {
        val small = text.fittedFillMarkText()
        val large = small.copy(fontSize = 32f).fittedFillMarkText()
        assertTrue(large.width > small.width * 1.9f)
        assertTrue(large.height > small.height * 1.8f)
        assertEquals(32f, shape(large).fontSize, .01f)
        assertTrue(large.isValid())
    }

    @Test fun longTextWrapsAtThePageEdgeBeforeReducingTheFont() {
        val value = text.copy(x = .75f, width = .25f, text = "Maria da Silva dos Santos de Oliveira")
            .fittedFillMarkText()
        val shaped = shape(value)
        assertTrue(shaped.layout.lineCount > 1)
        assertEquals(16f, shaped.fontSize, .01f)
        assertEquals(.75f, value.x, .00001f)
        assertTrue(value.x + value.width <= 1.00001f)
        assertTrue(value.isValid())
    }

    @Test fun verticalOverflowFitsAllTextInsideThePageWithoutChangingTheRequestedSize() {
        val value = text.copy(x = .75f, y = .98f, width = .25f, height = .02f,
            text = "Maria da Silva dos Santos de Oliveira").fittedFillMarkText()
        val shaped = shape(value)
        assertTrue(shaped.fontSize < value.fontSize)
        assertEquals(16f, value.fontSize, 0f)
        assertEquals(value.text, shaped.layout.text.toString())
        assertTrue(value.y + value.height <= 1.00001f)
        assertTrue(value.isValid())
    }

    @Test fun fittedDimensionsAreStableWhenReappliedAfterWrappingAndHeightClamping() {
        for (value in listOf(
            text,
            text.copy(x = .7f, width = .3f, text = "Nome completo com varias palavras"),
            text.copy(y = .99f, height = .01f, text = "Primeira linha\nSegunda linha"),
        )) {
            val fitted = value.fittedFillMarkText()
            assertEquals(fitted, fitted.fittedFillMarkText())
        }
    }

    @Test fun emptyFieldRetainsAUsableCaretBoxAndNeverLeavesThePage() {
        val value = text.copy(text = "", x = .98f, y = .98f, width = .02f, height = .02f)
            .fittedFillMarkText()
        assertTrue(value.width > 0f && value.height > 0f)
        assertTrue(value.isValid())
        assertEquals(.98f, value.x, 0f)
    }

    @Test fun manualBoxesAndNonTextElementsAreNeverResizedImplicitly() {
        val manual = text.copy(autoSizeText = false, wrapText = false)
        assertEquals(manual, manual.fittedFillMarkText())
        val check = text.copy(kind = PdfOverlayKind.CHECKMARK)
        assertEquals(check, check.fittedFillMarkText())
        val invalid = text.copy(fontSize = Float.NaN)
        assertEquals(invalid, invalid.fittedFillMarkText())
    }

    private fun shape(value: PdfOverlay) = ShapedTextLayout.create(
        value.text, value.fontSize, value.color, value.width * 595f, value.height * 842f,
        wrap = value.wrapText, horizontalAlignment = value.textHorizontalAlignment,
    )
}
