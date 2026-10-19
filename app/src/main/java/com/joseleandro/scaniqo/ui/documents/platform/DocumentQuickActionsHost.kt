package com.joseleandro.scaniqo.ui.documents.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentMerge.DocumentQuickMergeViewModel
import com.joseleandro.scaniqo.ui.documents.components.DocumentSignaturePicker
import com.joseleandro.scaniqo.ui.security.PasswordUnlockHandler

/** Mantém seletores e entregas de rascunhos estáveis durante a navegação e a recriação da Activity. */
@Composable
fun DocumentQuickActionsHost(
    showSignaturePicker: Boolean,
    onMergeCompleted: () -> Unit,
    onDismissSignaturePicker: () -> Unit,
    documents: List<DocumentModel>,
    isLoading: Boolean,
    loadError: Boolean,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onSign: (Long) -> Unit,
    actionsViewModel: DocumentActionsViewModel,
    mergeViewModel: DocumentQuickMergeViewModel,
    vault: PasswordVault,
) {
    val state by mergeViewModel.uiState.collectAsStateWithLifecycle()
    val latestState by rememberUpdatedState(state)
    val onCompleted by rememberUpdatedState(onMergeCompleted)
    val lifecycleOwner = LocalLifecycleOwner.current
    val mergedName = stringResource(R.string.quick_merge_name)
    LaunchedEffect(state.pending?.pdfUri) {
        state.pending?.let { scan ->
            actionsViewModel.onDocumentAction(DocumentActionEvent.ImportPrepared(scan, mergedName))
            mergeViewModel.completeDelivery(scan.pdfUri)
            onCompleted()
        }
    }
    LaunchedEffect(mergeViewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            mergeViewModel.cancellations.collect {
                actionsViewModel.onDocumentAction(DocumentActionEvent.ImportCancelled)
            }
        }
    }
    DisposableEffect(mergeViewModel) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !latestState.needsUnlock) mergeViewModel.backgrounded()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (showSignaturePicker) {
        DocumentSignaturePicker(
            documents, isLoading, loadError, onRetry,
            onImport = { onDismissSignaturePicker(); onImport() },
            onDismiss = onDismissSignaturePicker,
            onSelect = { onDismissSignaturePicker(); onSign(it) },
        )
    }
    SecureWindow(enabled = state.passwordRequest != null || state.needsUnlock)
    if (state.needsUnlock) {
        PasswordUnlockHandler(vault, mergeViewModel::unlocked, mergeViewModel::unlockDismissed)
    } else if (state.isActive && state.pending == null) {
        val prompt = state.passwordRequest
        var password by remember(prompt?.uri, prompt?.incorrect) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = mergeViewModel::cancel,
            title = { Text(stringResource(R.string.quick_merge_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        state.isWorking -> {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.quick_merge_working))
                        }
                        prompt != null -> {
                            Text(stringResource(R.string.quick_merge_password, prompt.name))
                            OutlinedTextField(
                                value = password, onValueChange = { password = it },
                                label = { Text(stringResource(R.string.quick_merge_password_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            if (prompt.incorrect) Text(stringResource(R.string.quick_merge_password_wrong),
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                if (state.isWorking) {
                    TextButton(onClick = { password = ""; mergeViewModel.cancel() }) {
                        Text(stringResource(R.string.cancel))
                    }
                } else {
                    DialogActions(
                        confirmText = stringResource(if (prompt == null) R.string.documents_retry else R.string.quick_merge_continue),
                        onConfirm = {
                            if (prompt == null) mergeViewModel.retry()
                            else { mergeViewModel.submitPassword(password); password = "" }
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { password = ""; mergeViewModel.cancel() },
                    )
                }
            },
        )
    }
}
