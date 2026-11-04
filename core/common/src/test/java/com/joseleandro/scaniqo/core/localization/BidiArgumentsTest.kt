package com.joseleandro.scaniqo.core.localization

import android.app.Application
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class BidiArgumentsTest {
    @Test
    fun latinFilenameKeepsParenthesesAndExtensionTogetherInsideArabicSentence() {
        val name = "Report (2026).pdf"

        assertEquals(
            "\u200F\u202AReport (2026).pdf\u202C\u200F",
            bidiArgument(name, Locale.forLanguageTag("ar-SA")),
        )
    }

    @Test
    fun arabicNameCannotReverseSurroundingEnglishPunctuation() {
        val name = "تقرير"

        assertEquals(
            "\u200E\u202Bتقرير\u202C\u200E",
            bidiArgument(name, Locale.US),
        )
    }

    @Test
    fun switchingAppLocaleUsesTheSameStoredNameWithoutRetainingFormattingMarks() {
        val name = "Invoice #12.pdf"

        assertEquals(name, bidiArgument(name, Locale.US))
        assertEquals(
            "\u200F\u202AInvoice #12.pdf\u202C\u200F",
            bidiArgument(name, Locale.forLanguageTag("ar-SA")),
        )
        assertEquals(name, bidiArgument(name, Locale.FRANCE))
    }
}
