package com.joseleandro.scaniqo.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.text.BidiFormatter
import java.util.Locale

/** Isola somente o argumento exibido; o texto armazenado e os campos editáveis ficam intactos. */
@Composable
@ReadOnlyComposable
fun bidiArgument(value: String): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    return bidiArgument(value, locale)
}

/** Usa a locale da frase, mesmo quando ela difere do idioma do sistema. */
internal fun bidiArgument(value: String, locale: Locale): String =
    BidiFormatter.getInstance(locale).unicodeWrap(value) ?: value

