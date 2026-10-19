package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R

@Composable
internal fun DocumentDeleteDialog(
    documentName: String,
    isDeleting: Boolean,
    hasError: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                painterResource(R.drawable.delete_24px),
                null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.document_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.document_delete_body, documentName))
                if (hasError) {
                    Text(
                        stringResource(R.string.document_delete_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(if (isDeleting) R.string.document_deleting else R.string.document_delete),
                onConfirm = onConfirm,
                dismissText = stringResource(R.string.document_cancel),
                onDismiss = onDismiss,
                confirmEnabled = !isDeleting,
                dismissEnabled = !isDeleting,
                confirming = isDeleting,
                destructive = true,
            )
        },
    )
}
