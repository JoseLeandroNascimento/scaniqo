package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState

@Composable
internal fun DocumentProtectionOptions(state: DocumentToolsUiState, onEvent: (DocumentToolsEvent) -> Unit) {
    val workspace = state.workspace ?: return
    Column(
        Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = if (workspace.isProtected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                Modifier.fillMaxWidth().toggleable(
                    value = workspace.isProtected,
                    enabled = state.canEdit,
                    role = Role.Switch,
                    onValueChange = { onEvent(DocumentToolsEvent.SetProtection(it)) },
                ).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_document_lock), null, Modifier.size(28.dp))
                    Text(
                        stringResource(R.string.password_ui_require),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    )
                    Switch(checked = workspace.isProtected, onCheckedChange = null, enabled = state.canEdit)
                }
                Text(
                    stringResource(if (workspace.isProtected) R.string.password_ui_pdf_protected else R.string.password_ui_pdf_unprotected),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            stringResource(R.string.password_ui_export_hint),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { onEvent(DocumentToolsEvent.PasswordSettings) },
            enabled = state.canEdit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_tool_lock), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.runtime_password_manage))
        }
    }
}
