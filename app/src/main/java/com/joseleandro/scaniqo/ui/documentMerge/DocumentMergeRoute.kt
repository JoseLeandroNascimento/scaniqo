package com.joseleandro.scaniqo.ui.documentMerge

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.state.rememberDocumentMergeSelectionState
import com.joseleandro.scaniqo.ui.documentMerge.model.DocumentMergeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

/** A mesma seleção atende um PDF novo ou uma sessão de edição existente. */
@Composable
fun DocumentMergeRoute(
    documents: List<DocumentModel>,
    target: DocumentMergeTarget? = null,
    enabled: Boolean = true,
    isActive: Boolean = true,
    canNavigateBack: Boolean = true,
    isLoading: Boolean = false,
    loadError: Boolean = false,
    onRetry: () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    onUnlock: (() -> Unit)? = null,
    onBack: () -> Unit,
    onConfirm: (List<DocumentMergeItem>) -> Unit,
) {
    val operationId = rememberSaveable(target?.documentId) { UUID.randomUUID().toString() }
    val selection = rememberDocumentMergeSelectionState(operationId)
    LaunchedEffect(target?.currentDocument) {
        target?.currentDocument?.let(selection::includeCurrentDocument)
    }
    var pendingUris by rememberSaveable(operationId) { mutableStateOf(emptyList<String>()) }
    var pickerError by remember(operationId) { mutableStateOf<String?>(null) }
    var leaving by remember(operationId) { mutableStateOf(false) }
    val context = LocalContext.current
    val active by rememberUpdatedState(isActive)
    val canLeave by rememberUpdatedState(canNavigateBack)
    val pickerErrorText = stringResource(R.string.quick_merge_picker_error)
    val interactive = enabled && isActive && !leaving && pendingUris.isEmpty()
    val requestBack = {
        if (active && canLeave && !leaving) {
            leaving = true
            onBack()
        }
    }
    BackHandler(enabled = isActive, onBack = requestBack)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Alguns provedores concedem apenas o acesso temporário.
            }
        }
        pendingUris = uris.map { it.toString() }
    }
    LaunchedEffect(pendingUris) {
        if (pendingUris.isEmpty()) return@LaunchedEffect
        val uris = pendingUris
        val resolver = context.applicationContext.contentResolver
        val files = withContext(Dispatchers.IO) {
            uris.mapIndexed { index, raw ->
                currentCoroutineContext().ensureActive()
                val name = runCatching {
                    resolver.query(raw.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() }?.take(200) ?: "PDF ${index + 1}"
                DocumentMergeItem(uri = raw, name = name)
            }
        }
        selection.add(files)
        pendingUris = emptyList()
    }

    DocumentMergeScreen(
        documents = documents,
        selection = selection,
        target = target,
        enabled = interactive,
        canNavigateBack = canNavigateBack && !leaving,
        canConfirm = (target == null || target.currentDocument != null) &&
            (!isLoading || selection.items.none { it.documentId != null }),
        isLoading = isLoading || pendingUris.isNotEmpty(),
        loadError = loadError,
        error = pickerError,
        onRetry = onRetry,
        snackbarHost = snackbarHost,
        onUnlock = onUnlock,
        onBack = requestBack,
        onPickFiles = {
            pickerError = null
            try { picker.launch(arrayOf("application/pdf")) }
            catch (_: Exception) { pickerError = pickerErrorText }
        },
        onConfirm = { items ->
            if (active && interactive) onConfirm(items)
        },
    )
}
