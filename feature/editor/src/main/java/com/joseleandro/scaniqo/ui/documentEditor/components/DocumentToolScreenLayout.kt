package com.joseleandro.scaniqo.ui.documentEditor.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * A ação final de cada editor é registrada no host da topbar. A função executada permanece atual
 * após recomposições, enquanto o registro só muda quando a disponibilidade da ação muda.
 */
@Composable
internal fun RegisterDocumentToolPrimaryAction(
    enabled: Boolean,
    onClick: () -> Unit,
    register: (((() -> Unit)?) -> Unit),
) {
    val currentOnClick by rememberUpdatedState(onClick)
    DisposableEffect(register, enabled) {
        register(if (enabled) ({ currentOnClick() }) else null)
        onDispose { register(null) }
    }
}
