package com.joseleandro.scaniqo.ui.documentEditor

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.documents.DocumentsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Mantém um único consumidor dos efeitos Android, mesmo ao navegar entre lista e PDF. */
@Composable
fun DocumentActionsHandler(
    viewModel: DocumentsViewModel,
    scanner: GmsDocumentScanner = koinInject(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shareChooserTitle by rememberUpdatedState(stringResource(R.string.document_share_chooser))
    var scannerRequested by rememberSaveable { mutableStateOf(false) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = runCatching {
                GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                    ?.pages.orEmpty().map { it.imageUri.toString() }
            }.getOrDefault(emptyList())
            viewModel.onEvent(
                if (pages.isEmpty()) DocumentsEvent.ScanFailed
                else DocumentsEvent.ScanCompleted(pages),
            )
        } else {
            val launchFailed = result.data?.hasExtra(
                ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION,
            ) == true
            viewModel.onEvent(
                if (launchFailed) DocumentsEvent.ScanFailed else DocumentsEvent.ScanCancelled,
            )
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        viewModel.onEvent(DocumentsEvent.ExportDestinationSelected(uri?.toString()))
    }

    // A solicitação sobrevive à rotação enquanto o Google Play Services prepara o scanner.
    LaunchedEffect(scannerRequested, context, lifecycleOwner) {
        if (!scannerRequested) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (scannerRequested) {
                try {
                    val activity = requireNotNull(context.findActivity())
                    val intentSender = scanner.getStartScanIntent(activity).awaitIntentSender()
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    scannerRequested = false
                } catch (_: CancellationException) {
                    // A Task pode cancelar sem que a tela tenha sido destruída.
                    currentCoroutineContext().ensureActive()
                    scannerRequested = false
                    viewModel.onEvent(DocumentsEvent.ScanCancelled)
                } catch (_: Exception) {
                    scannerRequested = false
                    viewModel.onEvent(DocumentsEvent.ScanFailed)
                }
            }
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    DocumentsEffect.LaunchScanner -> scannerRequested = true
                    is DocumentsEffect.ChooseDownloadLocation -> {
                        try {
                            downloadLauncher.launch(effect.fileName)
                        } catch (_: Exception) {
                            viewModel.onEvent(DocumentsEvent.ExportFailed)
                        }
                    }
                    is DocumentsEffect.SharePdf -> {
                        try {
                            val uri = Uri.parse(effect.uri)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TITLE, effect.name)
                                clipData = ClipData.newUri(context.contentResolver, effect.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = Intent.createChooser(
                                shareIntent,
                                shareChooserTitle,
                            )
                            requireNotNull(context.findActivity()).startActivity(chooser)
                        } catch (_: Exception) {
                            viewModel.onEvent(DocumentsEvent.ShareFailed)
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
