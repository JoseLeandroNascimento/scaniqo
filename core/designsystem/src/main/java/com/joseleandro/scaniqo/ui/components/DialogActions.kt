package com.joseleandro.scaniqo.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/**
 * Coloque no confirmButton do AlertDialog, sem um dismissButton separado.
 * Segue a ordem do AlertDialog: confirmação no fim da linha, ou acima ao empilhar.
 */
@Composable
fun DialogActions(
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    confirming: Boolean = false,
    destructive: Boolean = false,
) {
    val layoutDirection = LocalLayoutDirection.current
    val actionDirection = if (layoutDirection == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides actionDirection) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                TextButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled && !confirming,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    colors = if (destructive) ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ) else ButtonDefaults.textButtonColors(),
                ) {
                    if (confirming) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(confirmText, textAlign = TextAlign.Center)
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = dismissEnabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(dismissText, textAlign = TextAlign.Center)
                }
            }
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
                    confirmText = "Descartar", onConfirm = {},
                    dismissText = "Continuar", onDismiss = {}, destructive = true,
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
