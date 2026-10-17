package com.joseleandro.scaniqo.ui.documentEditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** A alça é pequena visualmente, mas mantém 48 dp para tocar e arrastar com conforto. */
@Composable
internal fun DocumentPreviewHandle(
    iconRes: Int,
    description: String,
    valueDescription: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
    actions: List<CustomAccessibilityAction> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier.size(48.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .semantics {
                contentDescription = description
                stateDescription = valueDescription
                customActions = if (enabled) actions else emptyList()
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(iconRes), null, Modifier.size(18.dp).rotate(iconRotation))
            }
        }
    }
}
