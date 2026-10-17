package com.joseleandro.scaniqo.ui.documentEditor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.ui.security.PasswordUnlockHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Concentra os efeitos do editor e mantém os contratos Android fora do ViewModel. */
@Composable
fun DocumentToolsHandler(
    viewModel: DocumentToolsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateSignatures: () -> Unit,
    onNavigatePasswordSettings: () -> Unit,
    vault: PasswordVault = koinInject(),
    scanner: GmsDocumentScanner = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val navigateBack by rememberUpdatedState(onNavigateBack)
    val navigateSignatures by rememberUpdatedState(onNavigateSignatures)
    val navigatePasswordSettings by rememberUpdatedState(onNavigatePasswordSettings)
    var launchRequested by rememberSaveable { mutableStateOf(false) }
    var unlockRequested by rememberSaveable { mutableStateOf(false) }
    var recoveryRequested by rememberSaveable { mutableStateOf(false) }
    val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onEvent(
            if (uris.isEmpty()) DocumentToolsEvent.MergePdfsCancelled
            else DocumentToolsEvent.MergePdfsSelected(uris.map { it.toString() }),
        )
    }
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = runCatching {
                GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty().map { it.imageUri.toString() }
            }.getOrDefault(emptyList())
            viewModel.onEvent(if (pages.isEmpty()) DocumentToolsEvent.ScanFailed else DocumentToolsEvent.ScanCompleted(pages))
        } else {
            val failed = result.data?.hasExtra(ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true
            viewModel.onEvent(if (failed) DocumentToolsEvent.ScanFailed else DocumentToolsEvent.ScanCancelled)
        }
    }
    LaunchedEffect(launchRequested, context, lifecycle) {
        if (!launchRequested) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (launchRequested) {
                try {
                    val sender = suspendCancellableCoroutine { continuation ->
                        scanner.getStartScanIntent(requireNotNull(context.findEditorActivity()))
                            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                            .addOnCanceledListener { continuation.cancel() }
                    }
                    scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    launchRequested = false
                } catch (_: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    launchRequested = false
                    viewModel.onEvent(DocumentToolsEvent.ScanCancelled)
                } catch (_: Exception) {
                    launchRequested = false
                    viewModel.onEvent(DocumentToolsEvent.ScanFailed)
                }
            }
        }
    }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    DocumentToolsEffect.LaunchScanner -> launchRequested = true
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

private tailrec fun Context.findEditorActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findEditorActivity()
    else -> null
}
