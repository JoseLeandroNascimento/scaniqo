package com.joseleandro.scaniqo.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/**
 * Coloque no confirmButton do AlertDialog, sem um dismissButton separado.
 * A linha única mantém as duas ações lado a lado, inclusive com fonte ampliada.
 */
@Composable
internal fun DialogActions(
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    confirming: Boolean = false,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onDismiss,
            enabled = dismissEnabled,
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(dismissText, textAlign = TextAlign.Center)
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            shape = MaterialTheme.shapes.medium,
            colors = if (destructive) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ) else ButtonDefaults.buttonColors(),
        ) {
            if (confirming) {
                CircularProgressIndicator(Modifier.size(16.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(confirmText, textAlign = TextAlign.Center)
        }
    }
}

@Preview(name = "Ações do diálogo", widthDp = 390, heightDp = 520, locale = "pt-rBR")
@Preview(name = "Ações no modo escuro", widthDp = 390, heightDp = 520, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DialogActionsPreview() {
    ScaniqoTheme {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Descartar alterações?") },
            text = { Text("As alterações ainda não foram salvas no documento.") },
            confirmButton = {
                DialogActions(
                    confirmText = "Descartar alterações", onConfirm = {},
                    dismissText = "Continuar editando", onDismiss = {}, destructive = true,
                )
            },
        )
    }
}

// O popup do layoutlib usa uma janela própria; esta prévia verifica a largura mínima do conteúdo.
@Preview(name = "Ações compactas com fonte ampliada", widthDp = 320, heightDp = 260,
    locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun DialogActionsCompactPreview() {
    ScaniqoTheme {
        Surface(
            modifier = Modifier.padding(20.dp).wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(Modifier.padding(24.dp)) {
                DialogActions(
                    confirmText = "Descartar alterações", onConfirm = {},
                    dismissText = "Continuar editando", onDismiss = {}, destructive = true,
                )
            }
        }
    }
}
