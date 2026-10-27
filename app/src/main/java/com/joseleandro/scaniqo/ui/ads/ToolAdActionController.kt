package com.joseleandro.scaniqo.ui.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.core.ads.AdMobManager
import com.joseleandro.scaniqo.core.ads.AdMobState
import com.joseleandro.scaniqo.core.ads.AdPlacement
import com.joseleandro.scaniqo.core.ads.RewardedAdManager
import com.joseleandro.scaniqo.core.ads.RewardedAdStatus
import com.joseleandro.scaniqo.core.ads.RewardedAdUiState
import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.core.ads.ToolAdAccess
import com.joseleandro.scaniqo.core.ads.ToolAdAccessPolicy
import com.joseleandro.scaniqo.ui.documentCommon.platform.findActivity
import java.util.UUID
import java.util.WeakHashMap
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

data class ToolAdAction(
    val showsVideo: Boolean,
    val inProgress: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun rememberToolAdAction(
    tool: RewardedTool?,
    contextKey: Any,
    active: Boolean,
    onApply: (adUnavailable: Boolean) -> Unit,
): ToolAdAction {
    val ads = koinInject<AdMobManager>()
    val rewarded = koinInject<RewardedAdManager>()
    val policy = koinInject<ToolAdAccessPolicy>()
    val environment = remember(ads, rewarded, policy) { SdkToolAdEnvironment(ads, rewarded, policy) }
    return rememberToolAdAction(
        tool, contextKey, active, onApply, environment,
        LocalLifecycleOwner.current.lifecycle, ProcessLifecycleOwner.get().lifecycle,
    )
}

@Composable
internal fun rememberToolAdAction(
    tool: RewardedTool?,
    contextKey: Any,
    active: Boolean,
    onApply: (adUnavailable: Boolean) -> Unit,
    environment: ToolAdActionEnvironment,
    lifecycle: Lifecycle,
    processLifecycle: Lifecycle,
): ToolAdAction {
    val ads by environment.adsState.collectAsStateWithLifecycle()
    val video by environment.videoState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val apply by rememberUpdatedState(onApply)
    val activeNow by rememberUpdatedState(active)
    val gate = remember(tool, contextKey) { ToolAdActionGate() }
    var resumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var accessRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle, processLifecycle, environment, gate) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_RESUME) accessRevision++
            if (event == Lifecycle.Event.ON_STOP) environment.releasePendingAd()
        }
        val processObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) gate.invalidatePending()
        }
        lifecycle.addObserver(observer)
        processLifecycle.addObserver(processObserver)
        onDispose {
            gate.disposed = true
            gate.invalidatePending()
            lifecycle.removeObserver(observer)
            processLifecycle.removeObserver(processObserver)
            environment.releasePendingAd()
        }
    }
    val access = remember(environment, tool, gate, video.completion, active, accessRevision) {
        tool?.let(environment::access)
    }
    val requiresAd = access == ToolAdAccess.AD_REQUIRED
    LaunchedEffect(active, gate) {
        if (!active) gate.invalidatePending()
    }
    LaunchedEffect(environment, tool, gate, active, resumed, requiresAd, accessRevision, ads.requestGeneration, ads.rewardedAdsAllowed) {
        if (active && resumed && requiresAd && ads.rewardedAdsAllowed) environment.prepare()
        else environment.releasePendingAd()
    }
    LaunchedEffect(gate.clickLocked, gate.pendingRequest) {
        if (gate.clickLocked && gate.pendingRequest == null) {
            withFrameNanos { }
            gate.clickLocked = false
        }
    }
    LaunchedEffect(video.completion, resumed, active, gate) {
        val completion = video.completion ?: return@LaunchedEffect
        if (!activeNow || gate.disposed || !resumed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            completion.requestId != gate.pendingRequest || completion.tool != tool
        ) return@LaunchedEffect
        gate.pendingRequest = null
        environment.acknowledgeCompletion(completion.requestId)
        accessRevision++
        when {
            completion.earned -> apply(false)
            completion.failed -> apply(true)
        }
    }
    val showsVideo = active && resumed && requiresAd && ads.rewardedAdsAllowed &&
        environment.hasAdUnit() && video.status == RewardedAdStatus.READY &&
        gate.pendingRequest == null && !gate.clickLocked
    return ToolAdAction(
        showsVideo = showsVideo,
        inProgress = gate.clickLocked || gate.pendingRequest != null || video.status == RewardedAdStatus.SHOWING,
        onClick = click@{
            if (!activeNow || gate.disposed || gate.clickLocked || gate.pendingRequest != null ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                environment.videoState.value.status == RewardedAdStatus.SHOWING
            ) return@click
            gate.clickLocked = true
            val currentAccess = tool?.let(environment::access)
            if (tool == null || currentAccess != ToolAdAccess.AD_REQUIRED) {
                apply(false)
            } else if (!showsVideo || !environment.adsState.value.rewardedAdsAllowed ||
                !environment.hasAdUnit() || environment.videoState.value.status != RewardedAdStatus.READY
            ) {
                apply(true)
            } else {
                val activity = context.findActivity()
                val requestId = UUID.randomUUID().toString()
                gate.pendingRequest = requestId
                if (activity == null || !environment.show(activity, requestId, tool)) {
                    gate.pendingRequest = null
                    apply(true)
                }
            }
            accessRevision++
        },
    )
}

private class ToolAdActionGate {
    var pendingRequest by mutableStateOf<String?>(null)
    var clickLocked by mutableStateOf(false)
    var disposed = false

    fun invalidatePending() {
        pendingRequest = null
        clickLocked = false
    }
}

internal interface ToolAdActionEnvironment {
    val adsState: StateFlow<AdMobState>
    val videoState: StateFlow<RewardedAdUiState>
    fun access(tool: RewardedTool): ToolAdAccess
    fun hasAdUnit(): Boolean
    fun prepare()
    fun releasePendingAd()
    fun show(activity: Activity, requestId: String, tool: RewardedTool): Boolean
    fun acknowledgeCompletion(requestId: String)
}

private class SdkToolAdEnvironment(
    private val ads: AdMobManager,
    private val rewarded: RewardedAdManager,
    private val policy: ToolAdAccessPolicy,
) : ToolAdActionEnvironment {
    private val preloadOwner = Any()
    override val adsState get() = ads.state
    override val videoState get() = rewarded.state
    override fun access(tool: RewardedTool) = policy.access(tool)
    override fun hasAdUnit() = ads.unitId(AdPlacement.TOOLS_REWARDED) != null
    override fun prepare() {
        preloadOwners.claim(rewarded, preloadOwner)
        rewarded.prepare()
    }
    override fun releasePendingAd() {
        preloadOwners.release(rewarded, preloadOwner, rewarded::releasePendingAd)
    }
    override fun show(activity: Activity, requestId: String, tool: RewardedTool) =
        rewarded.show(activity, requestId, tool)
    override fun acknowledgeCompletion(requestId: String) = rewarded.acknowledgeCompletion(requestId)

    private companion object {
        val preloadOwners = ToolAdPreloadOwners()
    }
}

internal class ToolAdPreloadOwners {
    private val owners = WeakHashMap<Any, Any>()

    fun claim(resource: Any, owner: Any) {
        owners[resource] = owner
    }

    fun release(resource: Any, owner: Any, onRelease: () -> Unit) {
        if (owners[resource] === owner) {
            owners.remove(resource)
            onRelease()
        }
    }
}
