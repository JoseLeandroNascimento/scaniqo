package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun DocumentEditorPrimaryActions(
    insertLabel: String,
    doneLabel: String,
    onInsert: () -> Unit,
    onDone: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    doneEnabled: Boolean = enabled,
    insertTag: String = "document_insert",
    doneTag: String = "document_finish",
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onInsert,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(insertTag),
        ) {
            Text(insertLabel)
        }
        Button(
            onClick = onDone,
            enabled = doneEnabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(doneTag),
        ) {
            Text(doneLabel)
        }
    }
}
