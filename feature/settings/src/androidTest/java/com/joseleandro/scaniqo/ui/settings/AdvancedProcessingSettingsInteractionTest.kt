package com.joseleandro.scaniqo.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joseleandro.scaniqo.domain.model.AppSettings
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlement
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.settings.R
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdvancedProcessingSettingsInteractionTest {
    @Test fun freeUserOpensPremiumWithoutEnablingHardwareProcessing() {
        var upgrades = 0
        val changes = mutableListOf<SettingsChange>()
        compose.setContent {
            ScaniqoTheme {
                SettingsScreen(
                    preferences = AppSettings(),
                    section = SettingsSection.PDF,
                    onChange = { changes += it },
                    onBack = {},
                    onPremium = { upgrades++ },
                )
            }
        }
        compose.onNodeWithText(
            compose.activity.getString(CoreResourcesR.string.premium_access_processing),
        ).performClick()
        compose.runOnIdle {
            assertEquals(1, upgrades)
            assertEquals(emptyList<SettingsChange>(), changes)
        }
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun advancedProcessingToggleCanBeEnabledAndDisabledFromPdfPreferences() {
        val changes = mutableListOf<SettingsChange>()
        compose.setContent {
            var settings by remember { mutableStateOf(AppSettings()) }
            ScaniqoTheme {
                SettingsScreen(
                    preferences = settings,
                    section = SettingsSection.PDF,
                    premiumEntitlement = PremiumEntitlement.PREMIUM,
                    onChange = { change ->
                        changes += change
                        if (change is SettingsChange.AdvancedProcessing) {
                            settings = settings.copy(advancedProcessingEnabled = change.value)
                        }
                    },
                    onBack = {},
                )
            }
        }

        val option = compose.onNodeWithText(
            compose.activity.getString(R.string.settings_advanced_processing),
        )
        option.assertIsOff().performClick()
        option.assertIsOn().performClick()
        option.assertIsOff()

        compose.runOnIdle {
            assertEquals(
                listOf(SettingsChange.AdvancedProcessing(true), SettingsChange.AdvancedProcessing(false)),
                changes,
            )
        }
    }
}
