package com.joseleandro.scaniqo.ui.documents.platform

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import androidx.compose.runtime.rememberUpdatedState
import com.joseleandro.scaniqo.ui.documents.PdfImportViewModel
import com.joseleandro.scaniqo.ui.documents.model.PdfImportDestination
import com.joseleandro.scaniqo.ui.documents.components.PdfImportDialogs
import com.joseleandro.scaniqo.ui.documents.effect.PdfImportEffect
import com.joseleandro.scaniqo.ui.security.PasswordUnlockHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** O seletor fornece acesso somente ao PDF escolhido; não exige permissão de armazenamento. */
@Composable
fun PdfImportHandler(
    viewModel: PdfImportViewModel,
    actionsViewModel: DocumentActionsViewModel,
    defaultTagId: Long?,
    vault: PasswordVault,
    showDialogs: Boolean,
    onPasswordSettings: () -> Unit,
    onToolImported: (Long, PdfImportDestination) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTagId by rememberUpdatedState(defaultTagId)
    val toolImported by rememberUpdatedState(onToolImported)
    val vaultState by vault.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var password by remember(state.needsPassword) { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val resolver = context.applicationContext.contentResolver
        // A consulta pertence ao ViewModel; a rotação não perde o retorno do seletor.
        viewModel.fileSelected(uri?.toString()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    uri?.let { selected ->
                        resolver.query(selected, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }
                }.getOrNull().orEmpty()
            }
        }
    }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PdfImportEffect.PickFile -> {
                        actionsViewModel.onDocumentAction(DocumentActionEvent.ImportStarted(defaultTagId = selectedTagId))
                        try { picker.launch(arrayOf("application/pdf")) }
                        catch (_: Exception) { viewModel.cancel() }
                    }
                    PdfImportEffect.Cancelled -> {
                        password = ""
                        actionsViewModel.onDocumentAction(DocumentActionEvent.ImportCancelled)
                    }
                }
            }
        }
    }
    LaunchedEffect(state.pending, state.isLoading) {
        state.pending?.let { pending ->
            password = ""
            if (state.destination == null) {
                actionsViewModel.onDocumentAction(DocumentActionEvent.ImportPrepared(pending.scan, pending.name))
                viewModel.completeDelivery(pending.scan.pdfUri)
            } else if (!state.isLoading) viewModel.saveForTool()
        }
    }
    LaunchedEffect(state.importedDocumentId, state.destination) {
        val id = state.importedDocumentId
        val destination = state.destination
        if (id != null && destination != null) {
            password = ""
            actionsViewModel.onDocumentAction(DocumentActionEvent.ImportCancelled)
            toolImported(id, destination)
            viewModel.acknowledgeToolDelivery(id)
        }
    }
    if (!showDialogs || !state.isActive || (state.pending != null && state.destination == null) ||
        state.importedDocumentId != null) return
    SecureWindow(enabled = state.needsPassword || state.needsUnlock)
    if (state.needsUnlock) {
        PasswordUnlockHandler(
            vault = vault,
            onUnlocked = viewModel::unlocked,
            onDismiss = viewModel::unlockDismissed,
        )
    } else {
        PdfImportDialogs(
            state = state,
            password = password,
            isPasswordConfigured = vaultState.isConfigured,
            onPasswordChange = { password = it },
            onSubmitPassword = {
                viewModel.submitPassword(password)
                password = ""
            },
            onPasswordSettings = {
                password = ""
                onPasswordSettings()
            },
            onCancel = viewModel::cancel,
        )
    }
}
