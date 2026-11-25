package com.joseleandro.scaniqo.ui.backup.platform

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupProblem

internal const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

internal sealed interface DriveAuthorizationOutcome {
    data class Authorized(val token: String) : DriveAuthorizationOutcome
    data class Failed(
        val problem: DriveBackupProblem,
        val statusCode: Int? = null,
    ) : DriveAuthorizationOutcome
}

internal fun resolveDriveAuthorizationResult(
    resultCode: Int,
    data: Intent?,
    readResult: (Intent) -> AuthorizationResult,
): DriveAuthorizationOutcome {
    if (data?.hasExtra(ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true) {
        return DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION)
    }
    if (data != null) {
        try {
            // O Google também pode devolver o motivo da falha em um resultado cancelado.
            val result = readResult(data)
            if (resultCode == Activity.RESULT_OK) return authorizationOutcome(result)
        } catch (error: Exception) {
            return authorizationFailure(error)
        }
    }
    return DriveAuthorizationOutcome.Failed(
        if (resultCode == Activity.RESULT_CANCELED) DriveBackupProblem.AUTHORIZATION_CANCELED
        else DriveBackupProblem.AUTHORIZATION,
    )
}

internal fun authorizationOutcome(result: AuthorizationResult): DriveAuthorizationOutcome {
    val token = result.accessToken
    return when {
        DRIVE_FILE_SCOPE !in result.grantedScopes ->
            DriveAuthorizationOutcome.Failed(DriveBackupProblem.PERMISSION)
        token.isNullOrBlank() ->
            DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION)
        else -> DriveAuthorizationOutcome.Authorized(token)
    }
}

internal fun authorizationFailure(error: Exception): DriveAuthorizationOutcome.Failed {
    val statusCode = (error as? ApiException)?.statusCode
    val problem = when (statusCode) {
        CommonStatusCodes.CANCELED -> DriveBackupProblem.AUTHORIZATION_CANCELED
        CommonStatusCodes.DEVELOPER_ERROR -> DriveBackupProblem.CONFIGURATION
        CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> DriveBackupProblem.NETWORK
        else -> DriveBackupProblem.AUTHORIZATION
    }
    return DriveAuthorizationOutcome.Failed(problem, statusCode)
}
