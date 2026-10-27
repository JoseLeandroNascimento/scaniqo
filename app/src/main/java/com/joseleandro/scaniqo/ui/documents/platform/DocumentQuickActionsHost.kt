package com.joseleandro.scaniqo.ui.documents.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
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
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeIllustration
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentMerge.DocumentQuickMergeViewModel
import com.joseleandro.scaniqo.ui.documents.components.DocumentSignaturePicker
import com.joseleandro.scaniqo.ui.security.PasswordUnlockHandler

/** Mantém seletores e entregas de rascunhos estáveis durante a navegação e a recriação da Activity. */
@Composable
fun DocumentQuickActionsHost(
    showSignaturePicker: Boolean,
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
    val actionsState by actionsViewModel.uiState.collectAsStateWithLifecycle()
    val latestState by rememberUpdatedState(state)
    val lifecycleOwner = LocalLifecycleOwner.current
    val mergedName = stringResource(R.string.quick_merge_name)
    var deliveredOperationId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(
        state.pending?.pdfUri,
        actionsState.isBusy,
        actionsState.editor == null,
        actionsState.pendingDeletion == null,
    ) {
        state.pending?.let { scan ->
            val operationId = "merge:${scan.pdfUri}"
            if (actionsViewModel.acceptGeneratedDraft(operationId, scan, mergedName, null)) {
                deliveredOperationId = operationId
                mergeViewModel.completeDelivery(scan.pdfUri)
            }
        }
    }
    LaunchedEffect(actionsState.generatedResult, deliveredOperationId) {
        val result = actionsState.generatedResult ?: return@LaunchedEffect
        if (result.operationId == deliveredOperationId) {
            actionsViewModel.acknowledgeGeneratedResult(result.operationId)
            deliveredOperationId = null
            mergeViewModel.finishGenerated(result.operationId, result.documentId)
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
            title = {
                Text(stringResource(if (state.isWorking) R.string.merge_motion_working else R.string.quick_merge_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        state.isWorking -> {
                            DocumentMergeIllustration(
                                selectedCount = 2,
                                isWorking = true,
                                modifier = Modifier.size(width = 200.dp, height = 110.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
                            )
                        }
                        prompt != null -> {
                            val documentName = if (prompt.name == "PDF selecionado") {
                                stringResource(R.string.runtime_selected_pdf)
                            } else prompt.name
                            Text(stringResource(R.string.quick_merge_password, bidiArgument(documentName)))
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
                    state.error?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
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
