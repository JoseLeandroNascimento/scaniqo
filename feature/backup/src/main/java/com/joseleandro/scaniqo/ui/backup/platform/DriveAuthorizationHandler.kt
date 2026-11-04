package com.joseleandro.scaniqo.ui.backup.platform

import android.accounts.Account
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.joseleandro.scaniqo.ui.backup.DriveBackupViewModel
import com.joseleandro.scaniqo.ui.backup.effect.DriveBackupEffect
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupProblem

private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

private class AuthorizationUiLifetime {
    var active = true
    var inFlight: Long? = null
}

/** A conta e o consentimento são selecionados pela UI oficial do Google. */
@Composable
internal fun DriveAuthorizationHandler(viewModel: DriveBackupViewModel) {
    val context = LocalContext.current.applicationContext
    val client = remember(context) { Identity.getAuthorizationClient(context) }
    val lifetime = remember(client, viewModel) { AuthorizationUiLifetime() }
    var launchedRequest by rememberSaveable { mutableLongStateOf(-1L) }
    DisposableEffect(lifetime) {
        onDispose { lifetime.active = false }
    }

    fun complete(requestId: Long, result: AuthorizationResult) {
        val token = result.accessToken
        if (DRIVE_FILE_SCOPE !in result.grantedScopes || token.isNullOrBlank()) {
            viewModel.authorizationFailed(requestId, DriveBackupProblem.PERMISSION)
        } else viewModel.authorized(requestId, token)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val requestId = launchedRequest
        launchedRequest = -1L
        if (!viewModel.isAwaitingAuthorization(requestId)) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            viewModel.authorizationFailed(requestId)
        } else {
            try {
                complete(requestId, client.getAuthorizationResultFromIntent(result.data!!))
            } catch (error: Exception) {
                viewModel.authorizationFailed(requestId, authorizationProblem(error))
            }
        }
    }

    fun authorize(requestId: Long) {
        if (!lifetime.active || !viewModel.isAwaitingAuthorization(requestId) ||
            launchedRequest == requestId || lifetime.inFlight == requestId
        ) return
        lifetime.inFlight = requestId
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .apply { viewModel.authorizationAccount(requestId)?.let { setAccount(Account(it, "com.google")) } }
                .build()
            client.authorize(request)
                .addOnSuccessListener { result ->
                    // Listeners do Play Services sobrevivem à Activity: uma tela antiga não lança UI.
                    if (!lifetime.active || !viewModel.isAwaitingAuthorization(requestId)) return@addOnSuccessListener
                    if (lifetime.inFlight == requestId) lifetime.inFlight = null
                    if (result.hasResolution()) {
                        if (!viewModel.authorizationMayShowUi(requestId)) {
                            viewModel.authorizationFailed(requestId, DriveBackupProblem.AUTHORIZATION)
                            return@addOnSuccessListener
                        }
                        val intent = result.pendingIntent
                        if (intent == null) {
                            viewModel.authorizationFailed(requestId, DriveBackupProblem.AUTHORIZATION)
                        } else try {
                            launchedRequest = requestId
                            launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                        } catch (error: Exception) {
                            launchedRequest = -1L
                            viewModel.authorizationFailed(requestId, authorizationProblem(error))
                        }
                    } else complete(requestId, result)
                }
                .addOnFailureListener { error ->
                    if (!lifetime.active) return@addOnFailureListener
                    if (lifetime.inFlight == requestId) lifetime.inFlight = null
                    viewModel.authorizationFailed(requestId, authorizationProblem(error))
                }
        } catch (error: Exception) {
            lifetime.inFlight = null
            viewModel.authorizationFailed(requestId, authorizationProblem(error))
        }
    }

    LaunchedEffect(viewModel, client, lifetime) {
        if (!viewModel.isAwaitingAuthorization(launchedRequest)) launchedRequest = -1L
        // Recriação antes de lançar o consentimento: retoma o Task. Depois de lançar,
        // rememberSaveable e ActivityResultRegistry preservam a entrega sem abrir outra tela.
        viewModel.awaitingAuthorizationRequestId()?.let(::authorize)
        viewModel.effects.collect { effect ->
            when (effect) {
                is DriveBackupEffect.Authorize -> authorize(effect.requestId)
                is DriveBackupEffect.ClearToken -> {
                    runCatching { client.clearToken(ClearTokenRequest.builder().setToken(effect.token).build()) }
                }
                is DriveBackupEffect.Revoke -> {
                    try {
                        val request = RevokeAccessRequest.builder()
                            .setAccount(Account(effect.email, "com.google"))
                            .setScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                            .build()
                        client.revokeAccess(request)
                            .addOnSuccessListener { viewModel.disconnected(effect.requestId, success = true) }
                            .addOnFailureListener { viewModel.disconnected(effect.requestId, success = false) }
                    } catch (_: Exception) {
                        viewModel.disconnected(effect.requestId, success = false)
                    }
                }
            }
        }
    }
}

private fun authorizationProblem(error: Exception): DriveBackupProblem? = when ((error as? ApiException)?.statusCode) {
    CommonStatusCodes.CANCELED -> null
    CommonStatusCodes.DEVELOPER_ERROR -> DriveBackupProblem.CONFIGURATION
    CommonStatusCodes.NETWORK_ERROR -> DriveBackupProblem.NETWORK
    else -> DriveBackupProblem.AUTHORIZATION
}
