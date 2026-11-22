package com.joseleandro.scaniqo.widget

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.ContextCompat

internal data class WidgetConfiguration(
    val languageTags: String? = null,
    val revision: Long = 0,
)

/** Applied Activity locales are available before AppCompat finishes persisting them on API < 33. */
internal fun widgetContext(context: Context, configuration: WidgetConfiguration): Context {
    val tags = configuration.languageTags ?: return ContextCompat.getContextForLanguage(context)
    val localized = Configuration(context.resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags(tags))
    }
    return context.createConfigurationContext(localized)
}
