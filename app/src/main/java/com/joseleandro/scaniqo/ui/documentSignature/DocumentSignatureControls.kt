package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.signatures.SignatureImage

@Composable
internal fun DocumentSignatureControls(
    state: DocumentToolsUiState,
    page: PdfPageSpec,
    draft: PdfSignaturePlacement?,
    adjustmentMode: SignatureAdjustmentMode,
    onAdjustmentModeChange: (SignatureAdjustmentMode) -> Unit,
    onEvent: (DocumentToolsEvent) -> Unit,
    onCreateSignature: () -> Unit,
    onCrop: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    onAdjustmentActiveChange: (Boolean) -> Unit = {},
) {
    Column(modifier) {
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (draft != null) {
                DocumentSignatureAdjustmentPanel(
                    draft = draft,
                    mode = adjustmentMode,
                    enabled = state.canEdit,
                    onModeChange = onAdjustmentModeChange,
                    onEvent = onEvent,
                    onCrop = onCrop,
                    onAdjustmentActiveChange = onAdjustmentActiveChange,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.signature_library), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onCreateSignature, enabled = state.canEdit) {
                        Text(stringResource(R.string.signature_new))
                    }
                }
                TextButton(
                    onClick = { onEvent(DocumentToolsEvent.ManageSignatures) },
                    enabled = state.canEdit,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) { Text(stringResource(R.string.tools_manage_signatures)) }
                if (state.signatures.isEmpty()) {
                    Text(
                        stringResource(R.string.signature_library_empty),
                        Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.signatures, key = { it.id }) { signature ->
                        Surface(
                            onClick = { onEvent(DocumentToolsEvent.InsertSignature(signature.id)) },
                            enabled = state.canEdit,
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.width(144.dp).padding(12.dp)) {
                                SignatureImage(
                                    signature.imageUri,
                                    Modifier.fillMaxWidth().height(48.dp).background(Color.White),
                                )
                                Text(
                                    signature.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                if (page.signatures.isNotEmpty()) {
                    Text(
                        stringResource(R.string.signature_already_placed),
                        Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(page.signatures, key = { it.id }) { signature ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { onEvent(DocumentToolsEvent.EditSignature(page.id, signature.id)) },
                                    enabled = state.canEdit,
                                ) {
                                    Text(stringResource(R.string.signature_edit_placed, page.signatures.indexOf(signature) + 1))
                                }
                                IconButton(
                                    onClick = { onEvent(DocumentToolsEvent.RemoveSignature(page.id, signature.id)) },
                                    enabled = state.canEdit,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.delete_24px),
                                        stringResource(R.string.tools_signature_remove),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // A confirmação permanece acessível mesmo em janelas baixas ou com fonte ampliada.
        if (draft != null) {
            Button(
                onClick = onApply,
                enabled = state.canEdit && !state.isRendering,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
            ) { Text(stringResource(R.string.signature_apply)) }
        }
    }
}
