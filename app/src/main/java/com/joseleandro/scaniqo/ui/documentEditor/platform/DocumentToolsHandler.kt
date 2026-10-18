package com.joseleandro.scaniqo.ui.documentEditor.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.ui.documentCommon.platform.rememberDocumentScannerLauncher
import com.joseleandro.scaniqo.ui.documentEditor.DocumentToolsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentMergeDialogs
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentPasswordRecoveryDialog
import com.joseleandro.scaniqo.ui.documentEditor.effect.DocumentToolsEffect
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.security.PasswordUnlockHandler
import org.koin.compose.koinInject

/** Concentra os efeitos do editor e mantém os contratos Android fora do ViewModel. */
@Composable
fun DocumentToolsHandler(
    viewModel: DocumentToolsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateSignatures: () -> Unit,
    onNavigateSignatureEditor: (Long) -> Unit,
    onNavigateMergeEditor: (Long) -> Unit,
    onNavigateEditorTool: (Long, DocumentTool) -> Unit,
    onEditorToolApplied: (Long, DocumentTool) -> Unit,
    onMergeCompleted: (Long) -> Unit,
    onNavigatePasswordSettings: () -> Unit,
    vault: PasswordVault = koinInject(),
    scanner: GmsDocumentScanner = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val navigateBack by rememberUpdatedState(onNavigateBack)
    val navigateSignatures by rememberUpdatedState(onNavigateSignatures)
    val navigateSignatureEditor by rememberUpdatedState(onNavigateSignatureEditor)
    val navigateMergeEditor by rememberUpdatedState(onNavigateMergeEditor)
    val mergeCompleted by rememberUpdatedState(onMergeCompleted)
    val navigateEditorTool by rememberUpdatedState(onNavigateEditorTool)
    val editorToolApplied by rememberUpdatedState(onEditorToolApplied)
    val navigatePasswordSettings by rememberUpdatedState(onNavigatePasswordSettings)
    var unlockRequested by rememberSaveable { mutableStateOf(false) }
    var recoveryRequested by rememberSaveable { mutableStateOf(false) }
    val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onEvent(
            if (uris.isEmpty()) DocumentToolsEvent.MergePdfsCancelled
            else DocumentToolsEvent.MergePdfsSelected(uris.map { it.toString() }),
        )
    }
    val launchScanner = rememberDocumentScannerLauncher(
        scanner = scanner,
        onCompleted = { pages -> viewModel.onEvent(DocumentToolsEvent.ScanCompleted(pages)) },
        onCancelled = { viewModel.onEvent(DocumentToolsEvent.ScanCancelled) },
        onFailed = { viewModel.onEvent(DocumentToolsEvent.ScanFailed) },
    )
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    DocumentToolsEffect.LaunchScanner -> launchScanner()
                    DocumentToolsEffect.LaunchPdfMergePicker -> {
                        try {
                            // Usa apenas o acesso concedido aos arquivos selecionados pelo usuário.
                            mergePicker.launch(arrayOf("application/pdf"))
                        } catch (_: Exception) {
                            viewModel.onEvent(DocumentToolsEvent.MergePdfsFailed)
                        }
                    }
                    DocumentToolsEffect.NeedUnlock -> unlockRequested = true
                    DocumentToolsEffect.NeedDocumentPassword -> recoveryRequested = true
                    DocumentToolsEffect.NavigateSignatures -> navigateSignatures()
                    is DocumentToolsEffect.NavigateSignatureEditor -> navigateSignatureEditor(effect.documentId)
                    is DocumentToolsEffect.NavigateMergeEditor -> navigateMergeEditor(effect.documentId)
                    is DocumentToolsEffect.MergeCompleted -> mergeCompleted(effect.documentId)
                    is DocumentToolsEffect.NavigateEditorTool -> navigateEditorTool(effect.documentId, effect.tool)
                    is DocumentToolsEffect.EditorToolApplied -> editorToolApplied(effect.documentId, effect.tool)
                    DocumentToolsEffect.NavigatePasswordSettings -> navigatePasswordSettings()
                    DocumentToolsEffect.NavigateBack -> navigateBack()
                }
            }
        }
    }
    DisposableEffect(viewModel) {
        val process = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        }
        process.addObserver(observer)
        onDispose { process.removeObserver(observer) }
    }
    if (!state.isLocked && !unlockRequested && !recoveryRequested) {
        DocumentMergeDialogs(state, viewModel::onEvent)
    }
    if (recoveryRequested) {
        DocumentPasswordRecoveryDialog(
            vault = vault,
            onDismiss = { recoveryRequested = false },
            onSettings = {
                recoveryRequested = false
                navigatePasswordSettings()
            },
            onRecovered = { password, keyId ->
                recoveryRequested = false
                viewModel.onEvent(DocumentToolsEvent.RecoverWithPassword(password, keyId))
            },
        )
    }
    if (unlockRequested) {
        PasswordUnlockHandler(
            vault,
            onUnlocked = {
                unlockRequested = false
                viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
            },
            onDismiss = {
                unlockRequested = false
                viewModel.onEvent(DocumentToolsEvent.AuthenticationDismissed)
            },
        )
    }
}
