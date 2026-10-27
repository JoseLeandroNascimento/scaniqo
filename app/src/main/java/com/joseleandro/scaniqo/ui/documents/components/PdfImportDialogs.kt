package com.joseleandro.scaniqo.ui.documents.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import com.joseleandro.scaniqo.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.documents.state.PdfImportUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@Composable
internal fun PdfImportDialogs(
    state: PdfImportUiState,
    password: String,
    isPasswordConfigured: Boolean,
    onPasswordChange: (String) -> Unit,
    onSubmitPassword: () -> Unit,
    onPasswordSettings: () -> Unit,
    onCancel: () -> Unit,
) {
    if (state.isLoading) {
        AlertDialog(
            onDismissRequest = {},
            icon = { PdfImportPagesIllustration() },
            title = { Text(stringResource(R.string.runtime_pdf_import_title)) },
            text = { Text(stringResource(R.string.runtime_pdf_import_progress)) },
            confirmButton = {
                if (!state.isSavingToLibrary) TextButton(onClick = onCancel) { Text(stringResource(R.string.runtime_cancel)) }
            },
        )
    } else if (state.needsPassword) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.runtime_pdf_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.runtime_pdf_password_body))
                    OutlinedTextField(
                        password,
                        onValueChange = { onPasswordChange(it.take(127)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.runtime_pdf_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    if (!isPasswordConfigured) {
                        Text(stringResource(R.string.runtime_pdf_password_setup))
                        TextButton(onClick = onPasswordSettings) { Text(stringResource(R.string.runtime_password_manage)) }
                    }
                    state.error?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.runtime_import),
                    onConfirm = onSubmitPassword,
                    dismissText = stringResource(R.string.runtime_cancel),
                    onDismiss = onCancel,
                    confirmEnabled = isPasswordConfigured && password.isNotEmpty(),
                )
            },
        )
    } else if (state.error != null) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.runtime_import_failed)) },
            text = { Text(localizedRuntimeMessage(state.error.orEmpty())) },
            confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.runtime_close)) } },
        )
    }
}


@Composable
private fun PdfImportPagesIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "PDF import pages")
    val gathering by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Pages entering",
    )
    Box(
        modifier.size(width = 148.dp, height = 108.dp).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(92.dp).background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                CircleShape,
            ),
        )
        PdfImportPage(
            modifier = Modifier.size(width = 62.dp, height = 80.dp).graphicsLayer {
                translationX = (-47f + 27f * gathering).dp.toPx()
                translationY = 9.dp.toPx()
                rotationZ = -13f + 7f * gathering
            },
            accent = MaterialTheme.colorScheme.secondaryContainer,
        )
        PdfImportPage(
            modifier = Modifier.size(width = 62.dp, height = 80.dp).graphicsLayer {
                translationX = (47f - 27f * gathering).dp.toPx()
                translationY = 9.dp.toPx()
                rotationZ = 13f - 7f * gathering
            },
            accent = MaterialTheme.colorScheme.tertiaryContainer,
        )
        PdfImportPage(
            modifier = Modifier.size(width = 68.dp, height = 88.dp).graphicsLayer {
                translationY = -4.dp.toPx() - gathering * 3.dp.toPx()
            },
            accent = MaterialTheme.colorScheme.primaryContainer,
        )
    }
}

@Composable
private fun PdfImportPage(modifier: Modifier = Modifier, accent: Color) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxSize().padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(16.dp).background(accent, MaterialTheme.shapes.extraSmall))
            Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
            Box(Modifier.fillMaxWidth(.72f).height(4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
            Box(Modifier.fillMaxWidth(.86f).height(4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
}

@Preview(name = "Importar PDF — preparando", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Preview(
    name = "Importar PDF — escuro", widthDp = 390, heightDp = 780,
    locale = "pt-rBR", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PdfImportPreparingPreview() {
    ScaniqoTheme {
        PdfImportDialogs(
            state = PdfImportUiState(isActive = true, isLoading = true),
            password = "",
            isPasswordConfigured = false,
            onPasswordChange = {},
            onSubmitPassword = {},
            onPasswordSettings = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Importar PDF — salvando", widthDp = 390, heightDp = 780, locale = "pt-rBR")
@Composable
private fun PdfImportSavingPreview() {
    ScaniqoTheme {
        PdfImportDialogs(
            state = PdfImportUiState(isActive = true, isLoading = true, isSavingToLibrary = true),
            password = "",
            isPasswordConfigured = false,
            onPasswordChange = {},
            onSubmitPassword = {},
            onPasswordSettings = {},
            onCancel = {},
        )
    }
}
