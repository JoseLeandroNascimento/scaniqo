package com.joseleandro.scaniqo.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.joseleandro.scaniqo.core.localization.AppLanguages
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.settings.R
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguageSettingsInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val englishName get() = requireNotNull(AppLanguages.find("en-US")).nativeName
    private val applyLabel get() = compose.activity.getString(R.string.localization_apply)

    @Test fun pendingSelectionSurvivesRestorationAndOnlyAppliesAfterConfirmation() {
        val applied = mutableListOf<String>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ScaniqoTheme {
                LanguageSettingsScreen(currentTag = "pt-BR", onBack = {}, onApply = applied::add)
            }
        }
        compose.onNodeWithText(applyLabel).assertIsNotEnabled()
        compose.onNodeWithText(englishName).performScrollTo().performClick()
        compose.onNodeWithText(englishName).assertIsSelected()
        compose.onNodeWithText(applyLabel).assertIsEnabled()
        compose.runOnIdle { assertEquals(emptyList<String>(), applied) }

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(englishName).assertIsSelected()
        compose.onNodeWithText(applyLabel).assertIsEnabled()
        compose.runOnIdle { assertEquals(emptyList<String>(), applied) }
        compose.onNodeWithText(applyLabel).performClick()
        compose.runOnIdle { assertEquals(listOf("en-US"), applied) }
    }

    @Test fun followingTheSystemAppliesAnEmptyLanguageTag() {
        val applied = mutableListOf<String>()
        compose.setContent {
            ScaniqoTheme {
                LanguageSettingsScreen(currentTag = "pt-BR", onBack = {}, onApply = applied::add)
            }
        }
        val systemLabel = compose.activity.getString(R.string.localization_system)
        compose.onNodeWithText(systemLabel).performScrollTo().performClick()
        compose.onNodeWithText(systemLabel).assertIsSelected()
        compose.runOnIdle { assertEquals(emptyList<String>(), applied) }
        compose.onNodeWithText(applyLabel).performClick()
        compose.runOnIdle { assertEquals(listOf(""), applied) }
    }

    @Test fun goingBackDoesNotApplyThePendingSelection() {
        var backCalls = 0
        val applied = mutableListOf<String>()
        compose.setContent {
            ScaniqoTheme {
                LanguageSettingsScreen(
                    currentTag = "pt-BR",
                    onBack = { backCalls++ },
                    onApply = applied::add,
                )
            }
        }
        compose.onNodeWithText(englishName).performScrollTo().performClick()
        compose.onNodeWithText(applyLabel).assertIsEnabled()
        compose.onNodeWithContentDescription(
            compose.activity.getString(CoreResourcesR.string.editor_tool_back),
        ).performClick()
        compose.runOnIdle {
            assertEquals(1, backCalls)
            assertEquals(emptyList<String>(), applied)
        }
    }
}
