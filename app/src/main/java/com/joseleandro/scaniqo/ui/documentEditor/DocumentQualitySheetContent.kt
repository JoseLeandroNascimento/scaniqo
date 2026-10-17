package com.joseleandro.scaniqo.ui.documentEditor

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfPageQuality
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlin.math.abs

/** Exportação e diagnóstico têm áreas próprias para não sobrecarregar os controles de imagem. */
@Composable
internal fun DocumentQualitySheetContent(state: DocumentToolsUiState, onEvent: (DocumentToolsEvent) -> Unit) {
    val workspace = state.workspace ?: return
    var section by remember { mutableIntStateOf(0) }
    var quality by remember(workspace.exportQuality) { mutableStateOf(workspace.exportQuality) }
    var ocrEnabled by remember(workspace.ocrEnabled) { mutableStateOf(workspace.ocrEnabled) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 660.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            listOf(R.string.quality_export_tab, R.string.quality_review_tab).forEachIndexed { index, title ->
                SegmentedButton(
                    selected = section == index,
                    onClick = { section = index },
                    enabled = !state.isBusy,
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    icon = {},
                    modifier = Modifier.fillMaxHeight().heightIn(min = 52.dp),
                ) { Text(stringResource(title), textAlign = TextAlign.Center) }
            }
        }
        if (section == 0) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.quality_export_title), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PdfExportQuality.entries.forEach { option ->
                            ExportQualityChoice(option, quality == option, state.canEdit) { quality = option }
                        }
                    }
                    QualityHint(R.string.quality_export_hint)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .toggleable(ocrEnabled, enabled = state.canEdit, role = Role.Switch, onValueChange = { ocrEnabled = it }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.quality_ocr_title),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Switch(checked = ocrEnabled, onCheckedChange = null, enabled = state.canEdit)
                    }
                    QualityHint(R.string.quality_ocr_body)
                    if (ocrEnabled) QualityHint(R.string.quality_ocr_hint)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = { onEvent(DocumentToolsEvent.SetExportOptions(quality, ocrEnabled)) },
                    enabled = state.canEdit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.quality_export_apply), textAlign = TextAlign.Center) }
            }
        } else {
            QualityReviewContent(state, onEvent, Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun ExportQualityChoice(
    option: PdfExportQuality,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val title = when (option) {
        PdfExportQuality.HIGH_QUALITY -> R.string.quality_export_high
        PdfExportQuality.BALANCED -> R.string.quality_export_balanced
        PdfExportQuality.SMALL -> R.string.quality_export_small
    }
    val description = when (option) {
        PdfExportQuality.HIGH_QUALITY -> R.string.quality_export_high_body
        PdfExportQuality.BALANCED -> R.string.quality_export_balanced_body
        PdfExportQuality.SMALL -> R.string.quality_export_small_body
    }
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            Modifier.heightIn(min = 80.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected, onClick = null, enabled = enabled)
        }
    }
}

