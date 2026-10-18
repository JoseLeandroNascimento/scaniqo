package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R

@Composable
internal fun DocumentMergeEmptySelection(enabled: Boolean, onPickFiles: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    painterResource(R.drawable.ic_document_import),
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp).size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                stringResource(R.string.merge_selection_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.merge_selection_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = onPickFiles, enabled = enabled) {
                Text(stringResource(R.string.merge_screen_add_files))
            }
        }
    }
}
