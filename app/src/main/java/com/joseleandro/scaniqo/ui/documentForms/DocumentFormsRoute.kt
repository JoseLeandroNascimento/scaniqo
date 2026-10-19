package com.joseleandro.scaniqo.ui.documentForms

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.components.DialogActions
import org.koin.androidx.compose.koinViewModel
import java.util.UUID

@Composable
fun DocumentFormsRoute(
    onBack: () -> Unit,
    viewModel: DocumentFormsViewModel = koinViewModel(),
) {
    val operationId = rememberSaveable { UUID.randomUUID().toString() }
    val currentState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = currentState.takeIf { it.operationId == operationId } ?: DocumentFormsUiState(operationId = operationId)
    LaunchedEffect(operationId) { viewModel.beginOperation(operationId) }
    var launchedExport by rememberSaveable { mutableStateOf<String?>(null) }
    var discardAction by rememberSaveable { mutableStateOf<String?>(null) }
    val fileError = stringResource(R.string.certificate_file_error)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.open(it.toString()) }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.export(it.toString(), launchedExport) }
    }
    val pickFile = {
        try { picker.launch(arrayOf("application/pdf")) }
        catch (_: Exception) { viewModel.reportError(fileError) }
    }
    val save: () -> Unit = {
        state.pendingExport?.let { result ->
            launchedExport = result.id
            try { exporter.launch(result.suggestedName) }
            catch (_: Exception) { viewModel.reportError(fileError) }
        }
    }
    LaunchedEffect(state.pendingExport?.id) {
        if (state.pendingExport != null && state.pendingExport?.id != launchedExport) save()
    }
    val requestBack = {
        if (!state.busy) {
            if (state.dirty) discardAction = "back" else onBack()
        }
    }
    BackHandler(onBack = requestBack)
    DocumentFormsScreen(
        state = state,
        onBack = requestBack,
        onPickPdf = { if (state.dirty) discardAction = "pick" else pickFile() },
        onChange = viewModel::change,
        onSave = { if (state.pendingExport == null) viewModel.prepareExport() else save() },
    )
    if (discardAction != null) {
        AlertDialog(
            onDismissRequest = { discardAction = null },
            title = { Text(stringResource(R.string.form_unsaved_title)) },
            text = { Text(stringResource(R.string.form_unsaved_message)) },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.form_discard),
                    onConfirm = {
                        val action = discardAction
                        discardAction = null
                        if (action == "back") onBack() else pickFile()
                    },
                    dismissText = stringResource(R.string.form_keep_editing),
                    onDismiss = { discardAction = null },
                    destructive = true,
                )
            },
        )
    }
}
