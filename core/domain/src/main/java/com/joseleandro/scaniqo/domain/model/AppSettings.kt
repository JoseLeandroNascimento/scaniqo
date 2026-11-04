package com.joseleandro.scaniqo.domain.model

enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class AppColorTheme { ORIGINAL, OCEAN, FOREST, LAVENDER, ROSE, SUNSET }

/** Preferências de novas operações; um projeto salvo conserva suas próprias opções. */
data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorTheme: AppColorTheme = AppColorTheme.ORIGINAL,
    val exportQuality: PdfExportQuality = PdfExportQuality.HIGH_QUALITY,
    val ocrEnabled: Boolean = false,
    val pageNumbers: Boolean = false,
    val imageFormat: PdfImageFormat = PdfImageFormat.JPEG,
    val compressionLevel: PdfCompressionLevel = PdfCompressionLevel.BALANCED,
)
