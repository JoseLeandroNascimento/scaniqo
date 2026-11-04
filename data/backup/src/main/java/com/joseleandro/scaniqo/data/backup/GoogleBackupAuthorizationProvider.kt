package com.joseleandro.scaniqo.data.backup

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.joseleandro.scaniqo.domain.model.DriveBackupException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class GoogleBackupAuthorizationProvider(context: Context) : BackupAuthorizationProvider {
    private val client = Identity.getAuthorizationClient(context.applicationContext)

    override suspend fun accessToken(accountEmail: String): String = suspendCancellableCoroutine { continuation ->
        val request = AuthorizationRequest.builder()
            .setAccount(Account(accountEmail, "com.google"))
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        client.authorize(request)
            .addOnSuccessListener { result ->
                if (!continuation.isActive) return@addOnSuccessListener
                val token = result.accessToken
                // Workers nunca disparam o PendingIntent. O usuário reconecta pela tela de backup.
                if (result.hasResolution() || token.isNullOrBlank() || DRIVE_FILE_SCOPE !in result.grantedScopes) {
                    continuation.resumeWithException(DriveBackupException.Unauthorized())
                } else continuation.resume(token)
            }
            .addOnFailureListener { failure ->
                if (!continuation.isActive) return@addOnFailureListener
                val error = when ((failure as? ApiException)?.statusCode) {
                    CommonStatusCodes.NETWORK_ERROR -> DriveBackupException.Network()
                    CommonStatusCodes.DEVELOPER_ERROR -> DriveBackupException.Configuration()
                    CommonStatusCodes.INTERNAL_ERROR -> DriveBackupException.Remote(500)
                    else -> DriveBackupException.Unauthorized()
                }
                continuation.resumeWithException(error)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resumeWithException(DriveBackupException.Unauthorized())
            }
    }

    override suspend fun invalidate(accessToken: String): Unit = suspendCancellableCoroutine { continuation ->
        client.clearToken(ClearTokenRequest.builder().setToken(accessToken).build())
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(DriveBackupException.Network()) }
            .addOnCanceledListener { if (continuation.isActive) continuation.resume(Unit) }
    }

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
