package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.joseleandro.scaniqo.ui.components.ScaniqoFieldDefaults
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.core.security.PasswordPolicy
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.feature.editor.R
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState

@Composable
internal fun DocumentProtectionOptions(
    state: DocumentToolsUiState,
    onEvent: (DocumentToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onDraftChanged: (Boolean) -> Unit = {},
    onFinish: () -> Unit = {},
) {
    val workspace = state.workspace ?: return
    var password by remember(workspace.workspaceId) { mutableStateOf("") }
    var confirmation by remember(workspace.workspaceId) { mutableStateOf("") }
    var submitted by remember(workspace.workspaceId) { mutableStateOf(false) }
    val initialRevision = remember(workspace.workspaceId) { state.protectionRevision }
    val validLength = password.isNotBlank() && PasswordPolicy.isValidDocument(password)
    val mismatch = confirmation.isNotEmpty() && password != confirmation
    val draftChanged by rememberUpdatedState(onDraftChanged)
    val finish by rememberUpdatedState(onFinish)
    SecureWindow(enabled = true)
    LaunchedEffect(password, confirmation) { draftChanged(password.isNotEmpty() || confirmation.isNotEmpty()) }
    LaunchedEffect(state.protectionRevision) {
        if (submitted && state.protectionRevision != initialRevision) {
            password = ""; confirmation = ""; submitted = false
            draftChanged(false)
            finish()
        }
    }
    LaunchedEffect(state.protectionError) { if (state.protectionError) submitted = false }
    DisposableEffect(Unit) { onDispose { draftChanged(false) } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.document_password_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (workspace.isProtected) R.string.document_password_protected else R.string.document_password_unprotected),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.document_password_body))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(), enabled = state.canEdit,
                    label = { Text(stringResource(CoreResourcesR.string.runtime_pdf_password)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = password.isNotEmpty() && !validLength,
                    supportingText = { if (password.isNotEmpty() && !validLength) Text(stringResource(R.string.document_password_length)) },
                    shape = ScaniqoFieldDefaults.shape,
                )
                OutlinedTextField(
                    value = confirmation, onValueChange = { confirmation = it },
                    modifier = Modifier.fillMaxWidth(), enabled = state.canEdit,
                    label = { Text(stringResource(R.string.document_password_confirm)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = mismatch,
                    supportingText = { if (mismatch) Text(stringResource(R.string.document_password_mismatch)) },
                    shape = ScaniqoFieldDefaults.shape,
                )
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                    Text(stringResource(R.string.document_password_keep), Modifier.padding(16.dp))
                }
                Text(stringResource(R.string.document_password_saved_notice),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.protectionError) Text(stringResource(R.string.tools_save_error), color = MaterialTheme.colorScheme.error)
                if (workspace.isProtected) {
                    OutlinedButton(
                        onClick = { submitted = true; onEvent(DocumentToolsEvent.SetProtection(false)) },
                        modifier = if (expanded) Modifier.align(Alignment.End).heightIn(min = 48.dp)
                            else Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = state.canEdit && !submitted,
                    ) { Text(stringResource(R.string.document_password_remove)) }
                }
            }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Button(
                onClick = {
                    submitted = true
                    onEvent(DocumentToolsEvent.SetDocumentPassword(password, confirmation))
                },
                modifier = (if (expanded) Modifier.widthIn(min = 160.dp, max = 320.dp) else Modifier.fillMaxWidth()).heightIn(min = 48.dp),
                enabled = state.canEdit && !submitted && validLength && password == confirmation,
            ) {
                Text(stringResource(if (workspace.isProtected) R.string.document_password_change else R.string.document_password_apply))
            }
            }
        }
    }
    }
}
