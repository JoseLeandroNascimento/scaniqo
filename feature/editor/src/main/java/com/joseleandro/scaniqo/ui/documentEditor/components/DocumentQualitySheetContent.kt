package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.feature.editor.R
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Composable
internal fun DocumentQualitySheetContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onDraftChanged: (Boolean) -> Unit = {},
    onFinish: () -> Unit = {},
) {
    val workspace = state.workspace ?: return
    var quality by rememberSaveable(workspace.workspaceId, workspace.exportQuality) {
        mutableStateOf(workspace.exportQuality)
    }
    var ocrEnabled by rememberSaveable(workspace.workspaceId, workspace.ocrEnabled) {
        mutableStateOf(workspace.ocrEnabled)
    }
    LaunchedEffect(quality, ocrEnabled, workspace.exportQuality, workspace.ocrEnabled) {
        onDraftChanged(quality != workspace.exportQuality || ocrEnabled != workspace.ocrEnabled)
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val expanded = maxWidth >= 600.dp
        val effectiveWidth = (maxWidth.coerceAtMost(840.dp) - 32.dp) / LocalDensity.current.fontScale
        val horizontalChoices = effectiveWidth >= 720.dp
        val stackedContent = effectiveWidth < 240.dp
        Column(
            Modifier.widthIn(max = 840.dp).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    stringResource(R.string.quality_export_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (horizontalChoices) {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PdfExportQuality.entries.forEach { option ->
                            ExportQualityChoice(
                                option = option, selected = quality == option, enabled = state.canEdit,
                                onSelect = { quality = option },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                tile = true,
                            )
                        }
                    }
                } else {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PdfExportQuality.entries.forEach { option ->
                            ExportQualityChoice(
                                option = option, selected = quality == option, enabled = state.canEdit,
                                onSelect = { quality = option }, modifier = Modifier.fillMaxWidth(),
                                tile = stackedContent,
                            )
                        }
                    }
                }
                QualityOcrChoice(ocrEnabled, state.canEdit, onCheckedChange = { ocrEnabled = it }, stacked = stackedContent)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = {
                    onDraftChanged(false)
                    onEvent(DocumentToolsEvent.SetExportOptions(quality, ocrEnabled))
                    onFinish()
                },
                enabled = state.canEdit,
                modifier = (if (expanded) Modifier.widthIn(min = 160.dp).align(Alignment.End)
                    else Modifier.fillMaxWidth()).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.quality_export_apply), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ExportQualityChoice(
    option: PdfExportQuality,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    tile: Boolean = false,
) {
    val title = when (option) {
        PdfExportQuality.HIGH_QUALITY -> R.string.quality_export_high
        PdfExportQuality.BALANCED -> R.string.quality_export_balanced
        PdfExportQuality.SMALL -> R.string.quality_export_small
    }
    val description = when (option) {
        PdfExportQuality.HIGH_QUALITY -> R.string.quality_visual_high_use
        PdfExportQuality.BALANCED -> R.string.quality_visual_balanced_use
        PdfExportQuality.SMALL -> R.string.quality_visual_small_use
    }
    val icon = when (option) {
        PdfExportQuality.HIGH_QUALITY -> CoreResourcesR.drawable.ic_symbol_high_quality
        PdfExportQuality.BALANCED -> CoreResourcesR.drawable.ic_symbol_balance
        PdfExportQuality.SMALL -> CoreResourcesR.drawable.ic_symbol_compress
    }
    val colors = MaterialTheme.colorScheme
    val contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = .38f)
    val label: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(description), style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.onSurfaceVariant else contentColor,
            )
        }
    }
    val leadingIcon: @Composable () -> Unit = {
        Box(Modifier.size(if (tile) 48.dp else 40.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon), contentDescription = null,
                modifier = Modifier.size(if (tile) 32.dp else 28.dp),
                tint = if (enabled && selected) colors.primary else contentColor,
            )
        }
    }
    Surface(
        modifier = modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        contentColor = contentColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        if (tile) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingIcon()
                    RadioButton(selected, onClick = null, enabled = enabled)
                }
                label()
            }
        } else {
            Row(
                Modifier.heightIn(min = 96.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leadingIcon()
                Box(Modifier.weight(1f)) { label() }
                RadioButton(selected, onClick = null, enabled = enabled)
            }
        }
    }
}

@Composable
private fun QualityOcrChoice(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = .38f)
    val label: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.quality_ocr_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.quality_visual_ocr_description),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.onSurfaceVariant else contentColor,
            )
        }
    }
    val leadingIcon: @Composable () -> Unit = {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(CoreResourcesR.drawable.ic_symbol_text_snippet), contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        contentColor = contentColor,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        val contentModifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(16.dp)
        if (stacked) {
            Column(contentModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingIcon()
                    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
                }
                label()
            }
        } else {
            Row(
                contentModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leadingIcon()
                Box(Modifier.weight(1f)) { label() }
                Switch(checked = checked, onCheckedChange = null, enabled = enabled)
            }
        }
    }
}

@Preview(name = "Qualidade claro", widthDp = 390, heightDp = 800, showBackground = true)
@Preview(name = "Qualidade escuro", widthDp = 390, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Qualidade fonte ampliada", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Preview(name = "Qualidade tablet", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Qualidade tablet fonte ampliada", widthDp = 1280, heightDp = 800, fontScale = 1.5f, locale = "pt-rBR")
@Preview(name = "Qualidade janela dividida", widthDp = 600, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Qualidade fonte 2", widthDp = 320, heightDp = 800, fontScale = 2f, locale = "pt-rBR")
@Preview(name = "Qualidade pouca altura", widthDp = 600, heightDp = 400, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun DocumentQualitySheetPreview() {
    ScaniqoTheme {
        Surface {
            Column(Modifier.padding(20.dp)) {
                DocumentQualitySheetContent(qualityPreviewState(), {})
            }
        }
    }
}

private fun qualityPreviewState(): DocumentToolsUiState = DocumentToolsUiState(
    workspace = PdfWorkspace(
        1, "preview", "preview",
        (1..3).map { PdfPageSpec("page-$it", "preview", it - 1) },
        ocrEnabled = true,
    ),
    selectedPageId = "page-1",
    previewUri = "preview",
)
