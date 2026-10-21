package com.joseleandro.scaniqo.core.localization

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** Use the activity's locale, including per-app language on Android 12 and earlier. */
fun Context.appLocale(): Locale = resources.configuration.locales[0] ?: Locale.ENGLISH

fun Context.localizedDateTime(millis: Long): String {
    val date = Date(millis)
    return DateFormat.getMediumDateFormat(this).format(date) + " " +
        DateFormat.getTimeFormat(this).format(date)
}

fun Context.withLocale(locale: Locale): Context = createConfigurationContext(
    Configuration(resources.configuration).apply { setLocale(locale) },
)

fun localizedPercent(value: Float, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }.format(value)

fun localizedInteger(value: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }.format(value)
