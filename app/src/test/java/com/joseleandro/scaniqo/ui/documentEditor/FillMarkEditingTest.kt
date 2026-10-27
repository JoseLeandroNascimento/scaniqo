package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import com.joseleandro.scaniqo.ui.documentEditor.components.FillMarkStyle
import com.joseleandro.scaniqo.ui.documentEditor.components.alignedFillMark
import com.joseleandro.scaniqo.ui.documentEditor.components.duplicatedFillMark
import com.joseleandro.scaniqo.ui.documentEditor.components.nextFillMarkAt
import com.joseleandro.scaniqo.ui.documentEditor.components.updatedFillMarkRecentColors
import com.joseleandro.scaniqo.ui.documentEditor.components.withFillMarkKind
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FillMarkEditingTest {
    private val text = PdfOverlay(
        id = "original", kind = PdfOverlayKind.TEXT, text = "Dado particular",
        x = .2f, y = .3f, width = .3f, height = .08f, fontSize = 24f,
        color = 0xFF1565C0.toInt(), opacity = .75f,
        bold = true, italic = true, underlined = true,
    )

    @Test fun rememberedAppearanceSurvivesSerializationWithoutReusingContentOrIdentity() {
        val encoded = Json.encodeToString(FillMarkStyle.from(text))
        val fresh = PdfOverlay("new", PdfOverlayKind.TEXT, x = .1f, y = .15f)
        val restored = Json.decodeFromString<FillMarkStyle>(encoded).applyTo(fresh)

        assertFalse(encoded.contains(text.text))
        assertFalse(encoded.contains(text.id))
        assertEquals("new", restored.id)
        assertEquals("", restored.text)
        assertEquals(.1f, restored.x, .00001f)
        assertEquals(.15f, restored.y, .00001f)
        assertEquals(text.color, restored.color)
        assertEquals(text.fontSize, restored.fontSize, .00001f)
        assertEquals(text.width, restored.width, .00001f)
        assertEquals(text.height, restored.height, .00001f)
        assertEquals(text.opacity, restored.opacity, .00001f)
        assertTrue(restored.bold)
        assertTrue(restored.italic)
        assertTrue(restored.underlined)
    }

    @Test fun oldSavedStyleUsesStartAndTopAlignment() {
        val legacy = """{"color":-16777216,"fontSize":18.0,"width":0.4,"height":0.1,"opacity":1.0}"""
        val style = Json.decodeFromString<FillMarkStyle>(legacy)

        assertEquals(PdfTextHorizontalAlignment.START, style.textHorizontalAlignment)
        assertEquals(PdfTextVerticalAlignment.TOP, style.textVerticalAlignment)
        assertTrue(style.isValid())
        assertFalse(style.bold)
        assertFalse(style.italic)
        assertFalse(style.underlined)
    }

    @Test fun rememberedAlignmentDoesNotRestoreManualSizingOrPrivateText() {
        val manuallySized = text.copy(
            textHorizontalAlignment = PdfTextHorizontalAlignment.END,
            textVerticalAlignment = PdfTextVerticalAlignment.CENTER,
            autoSizeText = false, wrapText = false,
        )
        val encoded = Json.encodeToString(FillMarkStyle.from(manuallySized))
        val fresh = PdfOverlay("new", PdfOverlayKind.TEXT, autoSizeText = true, wrapText = true)
        val restored = Json.decodeFromString<FillMarkStyle>(encoded).applyTo(fresh)

        assertEquals(PdfTextHorizontalAlignment.END, restored.textHorizontalAlignment)
        assertEquals(PdfTextVerticalAlignment.CENTER, restored.textVerticalAlignment)
        assertTrue(restored.autoSizeText)
        assertTrue(restored.wrapText)
        assertEquals("", restored.text)
        assertEquals(fresh.id, restored.id)
        assertFalse(encoded.contains(manuallySized.text))
    }

    @Test fun corruptRememberedAppearanceFallsBackToFreshDefaults() {
        val style = FillMarkStyle.from(text)
        listOf(
            style.copy(fontSize = Float.NaN), style.copy(width = 0f),
            style.copy(height = 2f), style.copy(opacity = Float.POSITIVE_INFINITY),
        ).forEach { assertEquals(text, it.applyTo(text)) }
    }

    @Test fun restoringLargeAppearanceKeepsTheElementInsideThePage() {
        val restored = FillMarkStyle.from(text).copy(width = .95f, height = .98f).applyTo(text)
        assertTrue(restored.isValid())
        assertEquals(.95f, restored.width, .00001f)
        assertEquals(.98f, restored.height, .00001f)
    }

    @Test fun duplicatesHaveIndependentIdentitiesAndPreserveOriginalTextAndStyle() {
        val first = text.duplicatedFillMark()
        val second = text.duplicatedFillMark()

        assertNotEquals(text.id, first.id)
        assertNotEquals(first.id, second.id)
        assertEquals(text.text, first.text)
        assertEquals(FillMarkStyle.from(text), FillMarkStyle.from(first))
        assertNotEquals(text.x, first.x)
        assertNotEquals(text.y, first.y)
        assertEquals("original", text.id)
        assertEquals(.2f, text.x, .00001f)
        assertTrue(first.isValid())
    }

    @Test fun checkmarkAtBottomRightDuplicatesInsidePageWithoutShrinking() {
        val check = text.copy(kind = PdfOverlayKind.CHECKMARK, text = "", x = .96f, y = .97f, width = .04f, height = .03f)
        val copy = check.duplicatedFillMark()

        assertTrue(copy.isValid())
        assertTrue(copy.x < check.x)
        assertTrue(copy.y < check.y)
        assertEquals(check.width, copy.width, .00001f)
        assertEquals(check.height, copy.height, .00001f)
        assertNotEquals(check.id, copy.id)
    }

    @Test fun convertingSelectedCheckAndCrossKeepsIdentityGeometryAndAppearance() {
        val check = text.copy(
            kind = PdfOverlayKind.CHECKMARK, text = "", width = .07f, height = .05f,
        )
        val cross = check.withFillMarkKind(PdfOverlayKind.CROSSMARK)
        assertEquals(PdfOverlayKind.CROSSMARK, cross.kind)
        assertEquals(check.copy(kind = PdfOverlayKind.CROSSMARK), cross)
        assertEquals(check, cross.withFillMarkKind(PdfOverlayKind.CHECKMARK))
        assertEquals(check, check.withFillMarkKind(PdfOverlayKind.TEXT))
    }

    @Test fun consecutiveMarksKeepTheirOwnIdentityAndDoNotChangeEarlierMarks() {
        val nextStyle = text.copy(
            id = "template", kind = PdfOverlayKind.CHECKMARK, text = "",
            width = .06f, height = .04f,
        )
        val first = nextStyle.nextFillMarkAt(PdfPoint(.2f, .3f))!!
        val crossStyle = nextStyle.withFillMarkKind(PdfOverlayKind.CROSSMARK)
        val second = crossStyle.nextFillMarkAt(PdfPoint(.5f, .6f))!!
        val third = crossStyle.nextFillMarkAt(PdfPoint(.7f, .8f))!!

        assertEquals(PdfOverlayKind.CHECKMARK, first.kind)
        assertEquals(PdfOverlayKind.CROSSMARK, second.kind)
        assertEquals(PdfOverlayKind.CROSSMARK, third.kind)
        assertEquals(4, setOf(nextStyle.id, first.id, second.id, third.id).size)
        assertEquals(.2f, first.x, .00001f)
        assertEquals(.3f, first.y, .00001f)
        assertEquals(.5f, second.x, .00001f)
        assertEquals(.6f, second.y, .00001f)
        assertEquals(nextStyle.color, second.color)
        assertEquals(nextStyle.opacity, second.opacity, .00001f)
        assertNull(text.nextFillMarkAt(PdfPoint(.2f, .3f)))
    }

    @Test fun recentlyUsedColorsStayUniqueAndBounded() {
        val original = listOf(1, 2, 3, 4, 5)
        assertEquals(listOf(3, 1, 2, 4, 5), updatedFillMarkRecentColors(original, 3))
        assertEquals(listOf(6, 1, 2, 3, 4), updatedFillMarkRecentColors(original, 6))
    }

    @Test fun draggingNearPageCenterSnapsAndReportsTheGuide() {
        val moving = text.copy(x = .397f, y = .7f, width = .2f)
        val result = moving.alignedFillMark(emptyList(), .01f, .01f)

        assertEquals(.4f, result.overlay.x, .00001f)
        assertEquals(.5f, result.verticalGuide!!, .00001f)
        assertNull(result.horizontalGuide)
        assertEquals(moving.y, result.overlay.y, .00001f)
    }

    @Test fun draggingUsesTheNearestNeighborAndIgnoresTheOriginalOfTheSelectedElement() {
        val moving = text.copy(x = .202f, y = .7f, width = .1f)
        val first = text.copy(id = "neighbor", x = .2f)
        val second = text.copy(id = "other", x = .208f)
        val result = moving.alignedFillMark(listOf(first, second), .01f, .01f)
        assertEquals(.2f, result.overlay.x, .00001f)
        assertEquals(.2f, result.verticalGuide!!, .00001f)

        val alone = text.copy(x = .304f, y = .7f, width = .2f)
            .alignedFillMark(listOf(text.copy(x = .3f, width = .2f)), .01f, .01f)
        assertNull(alone.verticalGuide)
        assertEquals(.304f, alone.overlay.x, .00001f)
    }

    @Test fun snapDistanceScalesWithZoomAndDoesNotPullDistantElements() {
        val moving = text.copy(x = .208f, y = .7f, width = .1f)
        val neighbor = text.copy(id = "neighbor", x = .2f)
        val normal = moving.alignedFillMark(listOf(neighbor), 6f / 500f, 6f / 700f)
        val zoomed = moving.alignedFillMark(listOf(neighbor), 6f / 1000f, 6f / 1400f)

        assertEquals(.2f, normal.overlay.x, .00001f)
        assertEquals(moving.x, zoomed.overlay.x, .00001f)
        assertNull(zoomed.verticalGuide)
    }

    @Test fun pageEdgesSnapWithoutPuttingAnyPartOutsideThePage() {
        val result = text.copy(x = .003f, y = .917f, width = .2f)
            .alignedFillMark(emptyList(), .01f, .01f)

        assertEquals(0f, result.overlay.x, .00001f)
        assertEquals(.92f, result.overlay.y, .00001f)
        assertEquals(0f, result.verticalGuide!!, .00001f)
        assertEquals(1f, result.horizontalGuide!!, .00001f)
        assertTrue(result.overlay.isValid())
    }
}
