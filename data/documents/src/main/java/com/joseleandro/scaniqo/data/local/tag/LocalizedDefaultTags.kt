package com.joseleandro.scaniqo.data.local.tag

import com.joseleandro.scaniqo.data.documents.R

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import com.joseleandro.scaniqo.domain.model.TagColor
import com.joseleandro.scaniqo.domain.model.TagIcon

/** Os nomes são escolhidos uma vez no idioma atual; alterações posteriores pertencem ao usuário. */
class LocalizedDefaultTags(private val applicationContext: Context) {
    fun tags(): List<TagEntity> {
        val locales = AppCompatDelegate.getApplicationLocales()
        val context = if (locales.isEmpty) applicationContext else
            applicationContext.createConfigurationContext(
                Configuration(applicationContext.resources.configuration).apply {
                    setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
                },
            )
        return listOf(
            Triple(R.string.default_tag_work, TagColor.BLUE, TagIcon.WORK),
            Triple(R.string.default_tag_personal, TagColor.PURPLE, TagIcon.PERSON),
            Triple(R.string.default_tag_studies, TagColor.INDIGO, TagIcon.STUDY),
            Triple(R.string.default_tag_health, TagColor.ROSE, TagIcon.HEALTH),
            Triple(R.string.default_tag_finances, TagColor.EMERALD, TagIcon.MONEY),
            Triple(R.string.default_tag_receipts, TagColor.AMBER, TagIcon.RECEIPT),
            Triple(R.string.default_tag_contracts, TagColor.SLATE, TagIcon.DOCUMENT),
            Triple(R.string.default_tag_home, TagColor.TEAL, TagIcon.HOME),
        ).map { (name, color, icon) ->
            TagEntity(name = context.getString(name), colorArgb = color.argb, iconKey = icon.key)
        }
    }
}
