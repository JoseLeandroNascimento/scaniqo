package com.joseleandro.scaniqo.ui.documentEditor

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.ui.documents.DocumentsViewModel
import com.joseleandro.scaniqo.ui.security.PasswordUnlockHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** O seletor fornece acesso somente ao PDF escolhido; não exige permissão de armazenamento. */
@Composable
fun PdfImportHandler(
    viewModel: PdfImportViewModel,
    documents: DocumentsViewModel,
    vault: PasswordVault,
    showDialogs: Boolean,
    onPasswordSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                }.getOrNull() ?: "Documento importado"
            }
        }
    }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PdfImportEffect.PickFile -> {
                        documents.onEvent(DocumentsEvent.ImportStarted)
                        try { picker.launch(arrayOf("application/pdf")) }
                        catch (_: Exception) { viewModel.cancel() }
                    }
                    PdfImportEffect.Cancelled -> {
                        password = ""
                        documents.onEvent(DocumentsEvent.ImportCancelled)
                    }
                }
            }
        }
    }
    LaunchedEffect(state.pending) {
        state.pending?.let { pending ->
            password = ""
            documents.onEvent(DocumentsEvent.ImportPrepared(pending.scan, pending.name))
            viewModel.completeDelivery(pending.scan.pdfUri)
        }
    }
    if (!showDialogs || !state.isActive || state.pending != null) return
    com.joseleandro.scaniqo.core.security.SecureWindow(enabled = state.needsPassword || state.needsUnlock)
    if (state.needsUnlock) {
        PasswordUnlockHandler(
            vault = vault,
            onUnlocked = viewModel::unlocked,
            onDismiss = viewModel::unlockDismissed,
        )
    } else if (state.isLoading) {
        AlertDialog(
            onDismissRequest = {},
            icon = { CircularProgressIndicator(Modifier.size(28.dp)) },
            title = { Text("Importando PDF") },
            text = { Text("Preparando o documento para edição…") },
            confirmButton = {
                TextButton(onClick = viewModel::cancel) { Text("Cancelar") }
            },
        )
    } else if (state.needsPassword) {
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text("Este PDF tem senha") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Informe a senha atual do arquivo. A proteção será mantida na cópia importada.")
                    OutlinedTextField(
                        password,
                        onValueChange = { password = it.take(127) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha do PDF") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    if (!vaultState.isConfigured) {
                        Text("Configure a senha geral para guardar este acesso com a proteção do aparelho.")
                        TextButton(onClick = { password = ""; onPasswordSettings() }) { Text("Gerenciar senha geral") }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = vaultState.isConfigured && password.isNotEmpty(),
                    onClick = { viewModel.submitPassword(password); password = "" },
                ) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancel) { Text("Cancelar") } },
        )
    } else if (state.error != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text("Não foi possível importar") },
            text = { Text(state.error.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::cancel) { Text("Fechar") } },
        )
    }
}
