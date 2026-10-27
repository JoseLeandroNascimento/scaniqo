package com.joseleandro.scaniqo.ui.documentSignature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.ui.signatures.SignatureImage

@Composable
internal fun DocumentSignatureActionMenu(
    enabled: Boolean,
    onReplace: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(painterResource(R.drawable.more_vert_24px), stringResource(R.string.signature_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.signature_replace)) },
                onClick = { expanded = false; onReplace() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.signature_duplicate)) },
                onClick = { expanded = false; onDuplicate() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.signature_cancel)) },
                onClick = { expanded = false; onCancel() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.signature_delete_selected)) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentSignatureReplaceSheet(
    signatures: List<SignatureModel>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.signature_replace),
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            if (signatures.isEmpty()) {
                Text(
                    stringResource(R.string.signature_library_empty),
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(signatures, key = { it.id }) { signature ->
                        SignatureChoiceRow(signature.imageUri, signature.name) { onSelect(signature.id) }
                    }
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
                        placement.imageUri,
                        stringResource(R.string.signature_edit_placed, index + 1),
                    ) { onSelect(placement.id) }
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
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 64.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SignatureImage(
                imageUri,
                Modifier.width(84.dp).height(40.dp).background(Color.White),
            )
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
