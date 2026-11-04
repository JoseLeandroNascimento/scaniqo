package com.joseleandro.scaniqo.data.repository

import android.app.Application
import android.content.Context
import com.joseleandro.scaniqo.domain.model.AppColorTheme
import com.joseleandro.scaniqo.domain.model.AppSettings
import com.joseleandro.scaniqo.domain.model.AppThemeMode
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AndroidAppSettingsRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        check(preferences.edit().clear().commit())
    }

    @Test fun emptyInstallationKeepsOriginalAppearanceAndExportDefaults() {
        assertEquals(AppSettings(), AndroidAppSettingsRepository(context).settings.value)
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun everyPreferenceSurvivesRepositoryRecreation() = runTest {
        val expected = customizedSettings()
        val repository = AndroidAppSettingsRepository(context)
        repository.update { expected }
        assertEquals(expected, repository.settings.value)
        assertEquals(expected, AndroidAppSettingsRepository(context).settings.value)
    }

    @Test fun unknownEnumNamesAndWrongStoredTypesFallBackPerPreference() {
        check(preferences.edit()
            .putInt("theme_mode", 3)
            .putString("color_theme", "future_theme")
            .putString("export_quality", "obsolete_quality")
            .putString("ocr_enabled", "true")
            .putInt("page_numbers", 1)
            .putFloat("image_format", 1f)
            .putString("compression_level", "unsupported")
            .commit())
        assertEquals(AppSettings(), AndroidAppSettingsRepository(context).settings.value)
    }

    @Test fun invalidPreferenceDoesNotDiscardOtherValidPreferences() {
        check(preferences.edit()
            .putString("theme_mode", AppThemeMode.DARK.name)
            .putString("color_theme", "missing_palette")
            .putString("export_quality", PdfExportQuality.SMALL.name)
            .putBoolean("ocr_enabled", true)
            .putBoolean("page_numbers", true)
            .putString("image_format", PdfImageFormat.PNG.name)
            .putString("compression_level", PdfCompressionLevel.STRONG.name)
            .commit())
        assertEquals(
            customizedSettings().copy(colorTheme = AppColorTheme.ORIGINAL),
            AndroidAppSettingsRepository(context).settings.value,
        )
    }

    @Test fun concurrentReadModifyWriteUpdatesAlwaysUseLatestSavedPreference() = runTest {
        val repository = AndroidAppSettingsRepository(context)
        val gate = CompletableDeferred<Unit>()
        val observedThemes = java.util.Collections.synchronizedList(mutableListOf<AppColorTheme>())
        val themes = AppColorTheme.entries
        coroutineScope {
            val jobs = List(101) {
                launch(Dispatchers.Default) {
                    gate.await()
                    repository.update { previous ->
                        observedThemes += previous.colorTheme
                        previous.copy(colorTheme = themes[(previous.colorTheme.ordinal + 1) % themes.size])
                    }
                }
            }
            gate.complete(Unit)
            jobs.joinAll()
        }
        assertEquals(List(101) { themes[it % themes.size] }, observedThemes.toList())
        val expected = themes[101 % themes.size]
        assertEquals(expected, repository.settings.value.colorTheme)
        assertEquals(expected, AndroidAppSettingsRepository(context).settings.value.colorTheme)
    }

    @Test fun restoringDefaultsResetsAllPreferencesAndPersistsTheReset() = runTest {
        val repository = AndroidAppSettingsRepository(context)
        repository.update { customizedSettings() }
        repository.update { AppSettings() }
        assertEquals(AppSettings(), repository.settings.value)
        assertEquals(AppSettings(), AndroidAppSettingsRepository(context).settings.value)
    }

    @Test fun failedTransformationLeavesObservableAndStoredSettingsUnchanged() = runTest {
        val repository = AndroidAppSettingsRepository(context)
        val expected = customizedSettings()
        repository.update { expected }
        val failure = runCatching {
            repository.update { throw IllegalStateException("transform failed") }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(expected, repository.settings.value)
        assertEquals(expected, AndroidAppSettingsRepository(context).settings.value)
        assertFalse(repository.settings.value == AppSettings())
    }

    private fun customizedSettings() = AppSettings(
        themeMode = AppThemeMode.DARK,
        colorTheme = AppColorTheme.LAVENDER,
        exportQuality = PdfExportQuality.SMALL,
        ocrEnabled = true,
        pageNumbers = true,
        imageFormat = PdfImageFormat.PNG,
        compressionLevel = PdfCompressionLevel.STRONG,
    )
}
