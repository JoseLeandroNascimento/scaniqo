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
            title = { Text("Importando PDF") },
            text = { Text("Preparando o documento para edição…") },
            confirmButton = {
                if (!state.isSavingToLibrary) TextButton(onClick = onCancel) { Text("Cancelar") }
            },
        )
    } else if (state.needsPassword) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Este PDF tem senha") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Informe a senha atual do arquivo. A proteção será mantida na cópia importada.")
                    OutlinedTextField(
                        password,
                        onValueChange = { onPasswordChange(it.take(127)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha do PDF") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    if (!isPasswordConfigured) {
                        Text("Configure a senha geral para guardar este acesso com a proteção do aparelho.")
                        TextButton(onClick = onPasswordSettings) { Text("Gerenciar senha geral") }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                DialogActions(
                    confirmText = "Importar",
                    onConfirm = onSubmitPassword,
                    dismissText = "Cancelar",
                    onDismiss = onCancel,
                    confirmEnabled = isPasswordConfigured && password.isNotEmpty(),
                )
            },
        )
    } else if (state.error != null) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Não foi possível importar") },
            text = { Text(state.error.orEmpty()) },
            confirmButton = { TextButton(onClick = onCancel) { Text("Fechar") } },
        )
    }
}
