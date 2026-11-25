package com.joseleandro.scaniqo.ui.backup.platform

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.joseleandro.scaniqo.ui.backup.state.DriveBackupProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class DriveAuthorizationResultTest {
    @Test
    fun canceledActivityStillReportsTheOAuthConfigurationError() {
        val data = Intent()
        val outcome = resolveDriveAuthorizationResult(Activity.RESULT_CANCELED, data) { returnedData ->
            assertSame(data, returnedData)
            throw ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR, "private Google response"))
        }

        assertEquals(
            DriveAuthorizationOutcome.Failed(
                DriveBackupProblem.CONFIGURATION,
                CommonStatusCodes.DEVELOPER_ERROR,
            ),
            outcome,
        )
        assertFalse(outcome.toString().contains("private Google response"))
    }

    @Test
    fun canceledActivityStillReportsNetworkFailuresAndTimeouts() {
        for (statusCode in listOf(CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT)) {
            val outcome = resolveDriveAuthorizationResult(Activity.RESULT_CANCELED, Intent()) {
                throw ApiException(Status(statusCode))
            }

            assertEquals(
                DriveAuthorizationOutcome.Failed(DriveBackupProblem.NETWORK, statusCode),
                outcome,
            )
        }
    }

    @Test
    fun googleCancellationHasVisibleFeedbackAndItsStatusCode() {
        val outcome = resolveDriveAuthorizationResult(Activity.RESULT_CANCELED, Intent()) {
            throw ApiException(Status(CommonStatusCodes.CANCELED))
        }

        assertEquals(
            DriveAuthorizationOutcome.Failed(
                DriveBackupProblem.AUTHORIZATION_CANCELED,
                CommonStatusCodes.CANCELED,
            ),
            outcome,
        )
    }

    @Test
    fun canceledActivityWithoutDataHasVisibleFeedback() {
        val outcome = resolveDriveAuthorizationResult(Activity.RESULT_CANCELED, null) {
            error("A missing Intent must not be decoded")
        }

        assertEquals(
            DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION_CANCELED),
            outcome,
        )
    }

    @Test
    fun successfulActivityWithoutDataReportsAnAuthorizationFailure() {
        var decoded = false
        val outcome = resolveDriveAuthorizationResult(Activity.RESULT_OK, null) {
            decoded = true
            result()
        }

        assertEquals(DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION), outcome)
        assertFalse(decoded)
    }

    @Test
    fun intentSenderLaunchFailureIsNotReportedAsUserCancellation() {
        val data = Intent().putExtra(
            ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION,
            IntentSender.SendIntentException("private launcher response"),
        )
        var decoded = false
        val outcome = resolveDriveAuthorizationResult(Activity.RESULT_CANCELED, data) {
            decoded = true
            result()
        }

        assertEquals(DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION), outcome)
        assertFalse(decoded)
        assertFalse(outcome.toString().contains("private launcher response"))
    }

    @Test
    fun canceledActivityCannotGrantAccessEvenWithAnOtherwiseValidResult() {
        val outcome = resolveDriveAuthorizationResult(Activity.RESULT_CANCELED, Intent()) {
            result()
        }

        assertEquals(
            DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION_CANCELED),
            outcome,
        )
    }

    @Test
    fun unexpectedActivityResultCannotGrantAccess() {
        val outcome = resolveDriveAuthorizationResult(Activity.RESULT_FIRST_USER, Intent()) {
            result()
        }

        assertEquals(DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION), outcome)
    }

    @Test
    fun successfulAuthorizationReturnsTheToken() {
        val outcome = resolveDriveAuthorizationResult(Activity.RESULT_OK, Intent()) { result() }

        assertEquals(DriveAuthorizationOutcome.Authorized("access-token"), outcome)
    }

    @Test
    fun successfulActivityStillRequiresTheDriveScope() {
        val outcome = resolveDriveAuthorizationResult(Activity.RESULT_OK, Intent()) {
            result(scopes = listOf("email"))
        }

        assertEquals(DriveAuthorizationOutcome.Failed(DriveBackupProblem.PERMISSION), outcome)
    }

    @Test
    fun directAuthorizationRequiresANonblankToken() {
        for (token in listOf(null, "", "  ")) {
            assertEquals(
                DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION),
                authorizationOutcome(result(token = token)),
            )
        }
    }

    @Test
    fun directAuthorizationAcceptsDriveScopeAmongOtherGrantedScopes() {
        assertEquals(
            DriveAuthorizationOutcome.Authorized("access-token"),
            authorizationOutcome(result(scopes = listOf("email", DRIVE_FILE_SCOPE))),
        )
    }

    @Test
    fun unexpectedGoogleStatusKeepsOnlyTheNumericDiagnostic() {
        val outcome = authorizationFailure(
            ApiException(Status(12345, "sensitive-account-and-token-details")),
        )

        assertEquals(
            DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION, 12345),
            outcome,
        )
        assertFalse(outcome.toString().contains("sensitive-account-and-token-details"))
    }

    @Test
    fun nonGoogleFailureDoesNotExposeItsMessageOrInventAStatusCode() {
        val outcome = authorizationFailure(IllegalStateException("sensitive-account-and-token-details"))

        assertEquals(DriveAuthorizationOutcome.Failed(DriveBackupProblem.AUTHORIZATION), outcome)
        assertFalse(outcome.toString().contains("sensitive-account-and-token-details"))
    }

    private fun result(
        token: String? = "access-token",
        scopes: List<String> = listOf(DRIVE_FILE_SCOPE),
    ) = AuthorizationResult(null, token, null, scopes, null, null, Bundle())

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
