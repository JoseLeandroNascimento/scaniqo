package com.joseleandro.scaniqo.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import com.joseleandro.scaniqo.ui.components.DialogActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.core.security.PasswordVaultRecoveryException
import com.joseleandro.scaniqo.core.security.PasswordVaultUnavailableException
import com.joseleandro.scaniqo.core.security.PasswordVersionMissingException
import com.joseleandro.scaniqo.core.security.VaultAuthenticationMode
import com.joseleandro.scaniqo.core.security.VaultAuthenticationRequest
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Integração única do desbloqueio com as ferramentas; não recebe nem persiste senhas. */
@Composable
fun PasswordUnlockHandler(
    vault: PasswordVault,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val request by vault.authenticationRequest.collectAsStateWithLifecycle()
    val currentUnlocked by rememberUpdatedState(onUnlocked)
    val currentDismiss by rememberUpdatedState(onDismiss)
    var error by remember { mutableStateOf<Int?>(null) }

    fun begin(useDeviceCredential: Boolean = false) {
        scope.launch {
            error = null
            try {
                vault.beginUnlock(useDeviceCredential)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                error = passwordErrorResource(failure)
            }
        }
    }

    LaunchedEffect(vault) {
        if (vault.state.value.isUnlocked) {
            currentUnlocked()
        } else if (vault.authenticationRequest.value == null) {
            begin()
        }
    }

    request?.let { authentication ->
        VaultAuthenticationHost(
            request = authentication,
            onAuthenticated = { cipher ->
                scope.launch {
                    try {
                        vault.completeAuthentication(authentication.id, cipher)
                        if (vault.state.value.isUnlocked) currentUnlocked()
                        else error = R.string.password_auth_error
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        error = passwordErrorResource(failure)
                    }
                }
            },
            onDismiss = {
                vault.cancelAuthentication(authentication.id)
                currentDismiss()
            },
            onError = {
                vault.cancelAuthentication(authentication.id)
                error = R.string.password_auth_error
            },
            onUseDeviceCredential = {
                vault.cancelAuthentication(authentication.id)
                begin(useDeviceCredential = true)
            },
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = currentDismiss,
            title = { Text(stringResource(R.string.password_auth_error_title)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                DialogActions(
                    confirmText = stringResource(R.string.password_retry),
                    onConfirm = { begin() },
                    dismissText = stringResource(R.string.password_close),
                    onDismiss = currentDismiss,
                )
            },
        )
    }
}

/**
 * Recria o callback do BiometricPrompt após rotação sem repetir a autenticação.
 * Somente o identificador e a informação de lançamento usam rememberSaveable.
 */
@Composable
internal fun VaultAuthenticationHost(
    request: VaultAuthenticationRequest,
    onAuthenticated: (Cipher?) -> Unit,
    onDismiss: () -> Unit,
    onError: () -> Unit,
    onUseDeviceCredential: () -> Unit = onDismiss,
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val authenticated by rememberUpdatedState(onAuthenticated)
    val dismissed by rememberUpdatedState(onDismiss)
    val failed by rememberUpdatedState(onError)
    val fallback by rememberUpdatedState(onUseDeviceCredential)
    val title = stringResource(R.string.password_auth_title)
    val subtitle = stringResource(R.string.password_auth_subtitle)
    val alternative = stringResource(
        if (request.allowsDeviceCredentialFallback) R.string.password_use_device_credential
        else R.string.password_cancel,
    )
    var launched by rememberSaveable(request.id) { mutableStateOf(false) }
    var completed by remember(request.id) { mutableStateOf(false) }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (!completed) {
            completed = true
            if (result.resultCode == Activity.RESULT_OK) authenticated(null) else dismissed()
        }
    }

    val prompt = remember(activity, request.id) {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(it),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (!completed) {
                            completed = true
                            authenticated(result.cryptoObject?.cipher)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (completed) return
                        completed = true
                        val credentialFallback = request.allowsDeviceCredentialFallback &&
                            errorCode in setOf(
                                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                BiometricPrompt.ERROR_LOCKOUT,
                                BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                                BiometricPrompt.ERROR_HW_UNAVAILABLE,
                                BiometricPrompt.ERROR_HW_NOT_PRESENT,
                                BiometricPrompt.ERROR_NO_BIOMETRICS,
                                BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
                            )
                        when {
                            credentialFallback -> fallback()
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON -> dismissed()
                            else -> failed()
                        }
                    }
                },
            )
        }
    }

    LaunchedEffect(request.id, activity) {
        if (activity == null) {
            failed()
            return@LaunchedEffect
        }
        if (launched) return@LaunchedEffect
        launched = true
        try {
            if (request.mode == VaultAuthenticationMode.DEVICE_CREDENTIAL) {
                val keyguard = activity.getSystemService(KeyguardManager::class.java)
                @Suppress("DEPRECATION")
                val intent = keyguard.createConfirmDeviceCredentialIntent(title, subtitle)
                if (intent == null) failed() else credentialLauncher.launch(intent)
            } else {
                val builder = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(
                        if (request.mode == VaultAuthenticationMode.COMBINED) {
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        } else {
                            BiometricManager.Authenticators.BIOMETRIC_STRONG
                        },
                    )
                if (request.mode == VaultAuthenticationMode.BIOMETRIC) {
                    builder.setNegativeButtonText(alternative)
                }
                checkNotNull(prompt).authenticate(
                    builder.build(),
                    BiometricPrompt.CryptoObject(checkNotNull(request.cipher)),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            failed()
        }
    }

    DisposableEffect(prompt, activity) {
        onDispose {
            // O Fragment interno mantém o prompt durante a mudança de configuração.
            if (activity?.isChangingConfigurations != true && !completed) {
                completed = true
                prompt?.cancelAuthentication()
            }
        }
    }
}

internal fun passwordErrorResource(error: Exception): Int = when (error) {
    is PasswordVaultUnavailableException -> R.string.password_device_not_secure
    is PasswordVaultRecoveryException -> R.string.password_recovery_description
    is PasswordVersionMissingException -> R.string.password_missing_version
    else -> R.string.password_auth_error
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
