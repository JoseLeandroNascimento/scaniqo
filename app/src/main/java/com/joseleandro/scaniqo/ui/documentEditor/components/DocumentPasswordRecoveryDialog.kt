package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.ui.security.PasswordUnlockHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A senha original permanece somente em memória, nunca na navegação ou no estado salvo. */
@Composable
internal fun DocumentPasswordRecoveryDialog(
    vault: PasswordVault,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onRecovered: (password: String, keyId: String) -> Unit,
) {
    com.joseleandro.scaniqo.core.security.SecureWindow()
    val state by vault.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var authenticating by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val recoveryError = stringResource(R.string.runtime_recovery_failed)

    fun recover() {
        if (saving || password.isEmpty()) return
        saving = true
        error = null
        scope.launch {
            try {
                // Guarda uma versão independente: a senha geral não muda ao recuperar um PDF.
                val keyId = vault.rememberDocumentPassword(password)
                onRecovered(password, keyId)
                password = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = recoveryError
            } finally {
                saving = false
            }
        }
    }

    if (authenticating) {
        PasswordUnlockHandler(
            vault,
            onUnlocked = { authenticating = false; recover() },
            onDismiss = { authenticating = false; password = "" },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { if (!saving) { password = ""; onDismiss() } },
        title = { Text(stringResource(R.string.runtime_recovery_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.runtime_recovery_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(127); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.runtime_recovery_password)) },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (!state.isConfigured || state.recoveryRequired) {
                    Text(stringResource(R.string.runtime_recovery_setup))
                    TextButton(onClick = { password = ""; onSettings() }) { Text(stringResource(R.string.runtime_password_manage)) }
                }
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = if (saving) stringResource(R.string.runtime_opening) else stringResource(R.string.runtime_open_document),
                onConfirm = { if (state.isUnlocked) recover() else authenticating = true },
                dismissText = stringResource(R.string.runtime_cancel),
                onDismiss = { password = ""; onDismiss() },
                confirmEnabled = !saving && password.isNotEmpty() && state.isConfigured && !state.recoveryRequired,
                dismissEnabled = !saving,
                confirming = saving,
            )
        },
    )
}
