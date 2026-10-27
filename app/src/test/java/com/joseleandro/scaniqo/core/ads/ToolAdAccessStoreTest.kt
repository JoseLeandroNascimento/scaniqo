package com.joseleandro.scaniqo.core.ads

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ToolAdAccessStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences
        get() = context.getSharedPreferences(ToolAdAccessStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val legacyPreferences
        get() = context.getSharedPreferences(ToolAdAccessStore.LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before
    fun reset() {
        check(preferences.edit().clear().commit())
        check(legacyPreferences.edit().clear().commit())
    }

    @Test
    fun freshInstallRequiresAnAdForTheFirstUseOfEveryTool() {
        val store = store()
        RewardedTool.entries.forEach { tool ->
            assertEquals(ToolAdAccess.AD_REQUIRED, store.access(tool))
            assertNull(store.acquire(tool, "first-" + tool.name))
        }
        RewardedTool.entries.forEach { assertEquals(ToolAdAccess.AD_REQUIRED, store().access(it)) }
    }

    @Test
    fun everyConsecutiveSuccessfulUseNeedsANewRewardWithoutADailyLimitOrInterval() {
        val store = store()
        repeat(4) { index ->
            RewardedTool.entries.forEach { tool ->
                assertEquals(ToolAdAccess.AD_REQUIRED, store.access(tool))
                assertNull(store.acquire(tool, "before-" + index + tool.name))
                val receipt = "ad-" + index + tool.name
                store.recordAdShown(receipt)
                store.grantReward(tool, receipt)
                val permit = requireNotNull(store.acquire(tool, "save-" + index + tool.name))
                store.finish(permit, success = true)
                assertEquals(ToolAdAccess.AD_REQUIRED, store.access(tool))
            }
        }
    }

    @Test
    fun repeatedImpressionsWithoutRewardNeverProvideFreeAccess() {
        val store = store()
        repeat(5) { index ->
            store.recordAdShown("closed-early-" + index)
            RewardedTool.entries.forEach { tool ->
                assertEquals(ToolAdAccess.AD_REQUIRED, store.access(tool))
                assertNull(store.acquire(tool, "try-" + index + tool.name))
            }
        }
        assertEquals(ToolAdAccess.AD_REQUIRED, store().access(C))
    }

    @Test
    fun oldTrialAndFrequencyPreferencesDoNotUnlockAnyTool() {
        check(preferences.edit()
            .putBoolean("legacy_compression_migrated", true)
            .putBoolean("first_use_consumed_COMPRESSION", false)
            .putLong("shown_day", Long.MAX_VALUE)
            .putInt("shown_count", 2)
            .putLong("last_shown_wall", Long.MAX_VALUE)
            .putStringSet("shown_ids", setOf("ad-1", "ad-2"))
            .commit())
        val store = store()
        RewardedTool.entries.forEach {
            assertEquals(ToolAdAccess.AD_REQUIRED, store.access(it))
            assertNull(store.acquire(it, "use-" + it.name))
        }
    }

    @Test
    fun failureNoGainAndCancelledSavePreserveTheEarnedCredit() {
        val store = store()
        store.grantReward(C, "earned")
        listOf("failure", "no-gain", "cancelled-save").forEach { operation ->
            store.finish(requireNotNull(store.acquire(C, operation)), success = false)
            assertEquals(ToolAdAccess.REWARDED, store.access(C))
        }
        store.finish(requireNotNull(store.acquire(C, "saved")), success = true)
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
    }

    @Test
    fun onlyOneOperationCanReserveAnEntitlement() {
        val store = store()
        store.grantReward(C, "earned")
        val first = requireNotNull(store.acquire(C, "first"))
        assertSame(first, store.acquire(C, "first"))
        assertNull(store.acquire(C, "other"))
        assertNull(store.acquire(RewardedTool.IMPORT_PDF, "other", adUnavailable = true))
        store.finish(first, success = false)
        assertNotNull(store.acquire(C, "other"))
    }

    @Test
    fun staleOrRepeatedFinishCannotConsumeAnotherReservation() {
        val store = store()
        store.grantReward(C, "earned")
        val previous = requireNotNull(store.acquire(C, "first"))
        store.finish(previous, success = false)
        val current = requireNotNull(store.acquire(C, "second"))
        store.finish(previous, success = true)
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
        store.finish(current, success = false)
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
    }

    @Test
    fun aForeignPermitCannotConsumeTheReservedCredit() {
        val store = store()
        store.grantReward(C, "earned")
        val reserved = requireNotNull(store.acquire(C, "operation"))
        store.finish(ToolAdPermit(C, "operation", ToolAdAccess.REWARDED, "earned"), success = true)
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
        assertSame(reserved, store.acquire(C, "operation"))
        store.finish(reserved, success = true)
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
    }

    @Test
    fun rewardSurvivesFailureAndRecreationUntilSaved() {
        val store = store()
        store.grantReward(C, "ad-1")
        store.finish(requireNotNull(store.acquire(C, "failed")), success = false)
        val restored = store()
        assertEquals(ToolAdAccess.REWARDED, restored.access(C))
        restored.finish(requireNotNull(restored.acquire(C, "saved")), success = true)
        assertEquals(ToolAdAccess.AD_REQUIRED, store().access(C))
    }

    @Test
    fun rewardReservedDuringProcessDeathIsStillAvailable() {
        val store = store()
        store.grantReward(C, "earned")
        store.acquire(C, "unsaved")
        val restored = store()
        assertEquals(ToolAdAccess.REWARDED, restored.access(C))
        assertNotNull(restored.acquire(C, "retry"))
    }

    @Test
    fun earnedCallbackIsIdempotentEvenAfterConsumptionAndRecreation() {
        val store = store()
        store.grantReward(C, "ad-1")
        store.grantReward(C, "ad-1")
        store.finish(requireNotNull(store.acquire(C, "saved")), success = true)
        val restored = store()
        restored.grantReward(C, "ad-1")
        restored.grantReward(RewardedTool.MERGE_PDFS, "ad-1")
        assertEquals(ToolAdAccess.AD_REQUIRED, restored.access(C))
        assertEquals(ToolAdAccess.AD_REQUIRED, restored.access(RewardedTool.MERGE_PDFS))
    }

    @Test
    fun aSecondCallbackDoesNotStackPendingCredits() {
        val store = store()
        store.grantReward(C, "ad-1")
        store.grantReward(C, "ad-2")
        store.finish(requireNotNull(store.acquire(C, "saved")), success = true)
        store.grantReward(C, "ad-2")
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
    }

    @Test
    fun rewardAndImpressionMayArriveInEitherOrder() {
        val store = store()
        store.grantReward(C, "reward-first")
        store.recordAdShown("reward-first")
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
        store.finish(requireNotNull(store.acquire(C, "saved-first")), success = true)
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
        store.recordAdShown("impression-first")
        store.grantReward(C, "impression-first")
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
        store.finish(requireNotNull(store.acquire(C, "saved-second")), success = true)
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
    }

    @Test
    fun duplicateImpressionDoesNotUnlockAnOperation() {
        val store = store()
        store.recordAdShown("ad-1")
        store.recordAdShown("ad-1")
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
        val restored = store()
        restored.recordAdShown("ad-1")
        assertEquals(ToolAdAccess.AD_REQUIRED, restored.access(C))
    }

    @Test
    fun unavailableAdAllowsOperationWithoutInventingARewardCredit() {
        val store = store()
        store.finish(requireNotNull(store.acquire(C, "offline", adUnavailable = true)), success = true)
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
        assertEquals(ToolAdAccess.AD_REQUIRED, store().access(C))
        assertNull(store.acquire(C, "next-with-ad-available"))
    }

    @Test
    fun unavailableFlagDoesNotConsumeExistingCreditBeforeSaving() {
        val store = store()
        store.grantReward(C, "earned")
        val permit = requireNotNull(store.acquire(C, "offline", adUnavailable = true))
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
        store.finish(permit, success = false)
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
        store.finish(requireNotNull(store.acquire(C, "retry", adUnavailable = true)), success = true)
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
    }

    @Test
    fun savingUnavailableAdOperationDoesNotConsumeALaterReward() {
        val store = store()
        val permit = requireNotNull(store.acquire(C, "offline", adUnavailable = true))
        store.grantReward(C, "late-reward")
        store.finish(permit, success = true)
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
    }

    @Test
    fun invalidIdsAndCorruptPreferencesDoNotCrashOrInventCredit() {
        check(preferences.edit()
            .putString("first_use_consumed_COMPRESSION", "corrupt")
            .putBoolean("reward_id_COMPRESSION", false)
            .putInt("rewarded_ids", -1)
            .putString("shown_count", "corrupt")
            .putString("last_shown_wall", "corrupt")
            .commit())
        val store = store()
        store.grantReward(C, "")
        store.grantReward(C, " ")
        store.recordAdShown(" ")
        assertNull(store.acquire(C, " ", adUnavailable = true))
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
    }

    @Test
    fun rewardsBelongToTheirToolAndOneReceiptCannotCreditAnotherTool() {
        val store = store()
        store.grantReward(C, "compression-video")
        store.grantReward(RewardedTool.SIGNATURE, "signature-video")
        store.grantReward(RewardedTool.FILL_PAGE, "compression-video")
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
        assertEquals(ToolAdAccess.REWARDED, store.access(RewardedTool.SIGNATURE))
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(RewardedTool.FILL_PAGE))
        store.finish(requireNotNull(store.acquire(C, "compress")), success = true)
        assertEquals(ToolAdAccess.AD_REQUIRED, store.access(C))
        assertEquals(ToolAdAccess.REWARDED, store.access(RewardedTool.SIGNATURE))
    }

    @Test
    fun sameOperationIdCannotReuseAnotherToolsReservation() {
        val store = store()
        store.grantReward(RewardedTool.IMPORT_PDF, "import")
        store.grantReward(RewardedTool.MERGE_PDFS, "merge")
        val permit = requireNotNull(store.acquire(RewardedTool.IMPORT_PDF, "same-operation"))
        assertNull(store.acquire(RewardedTool.MERGE_PDFS, "same-operation"))
        store.finish(permit, success = false)
        assertNotNull(store.acquire(RewardedTool.MERGE_PDFS, "same-operation"))
    }

    @Test
    fun migrationPreservesLegacyCreditAndReceiptWithoutReimporting() {
        check(legacyPreferences.edit()
            .putBoolean("first_use_consumed", true)
            .putString("reward_id", "legacy-earned")
            .putStringSet("rewarded_ids", setOf("legacy-earned"))
            .commit())
        val store = store()
        assertEquals(ToolAdAccess.REWARDED, store.access(C))
        RewardedTool.entries.filter { it != C }.forEach {
            assertEquals(ToolAdAccess.AD_REQUIRED, store.access(it))
        }
        store.finish(requireNotNull(store.acquire(C, "saved")), success = true)
        val restored = store()
        assertEquals(ToolAdAccess.AD_REQUIRED, restored.access(C))
        restored.grantReward(RewardedTool.EXTRACT_PAGES, "legacy-earned")
        assertEquals(ToolAdAccess.AD_REQUIRED, restored.access(RewardedTool.EXTRACT_PAGES))
    }

    @Test
    fun migrationIgnoresLegacyUnusedTrialAndFrequencyLimits() {
        check(legacyPreferences.edit()
            .putBoolean("first_use_consumed", false)
            .putLong("shown_day", Long.MAX_VALUE)
            .putInt("shown_count", 2)
            .putLong("last_shown_wall", Long.MAX_VALUE)
            .putStringSet("shown_ids", setOf("legacy-1", "legacy-2"))
            .commit())
        val store = store()
        RewardedTool.entries.forEach { assertEquals(ToolAdAccess.AD_REQUIRED, store.access(it)) }
    }

    @Test
    fun migrationDoesNotOverwriteNewCreditAndPreservesReceiptsFromBothStores() {
        check(legacyPreferences.edit()
            .putString("reward_id", "old-credit")
            .putStringSet("rewarded_ids", setOf("old-consumed"))
            .commit())
        check(preferences.edit()
            .putString("reward_id_COMPRESSION", "current-credit")
            .putStringSet("rewarded_ids", setOf("new-consumed"))
            .commit())
        val store = store()
        val permit = requireNotNull(store.acquire(C, "save"))
        assertEquals("current-credit", permit.rewardId)
        store.finish(permit, success = true)
        val restored = store()
        listOf("old-credit", "old-consumed", "new-consumed", "current-credit").forEach {
            restored.grantReward(C, it)
            assertEquals(ToolAdAccess.AD_REQUIRED, restored.access(C))
        }
    }

    @Test
    fun pendingCreditWithoutReceiptHistoryCannotBeCreditedAgainAfterSaving() {
        check(preferences.edit()
            .putBoolean("legacy_compression_migrated", true)
            .putString("reward_id_FILL_PAGE", "pending")
            .commit())
        val store = store()
        store.finish(requireNotNull(store.acquire(RewardedTool.FILL_PAGE, "save")), success = true)
        val restored = store()
        restored.grantReward(RewardedTool.FILL_PAGE, "pending")
        restored.grantReward(C, "pending")
        assertEquals(ToolAdAccess.AD_REQUIRED, restored.access(RewardedTool.FILL_PAGE))
        assertEquals(ToolAdAccess.AD_REQUIRED, restored.access(C))
    }

    @Test
    fun corruptLegacyPreferencesDoNotCrashOrInventAnEarnedUse() {
        check(legacyPreferences.edit()
            .putString("first_use_consumed", "bad")
            .putInt("reward_id", 42)
            .putInt("rewarded_ids", 42)
            .putString("shown_day", "bad")
            .commit())
        RewardedTool.entries.forEach { assertEquals(ToolAdAccess.AD_REQUIRED, store().access(it)) }
    }

    @Test
    fun pendingCreditsForMultipleToolsSurviveRecreationIndependently() {
        val store = store()
        store.grantReward(RewardedTool.EXTRACT_PAGES, "extract-video")
        store.grantReward(RewardedTool.FILL_PAGE, "fill-video")
        store.acquire(RewardedTool.EXTRACT_PAGES, "interrupted")
        val restored = store()
        assertEquals(ToolAdAccess.REWARDED, restored.access(RewardedTool.EXTRACT_PAGES))
        assertEquals(ToolAdAccess.REWARDED, restored.access(RewardedTool.FILL_PAGE))
        restored.finish(requireNotNull(restored.acquire(RewardedTool.FILL_PAGE, "saved")), success = true)
        assertEquals(ToolAdAccess.AD_REQUIRED, restored.access(RewardedTool.FILL_PAGE))
        assertEquals(ToolAdAccess.REWARDED, restored.access(RewardedTool.EXTRACT_PAGES))
    }

    private fun store() = ToolAdAccessStore(context)

    private companion object {
        val C = RewardedTool.COMPRESSION
    }
}
