package com.joseleandro.scaniqo.data.pdf

import android.app.Application
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
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
class PdfOverlayRotationTest {
    @Test fun rotatedStyledTextRemainsSearchableAndItsBaselineTurnsClockwiseAroundTheBoxCenter() = runTest {
        val overlay = PdfOverlay(
            "name", PdfOverlayKind.TEXT, text = "Maria", x = .2f, y = .25f,
            width = .5f, height = .2f, fontSize = 24f,
            bold = true, italic = true, underlined = true,
            textHorizontalAlignment = PdfTextHorizontalAlignment.CENTER,
            textVerticalAlignment = PdfTextVerticalAlignment.BOTTOM,
        )
        val baseline = PDDocument.load(export(listOf(overlay))).use { document ->
            textPositions(document).first().textMatrix
        }
        PDDocument.load(export(listOf(overlay.copy(rotationDegrees = 90f)))).use { document ->
            // Match the application's search: grouping respects the text's rotated baseline.
            val extracted = PDFTextStripper().apply { sortByPosition = true }.getText(document)
            assertEquals("Maria", extracted.trim())
            assertEquals(PdfTextMatch("Maria", 1), findPdfTextMatch(document, "Maria"))
            assertTrue(document.getPage(0).resources.xObjectNames.none())
            val rotated = textPositions(document).first().textMatrix
            val centerX = (overlay.x + overlay.width / 2f) * 595f
            val centerY = (1f - overlay.y - overlay.height / 2f) * 842f
            assertEquals(centerX + baseline.translateY - centerY, rotated.translateX, .03f)
            assertEquals(centerY - baseline.translateX + centerX, rotated.translateY, .03f)
            assertEquals(0f, rotated.getValue(0, 0), .001f)
            assertTrue("Text must advance down the PDF page after a clockwise turn", rotated.getValue(0, 1) < 0f)
            assertTrue(document.getPage(0).resources.fontNames.any {
                document.getPage(0).resources.getFont(it).name == "Helvetica-BoldOblique"
            })
        }
    }

    @Test fun rotatedVectorMarkPreservesItsCenterAndDoesNotRotateTheFollowingOverlay() {
        val check = PdfOverlay(
            "check", PdfOverlayKind.CHECKMARK, x = .2f, y = .3f,
            width = .2f, height = .1f, rotationDegrees = 90f,
        )
        val cross = PdfOverlay(
            "cross", PdfOverlayKind.CROSSMARK, x = .6f, y = .6f,
            width = .08f, height = .06f,
        )
        PDDocument.load(export(listOf(check, cross))).use { document ->
            val trace = graphics(document.getPage(0))
            val centerX = (check.x + check.width / 2f) * 595f
            val centerY = (1f - check.y - check.height / 2f) * 842f
            assertEquals(centerX, trace.moves[0].first, .01f)
            assertEquals(centerY + check.width * 595f * .38f, trace.moves[0].second, .01f)
            assertEquals((cross.x + cross.width * .18f) * 595f, trace.moves[1].first, .01f)
            assertEquals((1f - cross.y - cross.height * .18f) * 842f, trace.moves[1].second, .01f)
            assertTrue(document.getPage(0).resources.xObjectNames.none())
        }
    }

    @Test fun rotatedUnicodeImageUsesTheSameCenterAndClockwiseOrientationAsVectorText() {
        val overlay = PdfOverlay(
            "unicode", PdfOverlayKind.TEXT, text = "姓名：王小明",
            x = .25f, y = .35f, width = .3f, height = .15f,
            rotationDegrees = 90f,
        )
        PDDocument.load(export(listOf(overlay))).use { document ->
            val transform = graphics(document.getPage(0)).images.single()
            val centerX = (overlay.x + overlay.width / 2f) * 595f
            val centerY = (1f - overlay.y - overlay.height / 2f) * 842f
            val center = transform.point(.5f, .5f)
            val topLeft = transform.point(0f, 1f)
            val bottomRight = transform.point(1f, 0f)
            assertEquals(centerX, center.first, .01f)
            assertEquals(centerY, center.second, .01f)
            assertEquals(centerX + overlay.height * 842f / 2f, topLeft.first, .01f)
            assertEquals(centerY + overlay.width * 595f / 2f, topLeft.second, .01f)
            assertEquals(centerX - overlay.height * 842f / 2f, bottomRight.first, .01f)
            assertEquals(centerY - overlay.width * 595f / 2f, bottomRight.second, .01f)
        }
    }

    private fun textPositions(document: PDDocument): List<TextPosition> {
        val positions = mutableListOf<TextPosition>()
        object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                positions += text
                super.processTextPosition(text)
            }
        }.getText(document)
        return positions
    }

    private fun graphics(page: PDPage): GraphicsTrace {
        val parser = PDFStreamParser(page)
        parser.parse()
        var transform = PdfContentTransform()
        val stack = mutableListOf<PdfContentTransform>()
        val operands = mutableListOf<COSBase>()
        val moves = mutableListOf<Pair<Float, Float>>()
        val images = mutableListOf<PdfContentTransform>()
        for (token in parser.tokens) {
            if (token !is Operator) {
                if (token is COSBase) operands += token
                continue
            }
            fun number(index: Int) = (operands[index] as COSNumber).floatValue()
            when (token.name) {
                "q" -> stack += transform
                "Q" -> transform = stack.removeAt(stack.lastIndex)
                "cm" -> transform = transform.then(PdfContentTransform(
                    number(0), number(1), number(2), number(3), number(4), number(5),
                ))
                "m" -> moves += transform.point(number(0), number(1))
                "Do" -> images += transform
            }
            operands.clear()
        }
        assertTrue(stack.isEmpty())
        return GraphicsTrace(moves, images)
    }

    private fun export(overlays: List<PdfOverlay>): ByteArray {
        val bytes = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(595f, 842f))
            document.addPage(page)
            PDPageContentStream(document, page).use {
                PdfOverlayLayer.draw(document, it, overlays, 1, 1)
            }
            document.save(bytes)
        }
        return bytes.toByteArray()
    }

    private data class GraphicsTrace(
        val moves: List<Pair<Float, Float>>,
        val images: List<PdfContentTransform>,
    )
}
