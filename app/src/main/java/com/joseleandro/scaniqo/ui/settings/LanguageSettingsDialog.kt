package com.joseleandro.scaniqo.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.LocaleListCompat
import androidx.core.text.BidiFormatter
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.AppLanguages
import com.joseleandro.scaniqo.core.localization.localizedDateTime
import com.joseleandro.scaniqo.core.localization.withLocale
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun currentLanguageSummary(): String {
    // Reading configuration makes the label update after system language changes.
    val configuration = LocalConfiguration.current
    val selected = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
    return remember(configuration, selected) { AppLanguages.find(selected) }?.let { "${it.flag} ${it.nativeName}" }
        ?: stringResource(R.string.localization_system)
}

@Composable
internal fun LanguageSettingsDialog(onDismiss: () -> Unit) {
    val selected = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LanguageSettingsContent(
            currentTag = AppLanguages.find(selected)?.tag.orEmpty(),
            onDismiss = onDismiss,
            onApply = { tag ->
                onDismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            },
        )
    }
}

@Composable
private fun LanguageSettingsContent(currentTag: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var selectedTag by rememberSaveable(currentTag) { mutableStateOf(currentTag) }
    val context = LocalContext.current
    val displayLocale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    val systemLocale = if (LocalInspectionMode.current) displayLocale
        else androidx.core.app.LocaleManagerCompat.getSystemLocales(context)[0] ?: Locale.ENGLISH
    val selectedLocale = AppLanguages.find(selectedTag)?.locale ?: systemLocale
    val sampleTime = remember { System.currentTimeMillis() }
    val bidi = remember(displayLocale) { BidiFormatter.getInstance(displayLocale) }
        Surface(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(.92f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.localization_title), style = MaterialTheme.typography.headlineSmall)
                LazyColumn(Modifier.weight(1f).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        LanguageChoice("🌐", stringResource(R.string.localization_system),
                            stringResource(R.string.localization_system_description), selectedTag.isEmpty()) { selectedTag = "" }
                    }
                    items(AppLanguages.supported, key = { it.tag }) { language ->
                        LanguageChoice(
                            language.flag, language.nativeName,
                            bidi.unicodeWrap(language.displayName(displayLocale)),
                            selectedTag == language.tag,
                        ) { selectedTag = language.tag }
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(stringResource(R.string.localization_regional_formats), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.localization_date_example,
                            bidi.unicodeWrap(context.withLocale(selectedLocale).localizedDateTime(sampleTime))),
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.localization_number_example,
                            bidi.unicodeWrap(NumberFormat.getNumberInstance(selectedLocale).format(12345.67))),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.localization_formats_description),
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.localization_direction_description),
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.localization_flag_description),
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                DialogActions(
                    confirmText = stringResource(R.string.localization_apply),
                    onConfirm = { onApply(selectedTag) },
                    dismissText = stringResource(R.string.localization_cancel),
                    onDismiss = onDismiss,
                    confirmEnabled = selectedTag != currentTag,
                )
            }
        }
}

@Composable
private fun LanguageChoice(flag: String, name: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(flag, Modifier.clearAndSetSemantics {}, fontSize = 26.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content))
                if (name != subtitle) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected, onClick = null)
        }
    }
}

@Preview(name = "Languages", widthDp = 390, heightDp = 840, locale = "en-rUS")
@Preview(name = "Arabic large text", widthDp = 320, heightDp = 780, locale = "ar", fontScale = 1.5f)
@Composable
private fun LanguageSettingsPreview() {
    ScaniqoTheme { LanguageSettingsContent("ar-SA", {}, {}) }
}
