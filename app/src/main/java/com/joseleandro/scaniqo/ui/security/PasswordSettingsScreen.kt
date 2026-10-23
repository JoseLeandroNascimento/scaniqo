package com.joseleandro.scaniqo.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.security.PasswordPolicy
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.core.security.SecureWindow
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.components.toast.rememberAppToastState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class PasswordSettingsAction { SAVE, DEACTIVATE, ENABLE_BIOMETRICS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vault = koinInject<PasswordVault>()
    val state by vault.state.collectAsStateWithLifecycle()
    val ready by vault.isReady.collectAsStateWithLifecycle()
    val request by vault.authenticationRequest.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Campos sensíveis são efêmeros: a recriação da tela pede que o usuário digite novamente.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmationVisible by remember { mutableStateOf(false) }
    var validationAttempted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    var unlockAction by remember { mutableStateOf<PasswordSettingsAction?>(null) }
    var authenticationPurpose by remember { mutableStateOf(PasswordSettingsAction.SAVE) }
    var confirmDeactivate by remember { mutableStateOf(false) }
    var confirmRecovery by remember { mutableStateOf(false) }
    var recoveryAccepted by remember { mutableStateOf(false) }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    SecureWindow()
    val toastState = rememberAppToastState()
    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            toastState.show(it)
            message = null
        }
    }
    val controlsEnabled = ready && !busy && request == null && unlockAction == null
    val invalidPassword = validationAttempted && !PasswordPolicy.isValidGeneral(password)
    val mismatch = validationAttempted && password != confirmation

    fun perform(action: PasswordSettingsAction) {
        scope.launch {
            busy = true
            error = null
            message = null
            try {
                when (action) {
                    PasswordSettingsAction.SAVE -> {
                        if (!vault.hasVault || recoveryAccepted) {
                            val enteredPassword = password
                            password = ""
                            confirmation = ""
                            authenticationPurpose = action
                            vault.beginSetup(enteredPassword, replaceUnavailableVault = recoveryAccepted)
                        } else {
                            vault.setGeneralPassword(password)
                            password = ""
                            confirmation = ""
                            message = R.string.password_updated
                        }
                        validationAttempted = false
                        passwordVisible = false
                        confirmationVisible = false
                    }
                    PasswordSettingsAction.DEACTIVATE -> {
                        vault.deactivateGeneralPassword()
                        message = R.string.password_deactivated
                    }
                    PasswordSettingsAction.ENABLE_BIOMETRICS -> {
                        authenticationPurpose = action
                        vault.beginEnableLegacyBiometrics()
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                error = when (failure) {
                    is java.io.IOException -> R.string.password_storage_error
                    else -> passwordErrorResource(failure)
                }
            } finally {
                busy = false
            }
        }
    }

    fun authorizeOrPerform(action: PasswordSettingsAction) {
        val needsExistingSession = vault.hasVault && !recoveryAccepted
        if (needsExistingSession && !vault.state.value.isUnlocked) unlockAction = action
        else perform(action)
    }

    fun submitPassword() {
        if (!controlsEnabled) return
        validationAttempted = true
        if (PasswordPolicy.isValidGeneral(password) && password == confirmation) {
            keyboard?.hide()
            focusManager.clearFocus()
            authorizeOrPerform(PasswordSettingsAction.SAVE)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { AppToastHost(toastState) },
        topBar = {
            PasswordSettingsTopBar(onBack = onBack, enabled = !busy && request == null)
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!ready) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.password_loading))
                }
                PasswordStatusCard(configured = state.isConfigured, unlocked = state.isUnlocked)
                if (state.recoveryRequired) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    )) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.password_recovery_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(stringResource(R.string.password_recovery_description))
                            if (!recoveryAccepted) {
                                OutlinedButton(
                                    onClick = { confirmRecovery = true },
                                    enabled = controlsEnabled,
                                ) { Text(stringResource(R.string.password_recreate)) }
                            }
                        }
                    }
                }
                if (!state.recoveryRequired || recoveryAccepted) {
                    PasswordSetupForm(
                        configured = state.isConfigured,
                        password = password,
                        confirmation = confirmation,
                        passwordVisible = passwordVisible,
                        confirmationVisible = confirmationVisible,
                        enabled = controlsEnabled,
                        busy = busy,
                        passwordError = if (invalidPassword) stringResource(R.string.password_invalid) else null,
                        confirmationError = if (mismatch) stringResource(R.string.password_mismatch) else null,
                        onPasswordChange = {
                            if (it.length <= 256) {
                                password = it
                                error = null
                            } else validationAttempted = true
                        },
                        onConfirmationChange = {
                            if (it.length <= 256) {
                                confirmation = it
                                error = null
                            } else validationAttempted = true
                        },
                        onTogglePassword = { passwordVisible = !passwordVisible },
                        onToggleConfirmation = { confirmationVisible = !confirmationVisible },
                        onSubmit = ::submitPassword,
                    )
                }
                PasswordVersionNotice()
                if (vault.canEnableLegacyBiometrics && !state.recoveryRequired) {
                    OutlinedButton(
                        onClick = { authorizeOrPerform(PasswordSettingsAction.ENABLE_BIOMETRICS) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = controlsEnabled,
                    ) { Text(stringResource(R.string.password_enable_biometrics)) }
                    Text(
                        stringResource(R.string.password_legacy_biometrics_help),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.isConfigured && !state.recoveryRequired) {
                    OutlinedButton(
                        onClick = { confirmDeactivate = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = controlsEnabled,
                    ) { Text(stringResource(R.string.password_deactivate)) }
                }
                if (state.isUnlocked) {
                    TextButton(
                        onClick = { vault.lock() },
                        enabled = controlsEnabled,
                    ) { Text(stringResource(R.string.password_lock_now)) }
                }
                if (busy) CircularProgressIndicator()
                error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    unlockAction?.let { action ->
        PasswordUnlockHandler(
            vault = vault,
            onUnlocked = {
                unlockAction = null
                perform(action)
            },
            onDismiss = { unlockAction = null },
        )
    }

    if (unlockAction == null) {
        request?.let { authentication ->
            VaultAuthenticationHost(
                request = authentication,
                onAuthenticated = { cipher ->
                    scope.launch {
                        busy = true
                        try {
                            vault.completeAuthentication(authentication.id, cipher)
                            message = if (authenticationPurpose == PasswordSettingsAction.ENABLE_BIOMETRICS) {
                                R.string.password_biometrics_enabled
                            } else {
                                R.string.password_updated
                            }
                            recoveryAccepted = false
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (failure: Exception) {
                            error = passwordErrorResource(failure)
                        } finally {
                            busy = false
                        }
                    }
                },
                onDismiss = { vault.cancelAuthentication(authentication.id) },
                onError = {
                    vault.cancelAuthentication(authentication.id)
                    error = R.string.password_auth_error
                },
            )
        }
    }

    if (confirmDeactivate) {
        AlertDialog(
            onDismissRequest = { confirmDeactivate = false },
            title = { Text(stringResource(R.string.password_deactivate_title)) },
            text = { Text(stringResource(R.string.password_deactivate_description)) },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.password_confirm_action),
                    onConfirm = {
                        confirmDeactivate = false
                        authorizeOrPerform(PasswordSettingsAction.DEACTIVATE)
                    },
                    dismissText = stringResource(R.string.password_cancel),
                    onDismiss = { confirmDeactivate = false },
                )
            },
        )
    }

    if (confirmRecovery) {
        AlertDialog(
            onDismissRequest = { confirmRecovery = false },
            title = { Text(stringResource(R.string.password_recreate_title)) },
            text = { Text(stringResource(R.string.password_recreate_description)) },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.password_confirm_action),
                    onConfirm = {
                        confirmRecovery = false
                        recoveryAccepted = true
                    },
                    dismissText = stringResource(R.string.password_cancel),
                    onDismiss = { confirmRecovery = false },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordSettingsTopBar(onBack: () -> Unit, enabled: Boolean) {
    TopAppBar(
        title = { Text(stringResource(R.string.password_settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBack, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24px),
                    contentDescription = stringResource(R.string.password_back),
                )
            }
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Senha dos PDFs", widthDp = 390, heightDp = 780, locale = "pt-rBR", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Senha compacta", widthDp = 320, heightDp = 640, fontScale = 1.3f, locale = "pt-rBR", showBackground = true)
@Composable
private fun PasswordFieldsPreview() {
    com.joseleandro.scaniqo.ui.theme.ScaniqoTheme {
        Scaffold(topBar = { PasswordSettingsTopBar(onBack = {}, enabled = true) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PasswordStatusCard(configured = false, unlocked = false)
                PasswordSetupForm(
                    configured = false,
                    password = "Exemplo123",
                    confirmation = "Exemplo123",
                    passwordVisible = false,
                    confirmationVisible = false,
                    enabled = true,
                    busy = false,
                    passwordError = null,
                    confirmationError = null,
                    onPasswordChange = {},
                    onConfirmationChange = {},
                    onTogglePassword = {},
                    onToggleConfirmation = {},
                    onSubmit = {},
                )
                PasswordVersionNotice()
            }
        }
    }
}
