package com.joseleandro.scaniqo.ui.settings

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.settings.R

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.core.text.BidiFormatter
import com.joseleandro.scaniqo.core.localization.AppLanguages
import com.joseleandro.scaniqo.core.localization.localizedDateTime
import com.joseleandro.scaniqo.core.localization.withLocale
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
fun LanguageSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
    LanguageSettingsScreen(
        currentTag = AppLanguages.find(selected)?.tag.orEmpty(),
        onBack = onBack,
        onApply = { tag ->
            onBack()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageSettingsScreen(
    currentTag: String,
    onBack: () -> Unit,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTag by rememberSaveable(currentTag) { mutableStateOf(currentTag) }
    val context = LocalContext.current
    val displayLocale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    val systemLocale = if (LocalInspectionMode.current) displayLocale
        else androidx.core.app.LocaleManagerCompat.getSystemLocales(context)[0] ?: Locale.ENGLISH
    val selectedLocale = AppLanguages.find(selectedTag)?.locale ?: systemLocale
    val sampleTime = remember { System.currentTimeMillis() }
    val bidi = remember(displayLocale) { BidiFormatter.getInstance(displayLocale) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.localization_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(CoreResourcesR.drawable.arrow_back_24px),
                            stringResource(CoreResourcesR.string.editor_tool_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface {
                Box(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        )),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = { onApply(selectedTag) },
                        enabled = selectedTag != currentTag,
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.localization_apply))
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().selectableGroup(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "system") {
                    LanguageChoice(
                        "🌐", stringResource(R.string.localization_system),
                        stringResource(R.string.localization_system_description), selectedTag.isEmpty(),
                        onClick = { selectedTag = "" },
                    )
                }
                items(AppLanguages.supported, key = { it.tag }) { language ->
                    LanguageChoice(
                        language.flag, language.nativeName,
                        bidi.unicodeWrap(language.displayName(displayLocale)),
                        selectedTag == language.tag,
                        onClick = { selectedTag = language.tag },
                    )
                }
                item(key = "regional_formats") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(
                            stringResource(R.string.localization_regional_formats),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.localization_date_example,
                                bidi.unicodeWrap(context.withLocale(selectedLocale).localizedDateTime(sampleTime))),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.localization_number_example,
                                bidi.unicodeWrap(NumberFormat.getNumberInstance(selectedLocale).format(12345.67))),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(stringResource(R.string.localization_formats_description),
                            style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.localization_direction_description),
                            style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.localization_flag_description),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageChoice(
    flag: String,
    name: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth().heightIn(min = 64.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
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

@Preview(name = "Idiomas no celular", widthDp = 390, heightDp = 840, locale = "pt-rBR")
@Preview(name = "Idiomas no tablet", widthDp = 1024, heightDp = 768, locale = "pt-rBR")
@Preview(name = "Idiomas em janela horizontal", widthDp = 760, heightDp = 360, locale = "pt-rBR")
@Preview(name = "Árabe com fonte ampliada", widthDp = 320, heightDp = 780, locale = "ar", fontScale = 1.5f)
@Composable
private fun LanguageSettingsPreview() {
    ScaniqoTheme { LanguageSettingsScreen("pt-BR", {}, {}) }
}
