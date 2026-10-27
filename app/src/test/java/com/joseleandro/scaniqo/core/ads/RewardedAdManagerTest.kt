package com.joseleandro.scaniqo.core.ads

import android.app.Activity
import android.app.Application
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class RewardedAdManagerTest {
    @Test
    fun preparationRequiresConsentSdkAndConfiguredUnit() = runTest {
        val fixture = Fixture(this)
        fixture.ads.value = AdMobState()
        fixture.manager.prepare()
        assertTrue(fixture.loader.requests.isEmpty())
        fixture.ads.value = READY.copy(consentUpdating = true)
        fixture.manager.prepare()
        assertTrue(fixture.loader.requests.isEmpty())
        fixture.ads.value = READY
        fixture.unitId = null
        fixture.manager.prepare()
        assertEquals(RewardedAdStatus.UNAVAILABLE, fixture.status)
        assertTrue(fixture.loader.requests.isEmpty())
        fixture.unitId = TEST_UNIT
        fixture.manager.prepare()
        assertEquals(TEST_UNIT, fixture.loader.requests.single().unitId)
    }

    @Test
    fun preparationIsIdempotentAndDoesNotShowTheLoadedAd() = runTest {
        val fixture = Fixture(this)
        fixture.manager.prepare()
        fixture.manager.prepare()
        assertEquals(1, fixture.loader.requests.size)
        val ad = fixture.loader.complete()
        runCurrent()
        fixture.manager.prepare()
        assertEquals(RewardedAdStatus.READY, fixture.status)
        assertEquals(1, fixture.loader.requests.size)
        assertEquals(0, ad.showCount)
        assertEquals(0, fixture.rewards)
    }

    @Test
    fun sdkBackgroundCallbackIsDispatchedBeforePublishingReady() = runTest {
        val fixture = Fixture(this)
        fixture.manager.prepare()
        Thread { fixture.loader.complete() }.apply { start(); join() }
        assertEquals(RewardedAdStatus.LOADING, fixture.status)
        runCurrent()
        assertEquals(RewardedAdStatus.READY, fixture.status)
    }

    @Test
    fun leavingTheScreenDiscardsLateLoadResults() = runTest {
        val fixture = Fixture(this)
        fixture.manager.prepare()
        fixture.manager.releasePendingAd()
        val abandoned = fixture.loader.complete()
        runCurrent()
        assertEquals(1, abandoned.destroyCount)
        assertEquals(RewardedAdStatus.IDLE, fixture.status)
        assertEquals(0, fixture.rewards)
    }

    @Test
    fun changingConsentInvalidatesEvenAStillAllowedLoad() = runTest {
        val fixture = Fixture(this)
        fixture.manager.prepare()
        fixture.ads.value = READY.copy(requestGeneration = 1)
        runCurrent()
        fixture.manager.prepare()
        val stale = fixture.loader.complete(index = 0)
        val current = fixture.loader.complete(index = 1)
        runCurrent()
        assertEquals(1, stale.destroyCount)
        assertEquals(0, current.destroyCount)
        assertEquals(RewardedAdStatus.READY, fixture.status)
    }

    @Test
    fun showChecksCurrentConsentBeforeTheObserverRuns() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        fixture.ads.value = READY.copy(canRequestAds = false)
        assertFalse(fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION))
        assertEquals(0, ad.showCount)
        assertEquals(1, ad.destroyCount)
        assertEquals(0, fixture.rewards)
    }

    @Test
    fun doubleTapShowsOnlyOnceAndRewardSurvivesHostStopUntilDismissal() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        assertTrue(fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION))
        assertFalse(fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION))
        fixture.manager.releasePendingAd()
        runCurrent()
        assertEquals(1, ad.showCount)
        assertEquals(RewardedAdStatus.SHOWING, fixture.status)
        assertTrue(fixture.ads.value.rewardedAdShowing)
        assertEquals(0, ad.destroyCount)

        ad.earn()
        ad.earn()
        runCurrent()
        assertEquals(1, fixture.rewards)
        assertEquals(RewardedAdStatus.SHOWING, fixture.status)
        assertEquals(0, ad.destroyCount)
        assertNull(fixture.manager.state.value.completion)
        assertTrue(fixture.shownIds.isEmpty())

        ad.dismiss()
        ad.dismiss()
        runCurrent()
        assertEquals(1, ad.destroyCount)
        assertFalse(fixture.ads.value.rewardedAdShowing)
        assertEquals(RewardedAdStatus.IDLE, fixture.status)
        assertEquals(
            RewardedAdCompletion(REQUEST_ID, earned = true, failed = false, tool = RewardedTool.COMPRESSION),
            fixture.manager.state.value.completion,
        )
        assertTrue(fixture.ads.value.displayAdsAllowed)
    }

    @Test
    fun dismissalWithoutEarnedCallbackGivesNoReward() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION)
        ad.dismiss()
        runCurrent()
        ad.earn()
        runCurrent()
        assertEquals(0, fixture.rewards)
        assertEquals(1, ad.destroyCount)
        assertFalse(fixture.ads.value.rewardedAdShowing)
        assertEquals(
            RewardedAdCompletion(REQUEST_ID, earned = false, failed = false, tool = RewardedTool.COMPRESSION),
            fixture.manager.state.value.completion,
        )
    }

    @Test
    fun showFailureDoesNotRewardOrKeepTheAdCached() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION)
        ad.fail()
        runCurrent()
        ad.earn()
        ad.dismiss()
        runCurrent()
        assertEquals(0, fixture.rewards)
        assertEquals(1, ad.destroyCount)
        assertFalse(fixture.ads.value.rewardedAdShowing)
        assertEquals(RewardedAdStatus.UNAVAILABLE, fixture.status)
        assertEquals(
            RewardedAdCompletion(REQUEST_ID, earned = false, failed = true, tool = RewardedTool.COMPRESSION),
            fixture.manager.state.value.completion,
        )
        assertTrue(fixture.shownIds.isEmpty())
        fixture.manager.prepare()
        assertEquals(2, fixture.loader.requests.size)
    }

    @Test
    fun loadFailureAllowsExplicitRetryAndOldFailureCannotClearNewAd() = runTest {
        val fixture = Fixture(this)
        fixture.manager.prepare()
        fixture.loader.requests.single().failed()
        runCurrent()
        assertEquals(RewardedAdStatus.UNAVAILABLE, fixture.status)
        fixture.manager.prepare()
        fixture.loader.requests.first().failed()
        fixture.loader.complete(index = 1)
        runCurrent()
        assertEquals(RewardedAdStatus.READY, fixture.status)
    }

    @Test
    fun expiredCachedAdIsDestroyedAndNeverDisplayed() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        advanceTimeBy(RewardedAdManager.CACHE_LIFETIME_MILLIS)
        runCurrent()
        assertFalse(fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION))
        assertEquals(1, ad.destroyCount)
        assertEquals(0, ad.showCount)
        assertEquals(RewardedAdStatus.UNAVAILABLE, fixture.status)
    }

    @Test
    fun showChecksExpiryEvenBeforeTheExpiryJobRuns() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        fixture.clockOffset = RewardedAdManager.CACHE_LIFETIME_MILLIS
        assertFalse(fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION))
        assertEquals(1, ad.destroyCount)
        assertEquals(0, ad.showCount)
        assertEquals(RewardedAdStatus.UNAVAILABLE, fixture.status)
    }

    @Test
    fun loadingTimeoutMakesLateSuccessUnusable() = runTest {
        val fixture = Fixture(this)
        fixture.manager.prepare()
        runCurrent()
        advanceTimeBy(RewardedAdManager.LOAD_TIMEOUT_MILLIS)
        runCurrent()
        assertEquals(RewardedAdStatus.UNAVAILABLE, fixture.status)
        val ad = fixture.loader.complete()
        runCurrent()
        assertEquals(1, ad.destroyCount)
        assertEquals(RewardedAdStatus.UNAVAILABLE, fixture.status)
    }

    @Test
    fun finishingActivityDoesNotConsumeReadyAd() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        fixture.activity.finish()
        assertFalse(fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION))
        assertEquals(0, ad.showCount)
        assertEquals(0, ad.destroyCount)
        assertEquals(RewardedAdStatus.READY, fixture.status)
        fixture.manager.releasePendingAd()
        assertEquals(1, ad.destroyCount)
    }

    @Test
    fun synchronousSdkFailuresResetPresentationAndAllowRetry() = runTest {
        val fixture = Fixture(this)
        fixture.loader.throwOnLoad = true
        fixture.manager.prepare()
        assertEquals(RewardedAdStatus.UNAVAILABLE, fixture.status)
        fixture.loader.throwOnLoad = false
        val ad = fixture.load()
        ad.throwOnShow = true
        assertTrue(fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION))
        assertEquals(RewardedAdStatus.UNAVAILABLE, fixture.status)
        assertEquals(1, ad.destroyCount)
        assertFalse(fixture.ads.value.rewardedAdShowing)
        assertEquals(0, fixture.rewards)
        assertEquals(
            RewardedAdCompletion(REQUEST_ID, earned = false, failed = true, tool = RewardedTool.COMPRESSION),
            fixture.manager.state.value.completion,
        )
    }

    @Test
    fun onlySdkShownCallbackRecordsImpressionAndItsIdMatchesTheEarnedReward() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        assertTrue(fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION))
        assertTrue(fixture.shownIds.isEmpty())
        ad.earn()
        runCurrent()
        assertEquals(1, fixture.rewards)
        assertNull(fixture.manager.state.value.completion)
        ad.appear()
        ad.appear()
        runCurrent()
        assertEquals(1, fixture.shownIds.size)
        assertEquals(fixture.rewardedIds.single(), fixture.shownIds.single())
        assertFalse(fixture.shownIds.single() == REQUEST_ID)
        ad.dismiss()
        runCurrent()
        assertEquals(
            RewardedAdCompletion(REQUEST_ID, earned = true, failed = false, tool = RewardedTool.COMPRESSION),
            fixture.manager.state.value.completion,
        )
    }

    @Test
    fun completionSurvivesOffscreenReleaseAndPreloadingUntilCorrelatedAcknowledgement() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION)
        ad.earn()
        ad.dismiss()
        runCurrent()
        val completion = RewardedAdCompletion(REQUEST_ID, earned = true, failed = false, tool = RewardedTool.COMPRESSION)
        fixture.manager.releasePendingAd()
        assertEquals(completion, fixture.manager.state.value.completion)
        fixture.manager.prepare()
        fixture.loader.complete()
        runCurrent()
        assertEquals(RewardedAdStatus.READY, fixture.status)
        assertEquals(completion, fixture.manager.state.value.completion)
        fixture.manager.acknowledgeCompletion("another-request")
        assertEquals(completion, fixture.manager.state.value.completion)
        fixture.manager.acknowledgeCompletion(REQUEST_ID)
        assertNull(fixture.manager.state.value.completion)
        assertEquals(RewardedAdStatus.READY, fixture.status)
    }

    @Test
    fun previousRequestCallbacksCannotRewardOrAcknowledgeTheNextPresentation() = runTest {
        val fixture = Fixture(this)
        val previous = fixture.load()
        fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION)
        previous.dismiss()
        runCurrent()
        fixture.manager.acknowledgeCompletion(REQUEST_ID)
        val next = fixture.load()
        val nextRequest = "next-signature"
        fixture.manager.show(fixture.activity, nextRequest, RewardedTool.SIGNATURE)
        assertEquals(RewardedTool.SIGNATURE, next.shownTool)
        previous.earn()
        previous.dismiss()
        runCurrent()
        assertEquals(0, fixture.rewards)
        assertNull(fixture.manager.state.value.completion)
        next.earn()
        next.dismiss()
        runCurrent()
        assertEquals(1, fixture.rewards)
        assertEquals(listOf(RewardedTool.SIGNATURE), fixture.rewardedTools)
        val completion = RewardedAdCompletion(nextRequest, earned = true, failed = false, tool = RewardedTool.SIGNATURE)
        assertEquals(completion, fixture.manager.state.value.completion)
        fixture.manager.acknowledgeCompletion(REQUEST_ID)
        assertEquals(completion, fixture.manager.state.value.completion)
    }

    @Test
    fun rejectedShowDoesNotPublishCompletionOrConsumeTheCachedAd() = runTest {
        val fixture = Fixture(this)
        assertFalse(fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION))
        assertNull(fixture.manager.state.value.completion)
        val ad = fixture.load()
        assertFalse(fixture.manager.show(fixture.activity, " ", RewardedTool.COMPRESSION))
        assertEquals(0, ad.showCount)
        assertEquals(0, ad.destroyCount)
        assertEquals(RewardedAdStatus.READY, fixture.status)
        assertNull(fixture.manager.state.value.completion)
    }

    @Test
    fun sdkBackgroundEventsPublishCompletionOnlyAfterDismissal() = runTest {
        val fixture = Fixture(this)
        val ad = fixture.load()
        fixture.manager.show(fixture.activity, REQUEST_ID, RewardedTool.COMPRESSION)
        Thread { ad.appear(); ad.earn() }.apply { start(); join() }
        assertTrue(fixture.shownIds.isEmpty())
        assertEquals(0, fixture.rewards)
        runCurrent()
        assertEquals(1, fixture.shownIds.size)
        assertEquals(1, fixture.rewards)
        assertNull(fixture.manager.state.value.completion)
        Thread { ad.dismiss() }.apply { start(); join() }
        assertNull(fixture.manager.state.value.completion)
        runCurrent()
        assertEquals(
            RewardedAdCompletion(REQUEST_ID, earned = true, failed = false, tool = RewardedTool.COMPRESSION),
            fixture.manager.state.value.completion,
        )
    }

    @Test
    fun eachPresentationCreditsAndReportsOnlyItsRequestedTool() = runTest {
        val fixture = Fixture(this)
        RewardedTool.entries.forEach { tool ->
            val ad = fixture.load()
            val requestId = "request-" + tool.name
            assertTrue(fixture.manager.show(fixture.activity, requestId, tool))
            assertEquals(tool, ad.shownTool)
            ad.earn()
            ad.earn()
            runCurrent()
            assertEquals(tool, fixture.rewardedTools.last())
            assertNull(fixture.manager.state.value.completion)
            ad.dismiss()
            runCurrent()
            assertEquals(
                RewardedAdCompletion(requestId, earned = true, failed = false, tool = tool),
                fixture.manager.state.value.completion,
            )
            fixture.manager.acknowledgeCompletion(requestId)
        }
        assertEquals(RewardedTool.entries, fixture.rewardedTools)
        assertEquals(RewardedTool.entries.size, fixture.rewardedIds.distinct().size)
    }

    @Test
    fun consecutiveOperationsOnTheSameToolCanEachPresentWithoutCooldown() = runTest {
        val fixture = Fixture(this)
        repeat(4) { index ->
            val requestId = "consecutive-" + index
            val ad = fixture.load()
            assertTrue(fixture.manager.show(fixture.activity, requestId, RewardedTool.COMPRESSION))
            ad.appear()
            ad.earn()
            ad.dismiss()
            runCurrent()
            assertEquals(
                RewardedAdCompletion(requestId, earned = true, failed = false, tool = RewardedTool.COMPRESSION),
                fixture.manager.state.value.completion,
            )
            fixture.manager.acknowledgeCompletion(requestId)
        }
        assertEquals(4, fixture.shownIds.distinct().size)
        assertEquals(4, fixture.rewardedIds.distinct().size)
        assertEquals(List(4) { RewardedTool.COMPRESSION }, fixture.rewardedTools)
    }

    private class Fixture(private val test: TestScope) {
        val ads = MutableStateFlow(READY)
        val loader = FakeLoader()
        var unitId: String? = TEST_UNIT
        val rewardedIds = mutableListOf<String>()
        val rewardedTools = mutableListOf<RewardedTool>()
        val shownIds = mutableListOf<String>()
        val rewards get() = rewardedIds.size
        var clockOffset = 0L
        val activity: Activity by lazy {
            Robolectric.buildActivity(Activity::class.java).setup().get()
        }
        val manager = RewardedAdManager(
            adMobState = ads,
            adUnitId = { unitId },
            grantReward = { tool, id -> rewardedTools += tool; rewardedIds += id },
            recordAdShown = { shownIds += it },
            setShowing = { ads.value = ads.value.copy(rewardedAdShowing = it) },
            loader = loader,
            scope = test.backgroundScope,
            elapsedRealtime = { test.testScheduler.currentTime + clockOffset },
        )
        val status get() = manager.state.value.status

        fun load(): FakeAd {
            manager.prepare()
            val ad = loader.complete()
            test.runCurrent()
            return ad
        }
    }

    private class FakeLoader : RewardedAdLoader {
        data class Request(
            val unitId: String,
            val loaded: (LoadedRewardedAd) -> Unit,
            val failed: () -> Unit,
        )
        val requests = mutableListOf<Request>()
        var throwOnLoad = false

        override fun load(unitId: String, onLoaded: (LoadedRewardedAd) -> Unit, onFailed: () -> Unit) {
            if (throwOnLoad) throw IllegalStateException("SDK unavailable")
            requests += Request(unitId, onLoaded, onFailed)
        }

        fun complete(index: Int = requests.lastIndex): FakeAd = FakeAd().also(requests[index].loaded)
    }

    private class FakeAd : LoadedRewardedAd {
        var showCount = 0
        var destroyCount = 0
        var throwOnShow = false
        var shownTool: RewardedTool? = null
        private var shown: () -> Unit = {}
        private var earned: () -> Unit = {}
        private var dismissed: () -> Unit = {}
        private var failed: () -> Unit = {}

        override fun show(
            activity: Activity,
            tool: RewardedTool,
            onShown: () -> Unit,
            onEarned: () -> Unit,
            onDismissed: () -> Unit,
            onFailed: () -> Unit,
        ) {
            showCount++
            shownTool = tool
            if (throwOnShow) throw IllegalStateException("No presentation available")
            shown = onShown
            earned = onEarned
            dismissed = onDismissed
            failed = onFailed
        }

        fun appear() = shown()
        fun earn() = earned()
        fun dismiss() = dismissed()
        fun fail() = failed()
        override fun destroy() { destroyCount++ }
    }

    private companion object {
        val READY = AdMobState(sdkReady = true, canRequestAds = true)
        const val TEST_UNIT = "ca-app-pub-3940256099942544/5224354917"
        const val REQUEST_ID = "strong-compression-request"
    }
}
