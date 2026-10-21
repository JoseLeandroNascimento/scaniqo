package com.joseleandro.scaniqo.core.localization

import android.content.Context
import android.provider.Settings
import org.robolectric.RuntimeEnvironment
import com.joseleandro.scaniqo.R
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], application = android.app.Application::class)
class LocalizedFormatsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun percentagesAndPageNumbersUseLocalDigits() {
        assertEquals("75%", localizedPercent(.75f, Locale.US))
        val arabic = localizedPercent(.75f, Locale.forLanguageTag("ar-SA"))
        assertTrue(arabic.contains("٧٥"))
        assertTrue(arabic.contains("٪"))
        assertEquals("১২৩৪", localizedInteger(1234, Locale.forLanguageTag("bn-BD")))
    }

    @Test fun contextsResolveTranslationsAndReadingDirection() {
        val english = context.withLocale(Locale.US)
        val portuguese = context.withLocale(Locale.forLanguageTag("pt-BR"))
        val arabic = context.withLocale(Locale.forLanguageTag("ar-SA"))
        assertEquals("Cancel", english.getString(R.string.runtime_cancel))
        assertEquals("Cancelar", portuguese.getString(R.string.runtime_cancel))
        assertNotEquals(english.getString(R.string.runtime_cancel), arabic.getString(R.string.runtime_cancel))
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, arabic.resources.configuration.layoutDirection)
        assertEquals(android.view.View.LAYOUT_DIRECTION_LTR, english.resources.configuration.layoutDirection)
    }

    @Test fun dateTimeHonorsTheDeviceClockPreference() {
        val localized = context.withLocale(Locale.US)
        val resolver = context.contentResolver
        val previous = Settings.System.getString(resolver, Settings.System.TIME_12_24)
        try {
            Settings.System.putString(resolver, Settings.System.TIME_12_24, "24")
            val twentyFour = localized.localizedDateTime(1792602000000L)
            Settings.System.putString(resolver, Settings.System.TIME_12_24, "12")
            val twelve = localized.localizedDateTime(1792602000000L)
            assertNotEquals(twentyFour, twelve)
        } finally {
            Settings.System.putString(resolver, Settings.System.TIME_12_24, previous)
        }
    }

    @Test fun technicalErrorsHaveALocalizedFallback() {
        assertEquals(R.string.runtime_operation_failed, runtimeMessageResource("java.io.IOException: /private/path"))
        assertEquals(R.string.runtime_password_incorrect, runtimeMessageResource("Senha incorreta. Tente novamente."))
    }
}
