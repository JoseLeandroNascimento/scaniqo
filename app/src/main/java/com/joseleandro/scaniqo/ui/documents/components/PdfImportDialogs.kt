package com.joseleandro.scaniqo.ui.documents.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import com.joseleandro.scaniqo.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.documents.state.PdfImportUiState

@Composable
internal fun PdfImportDialogs(
    state: PdfImportUiState,
    password: String,
    isPasswordConfigured: Boolean,
    onPasswordChange: (String) -> Unit,
    onSubmitPassword: () -> Unit,
    onPasswordSettings: () -> Unit,
    onCancel: () -> Unit,
) {
    if (state.isLoading) {
        AlertDialog(
            onDismissRequest = {},
            icon = { CircularProgressIndicator(Modifier.size(28.dp)) },
            title = { Text(stringResource(R.string.runtime_pdf_import_title)) },
            text = { Text(stringResource(R.string.runtime_pdf_import_progress)) },
            confirmButton = {
                if (!state.isSavingToLibrary) TextButton(onClick = onCancel) { Text(stringResource(R.string.runtime_cancel)) }
            },
        )
    } else if (state.needsPassword) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.runtime_pdf_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.runtime_pdf_password_body))
                    OutlinedTextField(
                        password,
                        onValueChange = { onPasswordChange(it.take(127)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.runtime_pdf_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    if (!isPasswordConfigured) {
                        Text(stringResource(R.string.runtime_pdf_password_setup))
                        TextButton(onClick = onPasswordSettings) { Text(stringResource(R.string.runtime_password_manage)) }
                    }
                    state.error?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.runtime_import),
                    onConfirm = onSubmitPassword,
                    dismissText = stringResource(R.string.runtime_cancel),
                    onDismiss = onCancel,
                    confirmEnabled = isPasswordConfigured && password.isNotEmpty(),
                )
            },
        )
    } else if (state.error != null) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.runtime_import_failed)) },
            text = { Text(localizedRuntimeMessage(state.error.orEmpty())) },
            confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.runtime_close)) } },
        )
    }
}
