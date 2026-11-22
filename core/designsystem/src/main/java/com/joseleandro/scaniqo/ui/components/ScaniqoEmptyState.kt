package com.joseleandro.scaniqo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/**
 * Empty-state illustration shared by the app's primary lists.
 * The illustration is decorative; the title, description and optional action carry its meaning.
 */
@Composable
fun ScaniqoEmptyState(
    title: String,
    description: String,
    illustration: EmptyStateIllustration,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EmptyStateArtwork(illustration)
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.widthIn(max = 360.dp).heightIn(min = 48.dp),
            ) {
                Text(actionLabel, textAlign = TextAlign.Center)
            }
        }
    }
}

@Preview(name = "Documentos vazios", widthDp = 360, locale = "pt-rBR")
@Preview(name = "Documentos vazios · fonte ampliada", widthDp = 320, fontScale = 2f, locale = "pt-rBR")
@Composable
private fun DocumentsEmptyStatePreview() {
    ScaniqoTheme {
        ScaniqoEmptyState(
            title = "Seus documentos começam aqui",
            description = "Escaneie ou importe um PDF para guardar seus documentos.",
            illustration = EmptyStateIllustration.DOCUMENTS,
        )
    }
}
