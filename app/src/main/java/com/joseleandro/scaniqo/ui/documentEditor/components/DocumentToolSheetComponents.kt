package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R

/** As ações ficam fora da área rolável para permanecerem acessíveis em telas menores. */
@Composable
internal fun DocumentToolActions(
    enabled: Boolean,
    applyLabel: String? = null,
    compact: Boolean = false,
    onReset: () -> Unit,
    onApply: () -> Unit,
    applyEnabled: Boolean = true,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        if (!compact) HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onReset, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 52.dp)) {
                Text(stringResource(R.string.tools_reset_compact), textAlign = TextAlign.Center)
            }
            Button(onClick = onApply, enabled = enabled && applyEnabled, modifier = Modifier.weight(1.5f).heightIn(min = if (compact) 48.dp else 52.dp)) {
                Text(applyLabel ?: stringResource(R.string.tools_apply_compact), textAlign = TextAlign.Center)
            }
        }
    }
}
