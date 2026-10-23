package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfTextHorizontalAlignment
import com.joseleandro.scaniqo.domain.model.PdfTextVerticalAlignment
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** O alinhamento altera o conteúdo da caixa, sem deslocar a caixa na página. */
@Composable
internal fun FillMarkTextAlignmentControls(
    draft: PdfOverlay,
    editable: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.fill_text_alignment), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fill_text_horizontal), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp))
            listOf(
                Triple(PdfTextHorizontalAlignment.START, R.drawable.ic_fill_text_start, R.string.fill_text_start),
                Triple(PdfTextHorizontalAlignment.CENTER, R.drawable.ic_fill_text_center, R.string.fill_text_center),
                Triple(PdfTextHorizontalAlignment.END, R.drawable.ic_fill_text_end, R.string.fill_text_end),
            ).forEach { (alignment, icon, label) ->
                FilledTonalIconToggleButton(
                    checked = draft.textHorizontalAlignment == alignment,
                    onCheckedChange = { onUpdate(draft.copy(textHorizontalAlignment = alignment)) },
                    enabled = editable,
                ) {
                    Icon(painterResource(icon), stringResource(label))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fill_text_vertical), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp))
            listOf(
                Triple(PdfTextVerticalAlignment.TOP, R.drawable.ic_fill_text_top, R.string.fill_text_top),
                Triple(PdfTextVerticalAlignment.CENTER, R.drawable.ic_fill_text_middle, R.string.fill_text_middle),
                Triple(PdfTextVerticalAlignment.BOTTOM, R.drawable.ic_fill_text_bottom, R.string.fill_text_bottom),
            ).forEach { (alignment, icon, label) ->
                FilledTonalIconToggleButton(
                    checked = draft.textVerticalAlignment == alignment,
                    onCheckedChange = { onUpdate(draft.copy(textVerticalAlignment = alignment)) },
                    enabled = editable,
                ) {
                    Icon(painterResource(icon), stringResource(label))
                }
            }
        }
        if (draft.kind == PdfOverlayKind.TEXT) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = draft.autoSizeText, enabled = editable, role = Role.Switch,
                    onValueChange = { onUpdate(draft.copy(autoSizeText = it, wrapText = true)) },
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.fill_text_auto_box), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Switch(checked = draft.autoSizeText, onCheckedChange = null, enabled = editable)
            }
        }
    }
}

@Composable
internal fun FillMarkTextAlignmentMenu(
    draft: PdfOverlay,
    editable: Boolean,
    onUpdate: (PdfOverlay) -> Unit,
) {
    var expanded by remember(draft.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = editable) {
            Icon(
                painterResource(when (draft.textHorizontalAlignment) {
                    PdfTextHorizontalAlignment.START -> R.drawable.ic_fill_text_start
                    PdfTextHorizontalAlignment.CENTER -> R.drawable.ic_fill_text_center
                    PdfTextHorizontalAlignment.END -> R.drawable.ic_fill_text_end
                }),
                stringResource(R.string.fill_text_align_action),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(280.dp),
        ) {
            FillMarkTextAlignmentControls(
                draft = draft, editable = editable, onUpdate = onUpdate,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Preview(name = "Texto — alinhamento", widthDp = 320, locale = "pt-rBR", showBackground = true)
@Preview(name = "Texto — alinhamento ampliado", widthDp = 320, fontScale = 1.3f, locale = "pt-rBR", showBackground = true)
@Preview(name = "Texto — alinhamento escuro", widthDp = 320, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun FillMarkTextAlignmentPreview() {
    ScaniqoTheme {
        Surface {
            FillMarkTextAlignmentControls(
                draft = PdfOverlay(
                    id = "alignment-preview", kind = PdfOverlayKind.TEXT, text = "Maria Silva",
                    textHorizontalAlignment = PdfTextHorizontalAlignment.CENTER,
                    textVerticalAlignment = PdfTextVerticalAlignment.CENTER,
                    autoSizeText = true, wrapText = true,
                ),
                editable = true, onUpdate = {}, modifier = Modifier.padding(16.dp),
            )
        }
    }
}
