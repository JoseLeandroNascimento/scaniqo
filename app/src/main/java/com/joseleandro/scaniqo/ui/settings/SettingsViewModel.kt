package com.joseleandro.scaniqo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.domain.model.*
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SettingsChange {
    data class Mode(val value: AppThemeMode) : SettingsChange
    data class Palette(val value: AppColorTheme) : SettingsChange
    data class Quality(val value: PdfExportQuality) : SettingsChange
    data class Ocr(val value: Boolean) : SettingsChange
    data class Numbering(val value: Boolean) : SettingsChange
    data class ImageFormat(val value: PdfImageFormat) : SettingsChange
    data class Compression(val value: PdfCompressionLevel) : SettingsChange
    data object RestoreDefaults : SettingsChange
}

class SettingsViewModel(private val repository: AppSettingsRepository) : ViewModel() {
    val settings = repository.settings
    private val failure = MutableStateFlow(false)
    val saveError = failure.asStateFlow()

    private val changes = Channel<SettingsChange>(Channel.UNLIMITED)

    fun dismissError() { failure.value = false }
    fun change(change: SettingsChange) { changes.trySend(change) }

    init {
        viewModelScope.launch {
            for (change in changes) persist(change)
        }
    }

    private suspend fun persist(change: SettingsChange) {
            try {
                repository.update { current ->
                    when (change) {
                        is SettingsChange.Mode -> current.copy(themeMode = change.value)
                        is SettingsChange.Palette -> current.copy(colorTheme = change.value)
                        is SettingsChange.Quality -> current.copy(exportQuality = change.value)
                        is SettingsChange.Ocr -> current.copy(ocrEnabled = change.value)
                        is SettingsChange.Numbering -> current.copy(pageNumbers = change.value)
                        is SettingsChange.ImageFormat -> current.copy(imageFormat = change.value)
                        is SettingsChange.Compression -> current.copy(compressionLevel = change.value)
                        SettingsChange.RestoreDefaults -> AppSettings()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure.value = true
            }
    }

    override fun onCleared() {
        changes.close()
        super.onCleared()
    }
}
