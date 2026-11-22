package com.joseleandro.scaniqo.widget

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33], application = Application::class, manifest = Config.NONE)
class WidgetConfigurationTest {
    @Test
    fun appliedLocalesOverrideTheOldContextWithoutWaitingForPersistence() {
        val application = RuntimeEnvironment.getApplication()
        val oldContext = application.createConfigurationContext(
            Configuration(application.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags("en-US"))
                fontScale = 1.5f
            },
        )

        val localized = widgetContext(
            oldContext,
            WidgetConfiguration(languageTags = "pt-BR,en-US", revision = 1),
        )

        assertEquals("pt-BR,en-US", localized.resources.configuration.locales.toLanguageTags())
        assertEquals(1.5f, localized.resources.configuration.fontScale, 0f)
        assertEquals("en-US", oldContext.resources.configuration.locales.toLanguageTags())
    }

    @Test
    fun theSameSessionContextCanSwitchLanguagesAndReturnToSystemLocales() {
        val application = RuntimeEnvironment.getApplication()
        val systemTags = application.resources.configuration.locales.toLanguageTags()
        val portuguese = widgetContext(application, WidgetConfiguration("pt-BR", 1))
        val spanish = widgetContext(application, WidgetConfiguration("es", 2))
        val system = widgetContext(application, WidgetConfiguration(systemTags, 3))

        assertEquals("pt-BR", portuguese.resources.configuration.locales[0].toLanguageTag())
        assertEquals("es", spanish.resources.configuration.locales[0].toLanguageTag())
        assertEquals(systemTags, system.resources.configuration.locales.toLanguageTags())
        assertEquals(systemTags, application.resources.configuration.locales.toLanguageTags())
    }
}
