package com.joseleandro.scaniqo.ui.signatures

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import com.joseleandro.scaniqo.ui.components.DialogActions
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import com.joseleandro.scaniqo.ui.documentCommon.components.ScaniqoEmptyState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignaturesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberAppToastState()
    val messageText = state.message?.let { localizedRuntimeMessage(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            toast.show(it)
            viewModel.onEvent(SignaturesEvent.DismissMessage)
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onEvent(SignaturesEvent.ImageSelected(uri?.toString()))
    }

    val editor = state.editor
    BackHandler(enabled = editor != null) {
        if (editor?.isSaving == false) viewModel.onEvent(SignaturesEvent.DismissEditor)
    }
    if (editor != null) {
        SignatureEditorScreen(
            editor = editor,
            onEvent = viewModel::onEvent,
            onPickImage = { imagePicker.launch(arrayOf("image/*")) },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.runtime_signatures_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.runtime_back))
                    }
                },
            )
        },
        snackbarHost = { AppToastHost(toast) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.onEvent(SignaturesEvent.Create) },
                icon = { Icon(painterResource(R.drawable.edit_24px), null) },
                text = { Text(stringResource(R.string.runtime_new_signature)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading -> item { CircularProgressIndicator(Modifier.padding(24.dp)) }
                state.loadError -> item {
                    Text(stringResource(R.string.runtime_signatures_load_error))
                    TextButton(onClick = { viewModel.onEvent(SignaturesEvent.Retry) }) {
                        Text(stringResource(R.string.runtime_retry))
                    }
                }
                state.signatures.isEmpty() -> item(key = "empty") { SignaturesEmptyState() }
                else -> items(state.signatures, key = { it.id }) { signature ->
                    SignatureCard(
                        name = signature.name,
                        imageUri = signature.imageUri,
                        onEdit = { viewModel.onEvent(SignaturesEvent.Edit(signature.id)) },
                        onDelete = { viewModel.onEvent(SignaturesEvent.RequestDelete(signature)) },
                    )
                }
            }
        }
    }

    state.deleteCandidate?.let { signature ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(SignaturesEvent.DismissDialog) },
            title = { Text(stringResource(R.string.runtime_signature_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(
                        R.string.runtime_signature_delete_body,
                        androidx.core.text.BidiFormatter.getInstance().unicodeWrap(signature.name),
                    ))
                    state.error?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.runtime_delete),
                    onConfirm = { viewModel.onEvent(SignaturesEvent.ConfirmDelete) },
                    dismissText = stringResource(R.string.runtime_cancel),
                    onDismiss = { viewModel.onEvent(SignaturesEvent.DismissDialog) },
                    confirmEnabled = !state.isBusy,
                    dismissEnabled = !state.isBusy,
                    confirming = state.isBusy,
                    destructive = true,
                )
            },
        )
    }
}

@Composable
private fun SignatureCard(
    name: String,
    imageUri: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = name,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                SignatureImage(
                    imageUri,
                    Modifier.fillMaxWidth().height(144.dp).padding(16.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit, modifier = Modifier.weight(1f, fill = false)) {
                    Icon(painterResource(R.drawable.edit_24px), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.editar))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        stringResource(R.string.runtime_signature_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignaturesEmptyState() {
    ScaniqoEmptyState(
        title = stringResource(R.string.runtime_signatures_empty_title),
        description = stringResource(R.string.runtime_signatures_body),
        iconRes = R.drawable.ic_tool_signature,
    )
}

@Preview(name = "Assinatura", showBackground = true, widthDp = 360, locale = "pt-rBR")
@Preview(name = "Assinatura escura", showBackground = true, widthDp = 360, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Assinatura fonte ampliada", showBackground = true, widthDp = 320, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun SignatureCardPreview() {
    ScaniqoTheme {
        Surface {
            SignatureCard(
                name = "Assinatura para documentos profissionais",
                imageUri = "",
                onEdit = {},
                onDelete = {},
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Preview(name = "Sem assinaturas", showBackground = true, locale = "pt-rBR")
@Preview(name = "Sem assinaturas escuro", showBackground = true, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Sem assinaturas fonte ampliada", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "pt-rBR")
@Composable
private fun SignaturesEmptyPreview() {
    ScaniqoTheme {
        Surface { SignaturesEmptyState() }
    }
}
