package com.joseleandro.scaniqo.ui.settings

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.domain.model.*
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSettings()
    private val store = ViewModelStore()
    private lateinit var model: SettingsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        model = SettingsViewModel(repository)
        store.put("settings", model)
    }
    @After fun teardown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun rapidChangesKeepTapOrderAcrossSlowWritesAndRestore() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        model.change(SettingsChange.Mode(AppThemeMode.DARK))
        model.change(SettingsChange.Quality(PdfExportQuality.SMALL))
        model.change(SettingsChange.RestoreDefaults)
        model.change(SettingsChange.Palette(AppColorTheme.ROSE))
        runCurrent()
        assertEquals(AppSettings(), model.settings.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AppSettings(colorTheme = AppColorTheme.ROSE), model.settings.value)
        assertEquals(4, repository.writes)
    }

    @Test fun changingAppearanceKeepsEveryPdfPreference() = runTest(dispatcher) {
        repository.value.value = AppSettings(
            exportQuality = PdfExportQuality.BALANCED, ocrEnabled = true, pageNumbers = true,
            imageFormat = PdfImageFormat.PNG, compressionLevel = PdfCompressionLevel.STRONG,
        )
        val original = repository.value.value
        model.change(SettingsChange.Mode(AppThemeMode.LIGHT))
        model.change(SettingsChange.Palette(AppColorTheme.FOREST))
        advanceUntilIdle()
        assertEquals(original.copy(themeMode = AppThemeMode.LIGHT, colorTheme = AppColorTheme.FOREST),
            model.settings.value)
    }

    @Test fun failureIsReportedAndDoesNotStopLaterPreferenceChanges() = runTest(dispatcher) {
        repository.failNext = true
        model.change(SettingsChange.Mode(AppThemeMode.DARK))
        model.change(SettingsChange.ImageFormat(PdfImageFormat.PNG))
        advanceUntilIdle()
        assertTrue(model.saveError.value)
        assertEquals(AppThemeMode.SYSTEM, model.settings.value.themeMode)
        assertEquals(PdfImageFormat.PNG, model.settings.value.imageFormat)
        model.dismissError()
        assertFalse(model.saveError.value)
    }

    private class FakeSettings : AppSettingsRepository {
        val value = MutableStateFlow(AppSettings())
        override val settings = value.asStateFlow()
        var gate: CompletableDeferred<Unit>? = null
        var failNext = false
        var writes = 0
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            val waiting = gate
            gate = null
            waiting?.await()
            if (failNext) {
                failNext = false
                throw java.io.IOException("Simulação de falha ao persistir")
            }
            value.value = transform(value.value)
            writes++
        }
    }
}
