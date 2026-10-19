package com.joseleandro.scaniqo.ui.documentCertificates

import android.security.KeyChain
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.util.UUID

@Composable
fun DocumentCertificatesRoute(
    onBack: () -> Unit,
    viewModel: DocumentCertificatesViewModel = koinViewModel(),
) {
    val operationId = rememberSaveable { UUID.randomUUID().toString() }
    val currentState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = currentState.takeIf { it.operationId == operationId } ?: DocumentCertificatesUiState(operationId = operationId)
    LaunchedEffect(operationId) { viewModel.beginOperation(operationId) }
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var launchedExport by rememberSaveable { mutableStateOf<String?>(null) }
    val fileError = stringResource(R.string.certificate_file_error)
    val certificateError = stringResource(R.string.certificate_picker_error)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.open(it.toString()) }
    }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.export(it.toString(), launchedExport) }
    }
    val save: () -> Unit = {
        state.pendingExport?.let { result ->
            launchedExport = result.id
            try { exporter.launch(result.suggestedName) }
            catch (_: Exception) { viewModel.reportError(fileError) }
        }
    }
    LaunchedEffect(state.pendingExport?.id) {
        if (state.pendingExport != null && launchedExport != state.pendingExport?.id) save()
    }
    BackHandler(enabled = state.busy) {}
    DocumentCertificatesScreen(
        state = state,
        onBack = onBack,
        onPickPdf = {
            try { picker.launch(arrayOf("application/pdf")) }
            catch (_: Exception) { viewModel.reportError(fileError) }
        },
        onChooseCertificate = {
            val sessionId = state.document?.sessionId
            if (activity == null) viewModel.reportError(certificateError)
            else try {
                KeyChain.choosePrivateKeyAlias(activity, { alias ->
                    scope.launch { viewModel.selectCertificate(alias, sessionId) }
                }, arrayOf("RSA", "EC"), null, null as String?, -1, state.alias)
            } catch (_: Exception) { viewModel.reportError(certificateError) }
        },
        onInstallCertificate = {
            try { installer.launch(KeyChain.createInstallIntent()) }
            catch (_: Exception) { viewModel.reportError(certificateError) }
        },
        onSign = viewModel::sign,
        onSave = save,
    )
}
