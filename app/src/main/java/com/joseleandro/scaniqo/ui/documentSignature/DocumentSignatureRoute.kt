package com.joseleandro.scaniqo.ui.documentSignature

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.ui.documentEditor.DocumentToolsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerViewModel
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.signatures.SignatureEditorScreen
import com.joseleandro.scaniqo.ui.signatures.SignaturesEvent
import com.joseleandro.scaniqo.ui.signatures.SignaturesViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Compartilha a sessão de edição; sair desta etapa nunca descarta a receita do documento. */
@Composable
fun DocumentSignatureRoute(
    documentId: Long,
    viewerViewModel: DocumentViewerViewModel,
    toolsViewModel: DocumentToolsViewModel,
    isActive: Boolean,
    onBack: () -> Unit,
    signaturesViewModel: SignaturesViewModel = koinViewModel(),
) {
    val viewer by viewerViewModel.uiState.collectAsStateWithLifecycle()
    val tools by toolsViewModel.uiState.collectAsStateWithLifecycle()
    val signatures by signaturesViewModel.uiState.collectAsStateWithLifecycle()
    val document = viewer.document?.takeIf { it.id == documentId }
    var confirmExit by rememberSaveable(documentId) { mutableStateOf(false) }
    var leaving by remember(documentId) { mutableStateOf(false) }
    val active by rememberUpdatedState(isActive)
    val leave = {
        if (active && !leaving) {
            leaving = true
            onBack()
        }
    }
    val sameSession = tools.document?.id == documentId
    SecureWindow(enabled = document?.isProtected == true || (sameSession && tools.workspace?.isProtected == true))

    LaunchedEffect(documentId) { viewerViewModel.load(documentId) }
    LaunchedEffect(document?.id, document?.pdfUri) { document?.let(toolsViewModel::load) }
    val selection = signatures.pendingDocumentSelection
    LaunchedEffect(selection, tools.signatures, tools.canEdit, sameSession, isActive) {
        if (isActive && selection?.documentId == documentId && sameSession && tools.canEdit && tools.signatureDraft == null &&
            tools.signatures.any { it.id == selection.signatureId }
        ) {
            toolsViewModel.onEvent(DocumentToolsEvent.InsertSignature(selection.signatureId))
            signaturesViewModel.onEvent(SignaturesEvent.AcknowledgeDocumentSelection(selection.signatureId))
        }
    }
    val requestBack = {
        when {
            !active || leaving -> Unit
            signatures.editor?.targetDocumentId == documentId -> signaturesViewModel.onEvent(SignaturesEvent.DismissEditor)
            tools.isBusy -> Unit
            sameSession && tools.signatureDraft != null -> confirmExit = true
            else -> leave()
        }
    }
    BackHandler(enabled = isActive && !leaving, onBack = requestBack)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        signaturesViewModel.onEvent(SignaturesEvent.ImageSelected(uri?.toString()))
    }
    val editor = signatures.editor?.takeIf { isActive && it.targetDocumentId == documentId }
    if (editor != null) {
        SignatureEditorScreen(
            editor = editor,
            onEvent = signaturesViewModel::onEvent,
            onPickImage = { imagePicker.launch(arrayOf("image/*")) },
        )
    } else {
        DocumentSignatureScreen(
            documentId = documentId,
            state = tools,
            documentName = document?.name.orEmpty(),
            isLoadingDocument = viewer.isLoading || viewer.requestedDocumentId != documentId,
            documentError = viewer.loadError || (!viewer.isLoading && viewer.requestedDocumentId == documentId && document == null),
            onEvent = toolsViewModel::onEvent,
            onBack = requestBack,
            onCreateSignature = { signaturesViewModel.onEvent(SignaturesEvent.CreateForDocument(documentId)) },
            onRetry = {
                viewerViewModel.retry()
                toolsViewModel.onEvent(DocumentToolsEvent.Retry)
            },
            onApply = {
                val current = toolsViewModel.uiState.value
                if (active && !leaving && current.document?.id == documentId && current.signatureDraft != null) {
                    toolsViewModel.onEvent(DocumentToolsEvent.ConfirmSignature)
                    if (toolsViewModel.uiState.value.signatureDraft == null) leave()
                }
            },
        )
    }
    if (confirmExit && isActive) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.signature_cancel_title)) },
            text = { Text(stringResource(R.string.signature_cancel_body)) },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.signature_cancel_confirm),
                    onConfirm = {
                        if (active && !leaving) {
                            toolsViewModel.onEvent(DocumentToolsEvent.CancelSignature)
                            confirmExit = false
                            leave()
                        }
                    },
                    dismissText = stringResource(R.string.signature_keep_editing),
                    onDismiss = { confirmExit = false },
                    confirmEnabled = !tools.isBusy,
                    destructive = true,
                )
            },
        )
    }
}