@Composable
private fun QualityReviewContent(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier,
) {
    val workspace = state.workspace ?: return
    val report = state.qualityReport
    val positions = remember(workspace.pages) { workspace.pages.mapIndexed { index, page -> page.id to index + 1 }.toMap() }
    val flagged = remember(report, positions) {
        report?.pages.orEmpty().filter { it.pageId in positions && it.needsReview() }
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.quality_analysis_title), style = MaterialTheme.typography.titleMedium)
                QualityHint(R.string.quality_analysis_body)
            }
        }
        item {
            if (state.isAnalyzing) {
                AnalysisInProgress(onEvent)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onEvent(DocumentToolsEvent.AnalyzeQuality) },
                        enabled = state.canEdit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(if (report == null) R.string.quality_analysis_run else R.string.quality_analysis_again))
                    }
                    if (state.analysisError) {
                        Text(
                            stringResource(R.string.quality_analysis_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.quality_auto_title), style = MaterialTheme.typography.titleMedium)
                QualityHint(R.string.quality_auto_body)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onEvent(DocumentToolsEvent.AutoImprove(false)) },
                        enabled = state.canEdit,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.quality_auto_apply), textAlign = TextAlign.Center) }
                    OutlinedButton(
                        onClick = { onEvent(DocumentToolsEvent.AutoImprove(true)) },
                        enabled = state.canEdit,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.quality_auto_apply_all), textAlign = TextAlign.Center) }
                }
            }
        }
        if (report != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.quality_analysis_count, flagged.size, workspace.pages.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val nativePages = report.pages.count { it.hasNativeText || it.hasNativeVectors }
                    if (nativePages > 0) {
                        Text(
                            pluralStringResource(R.plurals.quality_analysis_native, nativePages, nativePages),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (flagged.isEmpty()) QualityHint(R.string.quality_analysis_clear)
                }
            }
            items(flagged, key = { it.pageId }) { page ->
                QualityPageReport(
                    page = page,
                    position = positions.getValue(page.pageId),
                    duplicatePosition = positions[page.duplicateOfPageId],
                    thumbnail = {
                        DocumentWorkspaceThumbnail(state, page.pageId, onEvent,
                            Modifier.width(44.dp).aspectRatio(595f / 842f))
                    },
                    enabled = state.canEdit,
                    canDelete = workspace.pages.size > 1,
                    onReview = {
                        onEvent(DocumentToolsEvent.SelectPage(page.pageId))
                        onEvent(DocumentToolsEvent.DismissTool)
                    },
                    onDelete = { onEvent(DocumentToolsEvent.RequestDeletePage(page.pageId)) },
                )
            }
        }

    }
}

private fun PdfPageQuality.needsReview(): Boolean =
    isBlank || duplicateOfPageId != null || isBlurry || isLowResolution || mayBeClipped || abs(suggestedStraightenDegrees) >= .1f

@Composable
private fun QualityPageReport(
    page: PdfPageQuality,
    position: Int,
    duplicatePosition: Int?,
    thumbnail: @Composable () -> Unit,
    enabled: Boolean,
    canDelete: Boolean,
    onReview: () -> Unit,
    onDelete: () -> Unit,
) {
    val messages = mutableListOf<String>()
    if (page.isBlank) messages += stringResource(R.string.quality_analysis_blank)
    if (duplicatePosition != null) messages += stringResource(R.string.quality_analysis_duplicate, duplicatePosition)
    if (page.isBlurry) messages += stringResource(R.string.quality_analysis_blurry)
    if (page.isLowResolution) messages += stringResource(R.string.quality_analysis_resolution)
    if (page.mayBeClipped) messages += stringResource(R.string.quality_analysis_clipped)
    if (abs(page.suggestedStraightenDegrees) >= .1f) {
        messages += stringResource(R.string.quality_analysis_angle, page.suggestedStraightenDegrees)
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                thumbnail()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.quality_analysis_page, position), style = MaterialTheme.typography.titleSmall)
                    Text(
                        messages.joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReview, enabled = enabled, modifier = Modifier.heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.quality_analysis_review))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, enabled = enabled && canDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        stringResource(R.string.quality_analysis_delete, position),
                        tint = if (enabled && canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityHint(resource: Int) {
    Text(stringResource(resource), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Preview(name = "Qualidade claro", widthDp = 390, heightDp = 800, showBackground = true)
@Preview(name = "Qualidade escuro", widthDp = 390, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Qualidade fonte ampliada", widthDp = 360, heightDp = 800, fontScale = 1.3f)
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

@Preview(name = "Revisão de qualidade", widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun DocumentQualityReviewPreview() {
    ScaniqoTheme {
        Surface {
            QualityReviewContent(qualityPreviewState(), {}, Modifier.padding(20.dp).heightIn(max = 680.dp))
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
    qualityReport = PdfQualityReport(listOf(
        PdfPageQuality("page-1", isBlurry = true, suggestedStraightenDegrees = -1.2f),
        PdfPageQuality("page-2", isBlank = true),
        PdfPageQuality("page-3", hasNativeText = true),
    )),
)
