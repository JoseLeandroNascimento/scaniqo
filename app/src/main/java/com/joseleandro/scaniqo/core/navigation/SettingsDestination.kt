package com.joseleandro.scaniqo.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joseleandro.scaniqo.ui.onboarding.OnboardingScreen
import com.joseleandro.scaniqo.ui.settings.SettingsRoute
import com.joseleandro.scaniqo.ui.settings.SettingsSection

/** The app coordinates navigation between settings and the reusable onboarding flow. */
@Composable
internal fun SettingsDestination(
    section: SettingsSection,
    onBack: () -> Unit,
    onPersonalization: () -> Unit = {},
    onPdf: () -> Unit = {},
    onBackup: () -> Unit = {},
    onPasswords: () -> Unit = {},
    onLanguage: () -> Unit = {},
) {
    var tutorialVisible by rememberSaveable { mutableStateOf(false) }
    if (tutorialVisible) {
        OnboardingScreen(onFinish = { tutorialVisible = false })
        BackHandler { tutorialVisible = false }
    } else {
        SettingsRoute(
            section = section,
            onBack = onBack,
            onPersonalization = onPersonalization,
            onPdf = onPdf,
            onBackup = onBackup,
            onPasswords = onPasswords,
            onLanguage = onLanguage,
            onTutorial = { tutorialVisible = true },
        )
    }
}
