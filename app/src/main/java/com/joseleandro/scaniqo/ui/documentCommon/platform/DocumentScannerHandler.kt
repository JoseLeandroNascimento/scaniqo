package com.joseleandro.scaniqo.ui.documentCommon.platform

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

/** Compartilha o contrato Android do scanner sem depender dos eventos de uma tela. */
@Composable
internal fun rememberDocumentScannerLauncher(
    scanner: GmsDocumentScanner,
    onCompleted: (List<String>) -> Unit,
    onCancelled: () -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val completed by rememberUpdatedState(onCompleted)
    val cancelled by rememberUpdatedState(onCancelled)
    val failed by rememberUpdatedState(onFailed)
    var scannerRequested by rememberSaveable { mutableStateOf(false) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = runCatching {
                GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                    ?.pages.orEmpty().map { it.imageUri.toString() }
            }.getOrDefault(emptyList())
            if (pages.isEmpty()) failed() else completed(pages)
        } else {
            val launchFailed = result.data?.hasExtra(
                ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION,
            ) == true
            if (launchFailed) failed() else cancelled()
        }
    }

    // A solicitação sobrevive à rotação enquanto o Google Play Services prepara o scanner.
    LaunchedEffect(scannerRequested, scanner, context, lifecycleOwner) {
        if (!scannerRequested) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (scannerRequested) {
                try {
                    val activity = requireNotNull(context.findActivity())
                    val intentSender = scanner.getStartScanIntent(activity).awaitIntentSender()
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    scannerRequested = false
                } catch (_: CancellationException) {
                    // Uma Task cancelada em uma tela ativa é diferente do cancelamento do lifecycle.
                    currentCoroutineContext().ensureActive()
                    scannerRequested = false
                    cancelled()
                } catch (_: Exception) {
                    scannerRequested = false
                    failed()
                }
            }
        }
    }

    return remember { { scannerRequested = true } }
}

/** O cancelamento impede que uma Activity antiga abra o scanner após uma recriação. */
private suspend fun Task<IntentSender>.awaitIntentSender(): IntentSender =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { sender ->
            if (continuation.isActive) continuation.resume(sender)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener { continuation.cancel() }
    }
