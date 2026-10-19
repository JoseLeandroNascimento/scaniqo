package com.joseleandro.scaniqo.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.joseleandro.scaniqo.domain.model.*
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidAppSettingsRepository(context: Context) : AppSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val state = MutableStateFlow(readSettings())
    override val settings = state.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(state.value)
            if (next == state.value) return@withLock
            val saved = preferences.edit()
                .putString("theme_mode", next.themeMode.name)
                .putString("color_theme", next.colorTheme.name)
                .putString("export_quality", next.exportQuality.name)
                .putBoolean("ocr_enabled", next.ocrEnabled)
                .putBoolean("page_numbers", next.pageNumbers)
                .putString("image_format", next.imageFormat.name)
                .putString("compression_level", next.compressionLevel.name)
                .commit()
            if (!saved) throw IOException("Não foi possível salvar as preferências.")
            state.value = next
        }
    }

    private fun readSettings() = AppSettings(
        themeMode = preferences.enum("theme_mode", AppThemeMode.SYSTEM),
        colorTheme = preferences.enum("color_theme", AppColorTheme.ORIGINAL),
        exportQuality = preferences.enum("export_quality", PdfExportQuality.HIGH_QUALITY),
        ocrEnabled = preferences.boolean("ocr_enabled"),
        pageNumbers = preferences.boolean("page_numbers"),
        imageFormat = preferences.enum("image_format", PdfImageFormat.JPEG),
        compressionLevel = preferences.enum("compression_level", PdfCompressionLevel.BALANCED),
    )
}

private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, fallback: T): T =
    runCatching { getString(key, null)?.let { enumValueOf<T>(it) } }.getOrNull() ?: fallback

private fun SharedPreferences.boolean(key: String): Boolean =
    runCatching { getBoolean(key, false) }.getOrDefault(false)
