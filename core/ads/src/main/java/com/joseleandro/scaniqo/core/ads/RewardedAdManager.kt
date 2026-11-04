package com.joseleandro.scaniqo.core.ads

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class RewardedAdStatus { IDLE, LOADING, READY, SHOWING, UNAVAILABLE }

data class RewardedAdCompletion(
    val requestId: String,
    val earned: Boolean,
    val failed: Boolean,
    val tool: RewardedTool,
)

data class RewardedAdUiState(
    val status: RewardedAdStatus = RewardedAdStatus.IDLE,
    val completion: RewardedAdCompletion? = null,
)

/** Owns one opt-in video at a time; never stores the Activity used to present it. */
class RewardedAdManager internal constructor(
    private val adMobState: StateFlow<AdMobState>,
    private val adUnitId: () -> String?,
    private val grantReward: (RewardedTool, String) -> Unit,
    private val recordAdShown: (String) -> Unit,
    private val setShowing: (Boolean) -> Unit,
    private val loader: RewardedAdLoader,
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long,
) {
    constructor(
        adMobManager: AdMobManager,
        accessPolicy: ToolAdAccessPolicy,
    ) : this(
        adMobState = adMobManager.state,
        adUnitId = { adMobManager.unitId(AdPlacement.TOOLS_REWARDED) },
        grantReward = accessPolicy::grantReward,
        recordAdShown = accessPolicy::recordAdShown,
        setShowing = adMobManager::setRewardedAdShowing,
        loader = GoogleRewardedAdLoader(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        elapsedRealtime = SystemClock::elapsedRealtime,
    )

    private val mutableState = MutableStateFlow(RewardedAdUiState())
    val state = mutableState.asStateFlow()
    private var request = 0L
    private var consentGeneration: Int? = null
    private var cachedAd: LoadedRewardedAd? = null
    private var loadedAt = 0L
    private var showing: ShowingAd? = null
    private var expiryJob: Job? = null
    private var loadingTimeoutJob: Job? = null

    init {
        scope.launch {
            adMobState.collect { ads ->
                // A fullscreen presentation hides display ads. Wait for its own terminal
                // callback instead of destroying it when that gate changes.
                val consentChanged = consentGeneration?.let { it != ads.requestGeneration } == true
                if (showing == null && (!ads.rewardedAdsAllowed || consentChanged)) {
                    releasePendingAd()
                }
            }
        }
    }

    /** Loads only while eligible. Calling this never presents a video. */
    @MainThread
    fun prepare() {
        if (showing != null) return
        val ads = adMobState.value
        val unitId = adUnitId()
        if (!ads.rewardedAdsAllowed) {
            releasePendingAd()
            return
        }
        if (unitId == null) {
            releasePendingAd()
            update(RewardedAdStatus.UNAVAILABLE)
            return
        }
        if (consentGeneration == ads.requestGeneration) {
            if (state.value.status == RewardedAdStatus.LOADING) return
            if (cachedAd != null && elapsedRealtime() - loadedAt < CACHE_LIFETIME_MILLIS) return
        }
        releasePendingAd()
        consentGeneration = ads.requestGeneration
        val loadRequest = request
        update(RewardedAdStatus.LOADING)
        loadingTimeoutJob = scope.launch {
            delay(LOAD_TIMEOUT_MILLIS)
            if (request == loadRequest && state.value.status == RewardedAdStatus.LOADING) {
                releasePendingAd()
                update(RewardedAdStatus.UNAVAILABLE)
            }
        }
        try {
            loader.load(
                unitId,
                onLoaded = { ad ->
                    scope.launch {
                        val current = adMobState.value
                        if (request != loadRequest || !current.rewardedAdsAllowed ||
                            current.requestGeneration != ads.requestGeneration ||
                            state.value.status != RewardedAdStatus.LOADING
                        ) {
                            destroy(ad)
                            return@launch
                        }
                        loadingTimeoutJob?.cancel()
                        loadingTimeoutJob = null
                        cachedAd = ad
                        loadedAt = elapsedRealtime()
                        update(RewardedAdStatus.READY)
                        expiryJob = scope.launch {
                            delay(CACHE_LIFETIME_MILLIS)
                            if (cachedAd === ad && request == loadRequest) {
                                releasePendingAd()
                                update(RewardedAdStatus.UNAVAILABLE)
                            }
                        }
                    }
                },
                onFailed = {
                    scope.launch {
                        if (request == loadRequest && state.value.status == RewardedAdStatus.LOADING) {
                            releasePendingAd()
                            update(RewardedAdStatus.UNAVAILABLE)
                        }
                    }
                },
            )
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Rewarded ad request failed.", failure)
            if (request == loadRequest) {
                releasePendingAd()
                update(RewardedAdStatus.UNAVAILABLE)
            }
        }
    }

    /**
     * Called only by an explicit watch action. True means the attempt was accepted;
     * its terminal result is delivered through completion, including SDK show failures.
     */
    @MainThread
    fun show(activity: Activity, requestId: String, tool: RewardedTool): Boolean {
        if (requestId.isBlank() || activity.isFinishing || activity.isDestroyed || showing != null) return false
        // A queued click after expiry must preserve UNAVAILABLE so the retry stays visible.
        val ad = cachedAd ?: return false
        val ads = adMobState.value
        if (!ads.rewardedAdsAllowed || consentGeneration != ads.requestGeneration) {
            releasePendingAd()
            return false
        }
        if (elapsedRealtime() - loadedAt >= CACHE_LIFETIME_MILLIS) {
            releasePendingAd()
            update(RewardedAdStatus.UNAVAILABLE)
            return false
        }
        // Consume before calling the SDK, so two clicks cannot present the same ad.
        cachedAd = null
        expiryJob?.cancel()
        expiryJob = null
        val presentation = ShowingAd(ad, requestId, UUID.randomUUID().toString(), tool)
        showing = presentation
        update(RewardedAdStatus.SHOWING)
        setShowing(true)
        try {
            ad.show(
                activity,
                tool,
                onShown = {
                    scope.launch {
                        if (!presentation.shownRecorded) {
                            presentation.shownRecorded = true
                            recordAdShown(presentation.adId)
                        }
                    }
                },
                onEarned = {
                    scope.launch {
                        if (showing === presentation && !presentation.rewardGranted) {
                            presentation.rewardGranted = true
                            grantReward(presentation.tool, presentation.adId)
                        }
                    }
                },
                onDismissed = { scope.launch { finish(presentation, failed = false) } },
                onFailed = { scope.launch { finish(presentation, failed = true) } },
            )
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Rewarded ad presentation failed.", failure)
            finish(presentation, failed = true)
        }
        return true
    }

    /** A stale screen cannot acknowledge a newer request's completion. */
    @MainThread
    fun acknowledgeCompletion(requestId: String) {
        if (mutableState.value.completion?.requestId == requestId) {
            mutableState.value = mutableState.value.copy(completion = null)
        }
    }

    /** Releases offscreen preload work; an already open video owns its dismissal. */
    @MainThread
    fun releasePendingAd() {
        if (showing != null) return
        request++
        consentGeneration = null
        loadingTimeoutJob?.cancel()
        loadingTimeoutJob = null
        expiryJob?.cancel()
        expiryJob = null
        cachedAd?.let(::destroy)
        cachedAd = null
        update(RewardedAdStatus.IDLE)
    }

    private fun finish(presentation: ShowingAd, failed: Boolean) {
        if (showing !== presentation) return
        showing = null
        consentGeneration = null
        request++
        destroy(presentation.ad)
        setShowing(false)
        mutableState.value = mutableState.value.copy(
            status = if (failed) RewardedAdStatus.UNAVAILABLE else RewardedAdStatus.IDLE,
            completion = RewardedAdCompletion(
                presentation.requestId, presentation.rewardGranted, failed, presentation.tool,
            ),
        )
    }

    private fun update(status: RewardedAdStatus) {
        // Preloading and lifecycle cleanup must not lose a result before its screen resumes.
        mutableState.value = mutableState.value.copy(status = status)
    }

    private fun destroy(ad: LoadedRewardedAd) {
        try {
            ad.destroy()
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Rewarded ad cleanup failed.", failure)
        }
    }

    private class ShowingAd(
        val ad: LoadedRewardedAd,
        val requestId: String,
        val adId: String,
        val tool: RewardedTool,
        var rewardGranted: Boolean = false,
        var shownRecorded: Boolean = false,
    )

    internal companion object {
        const val CACHE_LIFETIME_MILLIS = 55 * 60 * 1_000L
        const val LOAD_TIMEOUT_MILLIS = 90_000L
        private const val TAG = "ScaniqoAds"
    }
}
