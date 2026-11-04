package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.feature.onboarding.R

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.ui.onboarding.OnboardingScreen
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun slidesSupportSwipeButtonsAndStateRestorationBeforeCompleting() {
        var finished = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ScaniqoTheme {
                OnboardingScreen(onFinish = { finished++ }, animateIllustrations = false)
            }
        }
        compose.onNodeWithText(context.getString(R.string.onboarding_scan_title)).assertIsDisplayed()
        compose.onNodeWithTag("onboarding_previous").assertDoesNotExist()
        compose.onNodeWithTag("onboarding_pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.onboarding_tags_title)).assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(context.getString(R.string.onboarding_tags_title)).assertIsDisplayed()
        compose.onNodeWithTag("onboarding_previous").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.onboarding_scan_title)).assertIsDisplayed()

        repeat(3) {
            compose.onNodeWithTag("onboarding_next").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText(context.getString(R.string.onboarding_share_title)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, finished) }
        compose.onNodeWithTag("onboarding_next").performClick()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test fun skippingFinishesWithoutHavingToVisitAllSlides() {
        var finished = 0
        compose.setContent {
            ScaniqoTheme {
                OnboardingScreen(onFinish = { finished++ }, animateIllustrations = false)
            }
        }
        compose.onNodeWithTag("onboarding_skip").performClick()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test fun savingDisablesFinishSkipAndBack() {
        compose.setContent {
            ScaniqoTheme {
                OnboardingScreen(onFinish = {}, isSaving = true,
                    initialPage = 3, animateIllustrations = false)
            }
        }
        compose.onNodeWithTag("onboarding_skip").assertIsNotEnabled()
        compose.onNodeWithTag("onboarding_next").assertIsNotEnabled()
        compose.onNodeWithTag("onboarding_previous").assertIsNotEnabled()
    }

    @Test fun failedSaveKeepsLastSlideAndAllowsRetry() {
        var retries = 0
        compose.setContent {
            ScaniqoTheme {
                OnboardingScreen(onFinish = { retries++ }, saveError = true,
                    initialPage = 3, animateIllustrations = false)
            }
        }
        compose.onNodeWithText(context.getString(R.string.onboarding_save_error)).assertIsDisplayed()
        compose.onNodeWithTag("onboarding_next").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun largeFontsKeepSkipAndContinueReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ScaniqoTheme {
                    OnboardingScreen(onFinish = {}, animateIllustrations = false)
                }
            }
        }
        compose.onNodeWithTag("onboarding_skip").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.onboarding_scan_body)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding_next").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("onboarding_previous").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.onboarding_tags_body)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding_next").assertIsDisplayed()
    }
}
