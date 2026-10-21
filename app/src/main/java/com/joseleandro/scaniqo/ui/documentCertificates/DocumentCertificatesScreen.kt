package com.joseleandro.scaniqo.ui.documentCertificates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.core.localization.localizedRuntimeMessage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.PdfCertificateDocument
import com.joseleandro.scaniqo.domain.model.PdfCertificateSignature
import com.joseleandro.scaniqo.domain.model.PdfSignatureIntegrity
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentCertificatesScreen(
    state: DocumentCertificatesUiState,
    onBack: () -> Unit,
    onPickPdf: () -> Unit,
    onChooseCertificate: () -> Unit,
    onInstallCertificate: () -> Unit,
    onSign: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.certificate_screen_title)) },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !state.busy) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.certificate_back))
                }
            })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.certificate_intro), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onPickPdf, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.certificate_pick_pdf))
                    }
                    state.error?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.error) }
                    state.message?.let { Text(localizedRuntimeMessage(it), color = MaterialTheme.colorScheme.primary) }
                    state.document?.let {
                        Text(it.name, style = MaterialTheme.typography.titleMedium)
                        if (it.signatures.isEmpty()) Text(stringResource(R.string.certificate_unsigned), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            itemsIndexed(state.document?.signatures.orEmpty()) { index, signature ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (index > 0) HorizontalDivider()
                    Text(if (signature.signer == "Signatário não identificado") stringResource(R.string.certificate_unknown_signer)
                        else signature.signer, style = MaterialTheme.typography.titleSmall)
                    val label = when (signature.integrity) {
                        PdfSignatureIntegrity.VERIFIED -> R.string.certificate_integrity_verified
                        PdfSignatureIntegrity.INVALID -> R.string.certificate_integrity_invalid
                        PdfSignatureIntegrity.UNSUPPORTED -> R.string.certificate_integrity_unsupported
                    }
                    Text(stringResource(label),
                        color = if (signature.integrity == PdfSignatureIntegrity.VERIFIED && signature.coversWholeDocument)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(if (signature.coversWholeDocument) R.string.certificate_whole_file else R.string.certificate_earlier_revision),
                        style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (signature.certificateCurrent) R.string.certificate_current else R.string.certificate_not_current),
                        style = MaterialTheme.typography.bodySmall)
                    signature.detail?.let { Text(localizedRuntimeMessage(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    val document = state.document
                    if (document != null && document.canSign) {
                        HorizontalDivider()
                        state.alias?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium) }
                        OutlinedButton(onClick = onChooseCertificate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (state.alias == null) R.string.certificate_choose else R.string.certificate_change))
                        }
                        TextButton(onClick = onInstallCertificate, enabled = !state.busy) {
                            Text(stringResource(R.string.certificate_install))
                        }
                        Text(stringResource(R.string.certificate_sign_scope), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.pendingExport != null) {
                            Button(onClick = onSave, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.certificate_save))
                            }
                        } else {
                            Button(onClick = onSign, enabled = !state.busy && state.alias != null, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.certificate_sign))
                            }
                        }
                    } else if (document != null) {
                        Text(stringResource(R.string.certificate_sign_unavailable), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!document?.signatures.isNullOrEmpty()) {
                        Text(stringResource(R.string.certificate_verification_scope), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Preview(name = "Certificado digital", widthDp = 390, heightDp = 780, showBackground = true)
@Composable
private fun DocumentCertificatesPreview() {
    ScaniqoTheme {
        DocumentCertificatesScreen(
            state = DocumentCertificatesUiState(document = PdfCertificateDocument(
                "preview", "Contrato assinado.pdf",
                listOf(PdfCertificateSignature("CN=Maria Silva", PdfSignatureIntegrity.VERIFIED, true, true)), false,
            )),
            onBack = {}, onPickPdf = {}, onChooseCertificate = {}, onInstallCertificate = {}, onSign = {}, onSave = {},
        )
    }
}
