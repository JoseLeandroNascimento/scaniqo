package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentPasswordRecoveryDialog
import com.joseleandro.scaniqo.ui.security.PasswordUnlockHandler
import org.koin.compose.koinInject

@Composable
fun DocumentReaderRoute(
    documentId: Long,
    viewModel: DocumentViewerViewModel,
    readerViewModel: DocumentReaderViewModel,
    actionsViewModel: DocumentActionsViewModel,
    onNavigationBack: () -> Unit,
    onEdit: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    initialPageNumber: Int? = null,
    isActive: Boolean = true,
    onNavigatePasswordSettings: () -> Unit = {},
    vault: PasswordVault = koinInject(),
) {
    val viewerState by viewModel.uiState.collectAsStateWithLifecycle()
    val readerState by readerViewModel.uiState.collectAsStateWithLifecycle()
    val actionsState by actionsViewModel.uiState.collectAsStateWithLifecycle()
    val document = viewerState.document?.takeIf { it.id == documentId }
    val isLoading = viewerState.isLoading || viewerState.requestedDocumentId != documentId
    var unlockRequested by rememberSaveable(documentId) { mutableStateOf(false) }
    var recoveryRequested by rememberSaveable(documentId) { mutableStateOf(false) }
    var documentWasLoaded by rememberSaveable(documentId) { mutableStateOf(false) }
    val active by rememberUpdatedState(isActive)
    val readerOwner = remember { Any() }

    LaunchedEffect(documentId, isActive) {
        if (isActive) viewModel.load(documentId)
    }
    LaunchedEffect(document, isActive) {
        if (isActive && document != null) {
            documentWasLoaded = true
            readerViewModel.load(document, readerOwner)
        } else {
            readerViewModel.deactivate(documentId, readerOwner)
            unlockRequested = false
            recoveryRequested = false
        }
    }
    LaunchedEffect(document, isLoading, viewerState.loadError, isActive) {
        if (isActive && documentWasLoaded && document == null && !isLoading && !viewerState.loadError) {
            onNavigationBack()
        }
    }
    DisposableEffect(readerViewModel, documentId) {
        val process = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (active && event == Lifecycle.Event.ON_STOP) readerViewModel.background(readerOwner)
        }
        process.addObserver(observer)
        onDispose {
            process.removeObserver(observer)
            readerViewModel.deactivate(documentId, readerOwner)
        }
    }
    DocumentReaderScreen(
        documentId = documentId,
        uiState = viewerState.copy(
            document = document,
            actions = actionsState,
            isLoading = isLoading,
            loadError = viewerState.loadError && viewerState.requestedDocumentId == documentId,
        ),
        readerState = readerState,
        onDocumentAction = actionsViewModel::onDocumentAction,
        onRetryLoad = {
            viewModel.retry()
            readerViewModel.retry()
            actionsViewModel.retryLoadingTags()
        },
        onNavigationBack = onNavigationBack,
        onEdit = onEdit,
        onUnlock = {
            if (readerState.recoveryRequired) recoveryRequested = true
            else unlockRequested = true
        },
        modifier = modifier,
        initialPageNumber = initialPageNumber,
    )
    if (isActive && unlockRequested) {
        PasswordUnlockHandler(
            vault = vault,
            onUnlocked = {
                unlockRequested = false
                readerViewModel.retry()
            },
            onDismiss = { unlockRequested = false },
        )
    }
    if (isActive && recoveryRequested) {
        DocumentPasswordRecoveryDialog(
            vault = vault,
            onDismiss = { recoveryRequested = false },
            onSettings = {
                recoveryRequested = false
                onNavigatePasswordSettings()
            },
            onRecovered = { _, keyId ->
                recoveryRequested = false
                readerViewModel.recover(keyId)
            },
        )
    }
}
