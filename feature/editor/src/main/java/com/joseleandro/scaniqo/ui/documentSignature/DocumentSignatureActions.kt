package com.joseleandro.scaniqo.ui.documentSignature

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentSelectionToolbar
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentSelectionToolbarAction
import com.joseleandro.scaniqo.ui.signatures.SignatureImage
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Composable
internal fun DocumentSignatureContextToolbar(
    enabled: Boolean,
    onReplace: () -> Unit,
    onDuplicate: () -> Unit,
    onCrop: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onAdjust: (() -> Unit)? = null,
) {
    DocumentSelectionToolbar(
        description = stringResource(R.string.signature_actions),
        modifier = modifier.testTag("signature_context_toolbar"),
    ) {
        if (onAdjust != null) DocumentSelectionToolbarAction(
            label = stringResource(R.string.signature_open_adjustments),
            icon = CoreResourcesR.drawable.ic_tool_adjust,
            enabled = enabled,
            onClick = onAdjust,
            modifier = Modifier.testTag("signature_action_adjust"),
            primary = true,
        )
        DocumentSelectionToolbarAction(
            label = stringResource(R.string.signature_context_replace),
            icon = CoreResourcesR.drawable.ic_document_import,
            enabled = enabled,
            onClick = onReplace,
            modifier = Modifier.testTag("signature_action_replace"),
        )
        DocumentSelectionToolbarAction(
            label = stringResource(R.string.signature_duplicate),
            icon = R.drawable.ic_fill_duplicate,
            enabled = enabled,
            onClick = onDuplicate,
            modifier = Modifier.testTag("signature_action_duplicate"),
        )
        DocumentSelectionToolbarAction(
            label = stringResource(R.string.signature_crop),
            icon = CoreResourcesR.drawable.ic_signature_crop,
            enabled = enabled,
            onClick = onCrop,
            modifier = Modifier.testTag("signature_action_crop"),
        )
        DocumentSelectionToolbarAction(
            label = stringResource(R.string.signature_context_cancel),
            icon = CoreResourcesR.drawable.ic_toast_close,
            enabled = enabled,
            onClick = onCancel,
            modifier = Modifier.testTag("signature_action_cancel"),
        )
        DocumentSelectionToolbarAction(
            label = stringResource(R.string.signature_context_delete),
            icon = CoreResourcesR.drawable.delete_24px,
            enabled = enabled,
            onClick = onDelete,
            modifier = Modifier.testTag("signature_action_delete"),
            destructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentSignaturePickerSheet(
    signatures: List<SignatureModel>,
    selectedSignatureId: String?,
    replacing: Boolean,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.testTag("signature_picker_sheet"),
    ) {
        SignaturePickerContent(
            signatures = signatures,
            selectedSignatureId = selectedSignatureId,
            replacing = replacing,
            onSelect = onSelect,
            onCreate = onCreate,
        )
    }
}

@Composable
private fun SignaturePickerContent(
    signatures: List<SignatureModel>,
    selectedSignatureId: String?,
    replacing: Boolean,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(
                if (replacing) R.string.signature_replace else R.string.signature_choose_overlap,
            ),
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        FilledTonalButton(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .heightIn(min = 48.dp).testTag("signature_picker_create"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(CoreResourcesR.drawable.add_24px), contentDescription = null)
                Text(stringResource(R.string.signature_new))
            }
        }
        if (signatures.isEmpty()) {
            Text(
                stringResource(R.string.signature_library_empty),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(signatures, key = { it.id }) { signature ->
                    SignatureChoiceRow(
                        imageUri = signature.imageUri,
                        label = signature.name,
                        onClick = { onSelect(signature.id) },
                        modifier = Modifier.testTag("signature_choice_${signature.id}"),
                        isSelected = signature.id == selectedSignatureId,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentSignaturePlacementSheet(
    placements: List<Pair<Int, PdfSignaturePlacement>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.signature_choose_overlap),
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(placements, key = { it.second.id }) { (index, placement) ->
                    SignatureChoiceRow(
                        imageUri = placement.imageUri,
                        label = stringResource(R.string.signature_edit_placed, index + 1),
                        onClick = { onSelect(placement.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureChoiceRow(
    imageUri: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) colors.secondaryContainer else colors.surfaceContainerLowest,
        contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
        border = BorderStroke(
            1.dp,
            if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = .5f),
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 88.dp)
            .semantics { selected = isSelected },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.3f
            if (stacked) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SignatureChoiceThumbnail(imageUri, Modifier.fillMaxWidth().height(72.dp))
                    SignatureChoiceLabel(label, isSelected, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SignatureChoiceThumbnail(imageUri, Modifier.width(108.dp).height(56.dp))
                    SignatureChoiceLabel(label, isSelected, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SignatureChoiceThumbnail(imageUri: String, modifier: Modifier = Modifier) {
    SignatureImage(
        uri = imageUri,
        modifier = modifier.clip(MaterialTheme.shapes.small).background(Color.White).padding(6.dp),
    )
}

@Composable
private fun SignatureChoiceLabel(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (isSelected) {
            Icon(
                painterResource(CoreResourcesR.drawable.ic_check_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Preview(name = "Escolher assinatura", widthDp = 390, locale = "pt-rBR", showBackground = true)
@Preview(
    name = "Biblioteca escura", widthDp = 390, locale = "pt-rBR",
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true,
)
@Preview(
    name = "Biblioteca com fonte ampliada", widthDp = 320, fontScale = 1.5f,
    locale = "pt-rBR", showBackground = true,
)
@Composable
private fun DocumentSignaturePickerPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            SignaturePickerContent(
                signatures = listOf(
                    SignatureModel("signature-1", "Assinatura pessoal", "preview", 3.3f),
                    SignatureModel("signature-2", "Rubrica", "preview", 2f),
                ),
                selectedSignatureId = "signature-1",
                replacing = false,
                onSelect = {},
                onCreate = {},
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Preview(name = "Barra da assinatura", widthDp = 340, heightDp = 88, locale = "pt-rBR")
@Preview(name = "Barra compacta", widthDp = 320, heightDp = 88, fontScale = 2f, locale = "pt-rBR")
@Preview(
    name = "Barra escura", widthDp = 340, heightDp = 88,
    uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "pt-rBR",
)
@Composable
private fun DocumentSignatureContextToolbarPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                DocumentSignatureContextToolbar(
                    enabled = true,
                    onReplace = {}, onDuplicate = {}, onCrop = {}, onCancel = {}, onDelete = {},
                    onAdjust = {},
                )
            }
        }
    }
}
