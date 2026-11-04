package com.joseleandro.scaniqo.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.joseleandro.scaniqo.domain.model.AppSettings
import com.joseleandro.scaniqo.domain.model.AppThemeMode
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AndroidOnboardingRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        check(preferences.edit().clear().commit())
        check(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit())
    }

    @Test fun firstOpenShowsTutorialWithoutWritingCompletion() {
        val repository = AndroidOnboardingRepository(context)

        assertFalse(repository.completed.value)
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun completionSurvivesRepositoryRecreation() = runTest {
        val repository = AndroidOnboardingRepository(context)
        repository.complete()

        assertTrue(repository.completed.value)
        assertTrue(AndroidOnboardingRepository(context).completed.value)
    }

    @Test fun restoringAppPreferencesDoesNotResetTutorialCompletion() = runTest {
        val onboarding = AndroidOnboardingRepository(context)
        val settings = AndroidAppSettingsRepository(context)

        settings.update { it.copy(themeMode = AppThemeMode.DARK) }
        onboarding.complete()
        settings.update { AppSettings() }

        assertEquals(AppSettings(), settings.settings.value)
        assertTrue(AndroidOnboardingRepository(context).completed.value)
    }

    @Test fun existingInstallationWithoutCompletionStillShowsTutorial() = runTest {
        AndroidAppSettingsRepository(context).update { it.copy(themeMode = AppThemeMode.DARK) }

        assertFalse(AndroidOnboardingRepository(context).completed.value)
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun invalidStoredTypeFallsBackToIncomplete() {
        check(preferences.edit().putString("completed", "invalid").commit())

        assertFalse(AndroidOnboardingRepository(context).completed.value)
    }

    @Test fun failedCommitDoesNotPublishCompletionAndCanBeRetried() = runTest {
        val storage = ControlledPreferences(preferences)
        val repository = AndroidOnboardingRepository(storage, StandardTestDispatcher(testScheduler))
        storage.failCommit = true

        val failure = runCatching { repository.complete() }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertFalse(repository.completed.value)
        assertFalse(AndroidOnboardingRepository(context).completed.value)

        storage.failCommit = false
        repository.complete()

        assertTrue(repository.completed.value)
        assertTrue(AndroidOnboardingRepository(context).completed.value)
        assertEquals(2, storage.commits)
    }

    @Test fun concurrentCompletionRequestsCommitOnlyOnce() = runTest {
        val storage = ControlledPreferences(preferences)
        val repository = AndroidOnboardingRepository(storage, StandardTestDispatcher(testScheduler))

        repeat(10) { launch { repository.complete() } }
        advanceUntilIdle()

        assertTrue(repository.completed.value)
        assertEquals(1, storage.commits)
    }

    private class ControlledPreferences(
        private val delegate: SharedPreferences,
    ) : SharedPreferences by delegate {
        var failCommit = false
        var commits = 0

        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                    editor.putBoolean(key, value)
                    return this
                }

                override fun commit(): Boolean {
                    commits++
                    return !failCommit && editor.commit()
                }
            }
        }
    }
}
