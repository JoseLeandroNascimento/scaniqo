package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.joseleandro.scaniqo.ui.components.ScaniqoFieldDefaults
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.feature.editor.R
import com.joseleandro.scaniqo.ui.components.DialogActions

/** Password text deliberately uses remember, never SavedState or rememberSaveable. */
@Composable
internal fun DocumentPdfPasswordDialog(
    busy: Boolean,
    incorrect: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    SecureWindow(enabled = true)
    fun submit() {
        if (!busy && password.isNotEmpty()) {
            onSubmit(password)
            password = ""
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) { password = ""; onDismiss() } },
        title = { Text(stringResource(CoreResourcesR.string.runtime_pdf_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.document_password_open_body))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    label = { Text(stringResource(CoreResourcesR.string.runtime_pdf_password)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    isError = incorrect,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = ScaniqoFieldDefaults.shape,
                )
                if (incorrect) Text(stringResource(R.string.document_password_incorrect), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(CoreResourcesR.string.runtime_open_document),
                onConfirm = ::submit,
                dismissText = stringResource(CoreResourcesR.string.runtime_cancel),
                onDismiss = { password = ""; onDismiss() },
                confirmEnabled = !busy && password.isNotEmpty(),
                dismissEnabled = !busy, confirming = busy,
            )
        },
    )
}
