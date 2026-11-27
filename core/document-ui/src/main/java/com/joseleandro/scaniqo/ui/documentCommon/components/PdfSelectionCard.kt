package com.joseleandro.scaniqo.ui.documentCommon.components

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import com.joseleandro.scaniqo.ui.components.ScaniqoLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.documentui.R
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.ui.components.ScaniqoCardDefaults

/** A apresentação é compartilhada; seleção, ordem e ações continuam pertencendo ao fluxo. */
@Composable
fun PdfSelectionCard(
    name: String,
    metadata: String,
    thumbnail: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(ScaniqoCardDefaults.shape).then(
            if (onClick == null) Modifier else Modifier.clickable(
                enabled = enabled, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick,
            ),
        ),
        shape = ScaniqoCardDefaults.shape,
        color = ScaniqoCardDefaults.containerColor,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else ScaniqoCardDefaults.border,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leading?.invoke()
                thumbnail(Modifier.width(48.dp).aspectRatio(595f / 842f).clip(MaterialTheme.shapes.small))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr),
                        maxLines = if (LocalDensity.current.fontScale > 1.3f) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        metadata, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trailing?.invoke()
            }
            footer?.invoke()
        }
    }
}

@Composable
fun PdfSelectAction(
    title: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int = CoreResourcesR.drawable.ic_symbol_file_open,
    vertical: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = ScaniqoCardDefaults.shape,
        color = ScaniqoCardDefaults.containerColor,
        border = ScaniqoCardDefaults.border,
    ) {
        val iconContent: @Composable () -> Unit = {
            Icon(painterResource(icon), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        val labelContent: @Composable () -> Unit = {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (vertical) {
            Column(Modifier.heightIn(min = 136.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                iconContent()
                labelContent()
            }
        } else {
            Row(
                Modifier.heightIn(min = 88.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                iconContent()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { labelContent() }
            }
        }
    }
}

@Composable
fun PdfSourceSelection(
    document: DocumentModel?,
    emptyTitle: String,
    emptyHint: String,
    changeLabel: String,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (document == null) {
        PdfSelectAction(emptyTitle, emptyHint, onChoose, modifier, enabled)
    } else {
        PdfSelectionCard(
            name = document.name,
            metadata = pdfSelectionMetadata(document.pageCount, document.sizeBytes),
            thumbnail = { DocumentThumbnail(document, it) },
            modifier = modifier,
            enabled = enabled,
            onClick = onChoose,
            onClickLabel = changeLabel,
            footer = {
                Row(
                    Modifier.heightIn(min = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(CoreResourcesR.drawable.ic_symbol_swap_horiz), null,
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(changeLabel, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }
}

@Composable
fun pdfSelectionMetadata(pageCount: Int?, sizeBytes: Long? = null): String {
    val pages = pageCount?.let { pluralStringResource(CoreResourcesR.plurals.document_page_count, it, it) }
        ?: stringResource(CoreResourcesR.string.merge_selection_external)
    return if (sizeBytes != null && sizeBytes > 0L) {
        pages + " · " + Formatter.formatShortFileSize(LocalContext.current, sizeBytes)
    } else pages
}

@Composable
internal fun PdfSelectionFeedback(
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ScaniqoCardDefaults.shape,
        color = ScaniqoCardDefaults.containerColor,
        border = ScaniqoCardDefaults.border,
    ) {
        Column(
            Modifier.heightIn(min = 112.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) ScaniqoLoadingIndicator()
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRetry != null) TextButton(onClick = onRetry, enabled = enabled) {
                Text(stringResource(CoreResourcesR.string.documents_retry))
            }
        }
    }
}

@Preview(name = "Seleção PDF", widthDp = 390, locale = "pt-rBR")
@Preview(name = "Seleção PDF escuro", widthDp = 390, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Seleção PDF fonte ampliada", widthDp = 320, locale = "pt-rBR", fontScale = 2f)
@Composable
private fun PdfSelectionCardPreview() {
    val document = DocumentModel(1, "Contrato de prestação de serviços", "preview", 6, 0, 1_250_000)
    ScaniqoTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PdfSourceSelection(document, "", "", stringResource(R.string.merge_source_device), {})
                PdfSelectionCard(
                    name = document.name,
                    metadata = pdfSelectionMetadata(document.pageCount, document.sizeBytes),
                    thumbnail = { DocumentThumbnail(document, it) },
                    selected = true,
                    trailing = { Checkbox(checked = true, onCheckedChange = null) },
                )
                PdfSelectAction(
                    stringResource(R.string.merge_source_device),
                    stringResource(R.string.tool_source_device_hint), {},
                )
            }
        }
    }
}

@Preview(name = "Seleção PDF indisponível", widthDp = 320, locale = "pt-rBR", fontScale = 1.5f)
@Preview(name = "Seleção PDF indisponível escuro", widthDp = 390, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PdfSelectionFeedbackPreview() {
    ScaniqoTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PdfSelectionFeedback("", loading = true)
            PdfSelectionFeedback(stringResource(CoreResourcesR.string.documents_error_title), onRetry = {})
            PdfSelectionFeedback(stringResource(R.string.tool_source_library_empty))
        }
    }
}
