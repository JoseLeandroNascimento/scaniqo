package com.joseleandro.scaniqo.ui.documentCommon.platform

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.documentCommon.effect.DocumentActionEffect
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentCreationResult
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject

/** Mantém um único consumidor dos efeitos Android, mesmo ao navegar entre lista e PDF. */
@Composable
fun DocumentActionsHandler(
    viewModel: DocumentActionsViewModel,
    onDocumentCreated: (DocumentCreationResult) -> Unit,
    scanner: GmsDocumentScanner = koinInject(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val documentCreated by rememberUpdatedState(onDocumentCreated)
    LaunchedEffect(state.pendingCreation?.documentId) {
        state.pendingCreation?.let { creation ->
            documentCreated(creation)
            viewModel.acknowledgeCreation(creation.documentId)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val shareChooserTitle by rememberUpdatedState(stringResource(R.string.document_share_chooser))
    val launchScanner = rememberDocumentScannerLauncher(
        scanner = scanner,
        onCompleted = { pages -> viewModel.onDocumentAction(DocumentActionEvent.ScanCompleted(pages)) },
        onCancelled = { viewModel.onDocumentAction(DocumentActionEvent.ScanCancelled) },
        onFailed = { viewModel.onDocumentAction(DocumentActionEvent.ScanFailed) },
    )

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        viewModel.onDocumentAction(DocumentActionEvent.ExportDestinationSelected(uri?.toString()))
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    DocumentActionEffect.LaunchScanner -> launchScanner()
                    is DocumentActionEffect.ChooseDownloadLocation -> {
                        try {
                            downloadLauncher.launch(effect.fileName)
                        } catch (_: Exception) {
                            viewModel.onDocumentAction(DocumentActionEvent.ExportFailed)
                        }
                    }
                    is DocumentActionEffect.SharePdf -> {
                        try {
                            val uri = effect.uri.toUri()
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
                            viewModel.onDocumentAction(DocumentActionEvent.ShareFailed)
                        }
                    }
                }
            }
        }
    }
}
