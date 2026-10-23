package com.joseleandro.scaniqo.ui.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.security.PasswordPolicy

/** Apresentação apenas: validação, autenticação e descarte dos segredos ficam na tela. */
@Composable
internal fun PasswordSetupForm(
    configured: Boolean,
    password: String,
    confirmation: String,
    passwordVisible: Boolean,
    confirmationVisible: Boolean,
    enabled: Boolean,
    busy: Boolean,
    passwordError: String?,
    confirmationError: String?,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onToggleConfirmation: () -> Unit,
    onSubmit: () -> Unit,
) {
    val confirmationFocus = remember { FocusRequester() }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.password_ui_form_title), style = MaterialTheme.typography.titleMedium)
            PasswordEntryField(
                value = password,
                onValueChange = onPasswordChange,
                visible = passwordVisible,
                onToggleVisibility = onTogglePassword,
                label = stringResource(R.string.password_new_label),
                enabled = enabled,
                error = passwordError,
                supportingText = stringResource(R.string.password_ui_minimum, PasswordPolicy.MIN_GENERAL_LENGTH),
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(onNext = { confirmationFocus.requestFocus() }),
            )
            PasswordEntryField(
                value = confirmation,
                onValueChange = onConfirmationChange,
                visible = confirmationVisible,
                onToggleVisibility = onToggleConfirmation,
                label = stringResource(R.string.password_confirm_label),
                modifier = Modifier.focusRequester(confirmationFocus),
                enabled = enabled,
                error = confirmationError,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { if (enabled) onSubmit() }),
            )
            Button(
                onClick = onSubmit,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (configured) R.string.password_change else R.string.password_set))
            }
            Text(
                stringResource(R.string.password_ui_auth_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PasswordStatusCard(configured: Boolean, unlocked: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (configured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_document_lock), null, Modifier.size(28.dp),
                tint = if (configured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(if (configured) R.string.password_status_active else R.string.password_status_inactive),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(when {
                        !configured -> R.string.password_status_setup
                        unlocked -> R.string.password_status_unlocked
                        else -> R.string.password_status_locked
                    }),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun PasswordVersionNotice() {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(
                painterResource(R.drawable.ic_toast_info), null, Modifier.padding(top = 2.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.password_ui_versions_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(R.string.password_ui_versions_details))
            Icon(
                painterResource(if (expanded) R.drawable.ic_toast_close else R.drawable.ic_toast_info),
                null, Modifier.padding(start = 8.dp).size(16.dp),
            )
        }
        if (expanded) Text(
            stringResource(R.string.password_versions_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PasswordEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    label: String,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = (error ?: supportingText)?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility, enabled = enabled) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    stringResource(if (visible) R.string.password_hide else R.string.password_show),
                )
            }
        },
    )
}
