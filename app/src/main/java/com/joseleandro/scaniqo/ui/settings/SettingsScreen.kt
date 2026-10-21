package com.joseleandro.scaniqo.ui.settings

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.*
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.ui.theme.personalizedColorScheme
import org.koin.compose.viewmodel.koinViewModel

enum class SettingsSection { HOME, PERSONALIZATION, PDF }

@Composable
fun SettingsRoute(
    section: SettingsSection,
    onBack: () -> Unit,
    onPersonalization: () -> Unit = {},
    onPdf: () -> Unit = {},
    onBackup: () -> Unit = {},
    onPasswords: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.settings.collectAsStateWithLifecycle()
    val failed by viewModel.saveError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failureText = stringResource(R.string.settings_save_error)
    LaunchedEffect(failed) {
        if (failed) {
            snackbar.showSnackbar(failureText)
            viewModel.dismissError()
        }
    }
    SettingsScreen(preferences, section, viewModel::change, onBack,
        onPersonalization, onPdf, onBackup, onPasswords, snackbarHost = { SnackbarHost(snackbar) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    preferences: AppSettings,
    section: SettingsSection,
    onChange: (SettingsChange) -> Unit,
    onBack: () -> Unit,
    onPersonalization: () -> Unit = {},
    onPdf: () -> Unit = {},
    onBackup: () -> Unit = {},
    onPasswords: () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
) {
    var restore by rememberSaveable { mutableStateOf(false) }
    var languageSettings by rememberSaveable { mutableStateOf(false) }
    if (languageSettings) LanguageSettingsDialog { languageSettings = false }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(when (section) {
                    SettingsSection.HOME -> R.string.settings_title
                    SettingsSection.PERSONALIZATION -> R.string.settings_personalization
                    SettingsSection.PDF -> R.string.settings_pdf_export
                }), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.editor_tool_back))
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (section) {
                SettingsSection.HOME -> {
                    item { PersonalizationEntry(preferences, onPersonalization) }
                    item {
                        SettingsGroup(stringResource(R.string.localization_title)) {
                            SettingsDestination(R.drawable.ic_settings_device, stringResource(R.string.localization_language),
                                currentLanguageSummary()) { languageSettings = true }
                        }
                    }
                    item {
                        SettingsGroup(stringResource(R.string.settings_pdf_export)) {
                            SettingsDestination(
                                R.drawable.ic_tool_quality, stringResource(R.string.settings_pdf_defaults),
                                stringResource(R.string.settings_pdf_summary, stringResource(preferences.exportQuality.title()),
                                    preferences.imageFormat.name), onPdf,
                            )
                        }
                    }
                    item {
                        SettingsGroup(stringResource(R.string.settings_protection)) {
                            SettingsDestination(R.drawable.ic_cloud_backup, stringResource(R.string.backup_title),
                                stringResource(R.string.settings_backup_body), onBackup)
                            SettingsDivider()
                            SettingsDestination(R.drawable.ic_tool_lock, stringResource(R.string.menu_pdf_password),
                                stringResource(R.string.settings_password_body), onPasswords)
                        }
                    }
                    item {
                        TextButton(onClick = { restore = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(painterResource(R.drawable.ic_document_undo), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_restore), textAlign = TextAlign.Center)
                        }
                    }
                }
                SettingsSection.PERSONALIZATION -> {
                    item { AppearancePreview(preferences) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle(stringResource(R.string.settings_appearance))
                            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppThemeMode.entries.forEach { mode ->
                                    ModeChoice(mode, preferences.themeMode == mode,
                                        { onChange(SettingsChange.Mode(mode)) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle(stringResource(R.string.settings_palette))
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                AppColorTheme.entries.chunked(2).forEach { pair ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        pair.forEach { theme ->
                                            PaletteChoice(theme, preferences.colorTheme == theme,
                                                { onChange(SettingsChange.Palette(theme)) }, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                SettingsSection.PDF -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle(stringResource(R.string.settings_quality))
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PdfExportQuality.entries.forEach { quality ->
                                    QualityChoice(quality, preferences.exportQuality == quality) {
                                        onChange(SettingsChange.Quality(quality))
                                    }
                                }
                            }
                            Text(stringResource(R.string.settings_pdf_defaults_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        SettingsGroup(stringResource(R.string.settings_pdf_defaults)) {
                            SettingsToggle(R.drawable.ic_document_search, stringResource(R.string.settings_ocr),
                                stringResource(R.string.settings_ocr_body), preferences.ocrEnabled) { onChange(SettingsChange.Ocr(it)) }
                            SettingsDivider()
                            SettingsToggle(R.drawable.ic_overlay_number, stringResource(R.string.settings_numbering),
                                stringResource(R.string.settings_numbering_body), preferences.pageNumbers) { onChange(SettingsChange.Numbering(it)) }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle(stringResource(R.string.settings_images))
                            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PdfImageFormat.entries.forEach { format ->
                                    OptionCard(format.name, stringResource(if (format == PdfImageFormat.JPEG)
                                        R.string.settings_jpeg_body else R.string.settings_png_body),
                                        preferences.imageFormat == format, Modifier.weight(1f)) {
                                        onChange(SettingsChange.ImageFormat(format))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle(stringResource(R.string.settings_compression))
                            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PdfCompressionLevel.entries.forEach { level ->
                                    OptionCard(stringResource(if (level == PdfCompressionLevel.BALANCED)
                                        R.string.settings_compression_balanced else R.string.settings_compression_strong),
                                        null, preferences.compressionLevel == level, Modifier.weight(1f)) {
                                        onChange(SettingsChange.Compression(level))
                                    }
                                }
                            }
                            Text(stringResource(R.string.settings_utilities_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (section != SettingsSection.HOME) item {
                Text(stringResource(R.string.settings_saved_automatically),
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (restore) AlertDialog(
        onDismissRequest = { restore = false },
        icon = { Icon(painterResource(R.drawable.ic_document_undo), null) },
        title = { Text(stringResource(R.string.settings_restore_title)) },
        text = { Text(stringResource(R.string.settings_restore_body)) },
        confirmButton = {
            DialogActions(stringResource(R.string.settings_restore_confirm),
                { restore = false; onChange(SettingsChange.RestoreDefaults) },
                stringResource(R.string.settings_cancel), { restore = false })
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
}

@Composable
private fun SettingsDestination(@DrawableRes icon: Int, title: String, summary: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.arrow_back_24px), null, Modifier.size(18.dp).rotate(180f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggle(@DrawableRes icon: Int, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange), color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun SettingIcon(@DrawableRes icon: Int) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Icon(painterResource(icon), null, Modifier.padding(10.dp).size(22.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun PersonalizationEntry(settings: AppSettings, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_settings_palette), null, Modifier.size(28.dp))
                Spacer(Modifier.weight(1f))
                AppColorTheme.entries.forEach { theme ->
                    Box(Modifier.padding(start = 4.dp).size(18.dp).clip(CircleShape)
                        .background(personalizedColorScheme(theme, false).primary))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_personalization), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.settings_summary, stringResource(settings.themeMode.title()),
                    stringResource(settings.colorTheme.title())), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_personalization_body), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge)
                Icon(painterResource(R.drawable.arrow_back_24px), null, Modifier.size(20.dp).rotate(180f))
            }
        }
    }
}

@Composable
private fun ModeChoice(mode: AppThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        color = if (selected) colors.primaryContainer else colors.surface) {
        Column(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 6.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(when (mode) {
                AppThemeMode.SYSTEM -> R.drawable.ic_settings_device
                AppThemeMode.LIGHT -> R.drawable.ic_settings_sun
                AppThemeMode.DARK -> R.drawable.ic_settings_moon
            }), null)
            Text(stringResource(mode.title()), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PaletteChoice(theme: AppColorTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = personalizedColorScheme(theme, false)
    Surface(modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))) {
                Box(Modifier.weight(2f).fillMaxHeight().background(scheme.primary))
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.primaryContainer))
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.surfaceContainerHighest))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(theme.title()), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (selected) Icon(painterResource(R.drawable.ic_check_24), stringResource(R.string.settings_selected),
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AppearancePreview(settings: AppSettings) {
    val dark = when (settings.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val scheme = personalizedColorScheme(settings.colorTheme, dark)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainer, contentColor = scheme.onSurface) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Canvas(Modifier.size(68.dp, 90.dp).clip(RoundedCornerShape(8.dp)).background(scheme.surface)) {
                val inset = size.width * .18f
                drawRoundRect(scheme.primary, androidx.compose.ui.geometry.Offset(inset, size.height * .18f),
                    androidx.compose.ui.geometry.Size(size.width * .64f, size.height * .10f),
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                repeat(4) { line ->
                    drawLine(scheme.outlineVariant, androidx.compose.ui.geometry.Offset(inset, size.height * (.4f + .12f * line)),
                        androidx.compose.ui.geometry.Offset(size.width * .82f, size.height * (.4f + .12f * line)), 2.dp.toPx())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_live_preview), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.settings_preview_ready), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(scheme.primary, scheme.primaryContainer, scheme.outlineVariant).forEach { color ->
                        Box(Modifier.size(20.dp).clip(CircleShape).background(color))
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityChoice(value: PdfExportQuality, selected: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(when (value) {
                PdfExportQuality.HIGH_QUALITY -> R.drawable.ic_tag_star
                PdfExportQuality.BALANCED -> R.drawable.ic_tool_quality
                PdfExportQuality.SMALL -> R.drawable.ic_tool_compress
            }), null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(value.title()), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(when (value) {
                    PdfExportQuality.HIGH_QUALITY -> R.string.settings_quality_high_body
                    PdfExportQuality.BALANCED -> R.string.settings_quality_balanced_body
                    PdfExportQuality.SMALL -> R.string.settings_quality_small_body
                }), style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected, onClick = null)
        }
    }
}

@Composable
private fun OptionCard(title: String, subtitle: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 86.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected) Icon(painterResource(R.drawable.ic_check_24), null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun AppThemeMode.title() = when (this) {
    AppThemeMode.SYSTEM -> R.string.settings_mode_system
    AppThemeMode.LIGHT -> R.string.settings_mode_light
    AppThemeMode.DARK -> R.string.settings_mode_dark
}
private fun AppColorTheme.title() = when (this) {
    AppColorTheme.ORIGINAL -> R.string.settings_palette_original
    AppColorTheme.OCEAN -> R.string.settings_palette_ocean
    AppColorTheme.FOREST -> R.string.settings_palette_forest
    AppColorTheme.LAVENDER -> R.string.settings_palette_lavender
    AppColorTheme.ROSE -> R.string.settings_palette_rose
    AppColorTheme.SUNSET -> R.string.settings_palette_sunset
}
private fun PdfExportQuality.title() = when (this) {
    PdfExportQuality.HIGH_QUALITY -> R.string.settings_quality_high
    PdfExportQuality.BALANCED -> R.string.settings_quality_balanced
    PdfExportQuality.SMALL -> R.string.settings_quality_small
}

@Preview(name = "Configurações", widthDp = 390, heightDp = 820, locale = "pt-rBR")
@Preview(name = "Configurações escuro", widthDp = 390, heightDp = 820, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Configurações fonte ampliada", widthDp = 320, heightDp = 720, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun SettingsHomePreview() {
    ScaniqoTheme { SettingsScreen(AppSettings(), SettingsSection.HOME, {}, {}) }
}
@Preview(name = "Personalizar", widthDp = 390, heightDp = 900, locale = "pt-rBR")
@Preview(name = "Personalizar fonte ampliada", widthDp = 320, heightDp = 900, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun PersonalizationPreview() {
    val settings = AppSettings(colorTheme = AppColorTheme.LAVENDER)
    ScaniqoTheme(colorTheme = settings.colorTheme) { SettingsScreen(settings, SettingsSection.PERSONALIZATION, {}, {}) }
}
@Preview(name = "Preferências PDF", widthDp = 390, heightDp = 900, locale = "pt-rBR")
@Composable
private fun PdfSettingsPreview() {
    ScaniqoTheme { SettingsScreen(AppSettings(), SettingsSection.PDF, {}, {}) }
}
