package com.joseleandro.scaniqo.core.localization

import java.util.Locale

/** Regional variants; flags identify a region, never every speaker of a language. */
data class AppLanguage(val tag: String, val flag: String) {
    val locale: Locale get() = Locale.forLanguageTag(tag)
    val nativeName: String get() = locale.getDisplayName(locale)
    fun displayName(inLocale: Locale): String = locale.getDisplayName(inLocale)
}

object AppLanguages {
    val supported = listOf(
        AppLanguage("en-US", "🇺🇸"),
        AppLanguage("pt-BR", "🇧🇷"),
        AppLanguage("es-ES", "🇪🇸"),
        AppLanguage("fr-FR", "🇫🇷"),
        AppLanguage("hi-IN", "🇮🇳"),
        AppLanguage("id-ID", "🇮🇩"),
        AppLanguage("bn-BD", "🇧🇩"),
        AppLanguage("zh-CN", "🇨🇳"),
        AppLanguage("ar-SA", "🇸🇦"),
        AppLanguage("ru-RU", "🇷🇺"),
    )

    fun find(tag: String): AppLanguage? {
        if (tag.isBlank()) return null
        val locale = Locale.forLanguageTag(tag)
        return supported.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?: supported.firstOrNull { it.locale.language == locale.language }
    }
}
