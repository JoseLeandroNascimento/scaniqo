package com.joseleandro.scaniqo.core.localization

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.joseleandro.scaniqo.R

/** Resolve on demand, so an existing ViewModel also sees later language changes. */
class LocalizedNames(private val applicationContext: Context) {
    private fun context(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) applicationContext else applicationContext.createConfigurationContext(
            Configuration(applicationContext.resources.configuration).apply { setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags())) },
        )
    }

    fun document(createdAt: Long): String = context().let {
        it.getString(R.string.runtime_default_document, it.localizedDateTime(createdAt))
    }
    fun importedDocument(): String = context().getString(R.string.runtime_imported_document)
    fun signature(): String = context().getString(R.string.runtime_signature_default_name)
}
