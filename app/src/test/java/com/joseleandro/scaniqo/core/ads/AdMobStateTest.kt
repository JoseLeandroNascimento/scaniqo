package com.joseleandro.scaniqo.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMobStateTest {
    @Test
    fun adsStayHiddenUntilSdkAndConsentAreReady() {
        assertFalse(AdMobState().displayAdsAllowed)
        assertFalse(AdMobState(sdkReady = true).displayAdsAllowed)
        assertFalse(AdMobState(canRequestAds = true).displayAdsAllowed)
        assertTrue(READY.displayAdsAllowed)
    }

    @Test
    fun refreshingConsentHidesAdsEvenWhenPreviousConsentAllowsRequests() {
        assertFalse(READY.copy(consentUpdating = true).displayAdsAllowed)
        assertTrue(READY.copy(consentUpdating = false, requestGeneration = 2).displayAdsAllowed)
    }

    @Test
    fun openPrivacyFormHidesAdsUntilItCloses() {
        assertFalse(READY.copy(privacyFormOpen = true).displayAdsAllowed)
        assertTrue(READY.copy(privacyFormOpen = false).displayAdsAllowed)
    }

    @Test
    fun revokedConsentKeepsAdsHiddenAfterPrivacyFormCloses() {
        assertFalse(
            READY.copy(
                canRequestAds = false,
                privacyFormOpen = false,
                consentUpdating = false,
                requestGeneration = 3,
            ).displayAdsAllowed,
        )
    }

    @Test
    fun requiredPrivacyOptionsEntryDoesNotBlockOtherwiseAllowedAds() {
        assertTrue(READY.copy(privacyOptionsRequired = true).displayAdsAllowed)
    }

    @Test
    fun closingVideoRestoresDisplayAdsWithoutATimedAdFreePeriod() {
        val dismissed = READY.copy(rewardedAdShowing = false)
        assertTrue(dismissed.displayAdsAllowed)
        assertTrue(dismissed.rewardedAdsAllowed)
    }

    @Test
    fun dismissedVideoDoesNotBypassConsentOrPrivacyOptions() {
        val dismissed = READY.copy(rewardedAdShowing = false)
        assertFalse(dismissed.copy(canRequestAds = false).rewardedAdsAllowed)
        assertFalse(dismissed.copy(consentUpdating = true).rewardedAdsAllowed)
        assertFalse(dismissed.copy(privacyFormOpen = true).rewardedAdsAllowed)
    }

    @Test
    fun fullScreenRewardedAdHidesDisplayAdsAndPreventsNestedVideo() {
        assertFalse(READY.copy(rewardedAdShowing = true).displayAdsAllowed)
        assertFalse(READY.copy(rewardedAdShowing = true).rewardedAdsAllowed)
        assertTrue(READY.copy(rewardedAdShowing = false).displayAdsAllowed)
    }

    private companion object {
        val READY = AdMobState(sdkReady = true, canRequestAds = true)
    }
}
