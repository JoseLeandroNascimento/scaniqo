package com.joseleandro.scaniqo.ui.documentEditor.components

import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentMergeUiState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** O acesso aos arquivos e as senhas da mesclagem existem apenas durante esta edição. */
@Composable
internal fun DocumentMergeDialogs(state: DocumentToolsUiState, onEvent: (DocumentToolsEvent) -> Unit) {
    val passwordRequest = state.merge as? DocumentMergeUiState.PasswordRequired
    if (passwordRequest != null) {
        val passwordUri = passwordRequest.uri
        SecureWindow()
        val resolver = LocalContext.current.applicationContext.contentResolver
        val fallbackName = passwordRequest.name ?: stringResource(R.string.merge_tools_selected_file)
        val name by produceState(fallbackName, passwordUri, fallbackName) {
            value = fallbackName
            value = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.query(
                        passwordUri.toUri(),
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null, null, null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0)?.take(160) else null
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallbackName
            }
        }
        key(passwordUri) {
            MergePasswordDialog(
                fileName = name,
                protectedResult = state.workspace?.isProtected == true,
                invalidPassword = passwordRequest.incorrect,
                onSubmit = { onEvent(DocumentToolsEvent.SubmitMergePassword(it)) },
                onCancel = { onEvent(DocumentToolsEvent.CancelMergePassword) },
            )
        }
    } else if (state.merge is DocumentMergeUiState.Merging) {
        AlertDialog(
            onDismissRequest = { onEvent(DocumentToolsEvent.CancelPdfMerge) },
            icon = { CircularProgressIndicator(Modifier.size(28.dp)) },
            title = { Text(stringResource(R.string.merge_tools_progress_title)) },
            text = { Text(stringResource(R.string.merge_tools_progress_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(DocumentToolsEvent.CancelPdfMerge) }) {
                    Text(stringResource(R.string.tools_cancel))
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false),
        )
    }
}

/** A senha não usa rememberSaveable e é apagada ao confirmar ou fechar o diálogo. */
@Composable
private fun MergePasswordDialog(
    fileName: String,
    protectedResult: Boolean,
    invalidPassword: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // Limpa também o campo local quando a coleta do UiState pausa em segundo plano.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                password = ""
                visible = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val submit = {
        if (password.isNotEmpty()) {
            onSubmit(password)
            password = ""
        }
    }
    val cancel = {
        password = ""
        onCancel()
    }
    AlertDialog(
        onDismissRequest = cancel,
        icon = { Icon(painterResource(R.drawable.ic_document_lock), contentDescription = null) },
        title = { Text(stringResource(R.string.merge_tools_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(
                    if (protectedResult) R.string.merge_tools_password_protected
                    else R.string.merge_tools_password_unprotected,
                ))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(127) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.merge_tools_password_label)) },
                    singleLine = true,
                    isError = invalidPassword,
                    supportingText = if (invalidPassword) {
                        { Text(stringResource(R.string.merge_tools_password_error)) }
                    } else null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                contentDescription = stringResource(if (visible) R.string.password_hide else R.string.password_show),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
        },
        confirmButton = {
            DialogActions(
                confirmText = stringResource(R.string.merge_tools_confirm),
                onConfirm = submit,
                dismissText = stringResource(R.string.tools_cancel),
                onDismiss = cancel,
                confirmEnabled = password.isNotEmpty(),
            )
        },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}

@Preview(name = "Senha para mesclar", widthDp = 390, heightDp = 760)
@Composable
private fun MergePasswordDialogPreview() {
    ScaniqoTheme {
        MergePasswordDialog("Contrato.pdf", protectedResult = false, invalidPassword = false, onSubmit = {}, onCancel = {})
    }
}
