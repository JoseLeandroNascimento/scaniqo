package com.joseleandro.scaniqo.ui.ads

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.ads.AdMobManager
import com.joseleandro.scaniqo.core.ads.AdPlacement
import org.koin.compose.koinInject

/** The tools catalog owns its adaptive banner; document selection never loads an ad. */
@Composable
fun ToolsBannerAd(
    modifier: Modifier = Modifier,
    manager: AdMobManager? = null,
) {
    DisplayBannerAd(modifier, AdPlacement.TOOLS_BANNER, manager)
}

/** A fixed 320 × 50 dp creative, in its own row above the viewer's tool bar. */
@Composable
fun EditorBannerAd(
    modifier: Modifier = Modifier,
    manager: AdMobManager? = null,
) {
    val configuration = LocalConfiguration.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // A landscape phone, narrow split window or keyboard leaves too little canvas space.
    if (keyboardVisible || configuration.screenHeightDp < 600) return
    DisplayBannerAd(modifier, AdPlacement.EDITOR_BANNER, manager)
}

@Composable
private fun DisplayBannerAd(
    modifier: Modifier = Modifier,
    placement: AdPlacement,
    manager: AdMobManager?,
) {
    val editor = placement == AdPlacement.EDITOR_BANNER
    // Inspection must not resolve DI, initialize an SDK or issue an ad request.
    if (LocalInspectionMode.current) {
        BannerPlaceholder(modifier, editor)
        return
    }
    val adManager = manager ?: koinInject<AdMobManager>()
    val state by adManager.state.collectAsStateWithLifecycle()
    val unitId = adManager.unitId(placement)
    val lifecycleOwner = LocalLifecycleOwner.current
    var started by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var lifecycleGeneration by remember(lifecycleOwner) { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // STOP destroys the view immediately. Even if START arrives before recomposition,
            // the generation changes so the next frame cannot reuse that destroyed view.
            if (event == Lifecycle.Event.ON_STOP) lifecycleGeneration++
            started = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!started || !state.displayAdsAllowed || unitId == null) return

    val orientation = LocalConfiguration.current.orientation
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = if (editor) 0.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // These constraints already exclude horizontal safe insets and our padding.
        val widthDp = maxWidth.value.toInt()
        if (widthDp < 320) return@BoxWithConstraints
        key(placement, widthDp, orientation, unitId, state.requestGeneration, lifecycleGeneration) {
            ActiveBanner(unitId = unitId, widthDp = widthDp, editor = editor)
        }
    }
}

@Composable
private fun ActiveBanner(
    modifier: Modifier = Modifier,
    unitId: String,
    widthDp: Int,
    editor: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adSize = remember(context, widthDp, editor) {
        if (editor) AdSize.BANNER else AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
    val adView = remember(context, adSize) { AdView(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var loaded by remember(adView) { mutableStateOf(false) }

    DisposableEffect(adView, lifecycleOwner) {
        // All reads and writes of released occur on the main thread, including SDK callbacks.
        var released = false
        fun release() {
            if (!released) {
                released = true
                (adView.parent as? ViewGroup)?.removeView(adView)
                adView.destroy()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                loaded = false
                release()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        adView.loadAd(
            BannerAdRequest.Builder(unitId, adSize).build(),
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    mainHandler.post { if (!released) loaded = true }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mainHandler.post {
                        if (!released) {
                            loaded = false
                            release()
                        }
                    }
                }
            },
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            release()
        }
    }

    // No loading placeholder or empty space when offline, denied or without inventory.
    if (loaded) {
        BannerFrame(modifier = modifier, editor = editor) {
            AndroidView(
                factory = { adView },
                modifier = Modifier.size(adSize.width.dp, adSize.height.dp),
            )
        }
    }
}

@Composable
private fun BannerFrame(
    modifier: Modifier = Modifier,
    editor: Boolean,
    content: @Composable () -> Unit,
) {
    // The editor toolbar already owns bottom insets; applying them here would duplicate space.
    Column(
        modifier = modifier.fillMaxWidth()
            .then(if (editor) Modifier else Modifier.navigationBarsPadding())
            .padding(top = 8.dp, bottom = if (editor) 12.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (editor) 4.dp else 6.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.ads_label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        content()
    }
}

@Composable
private fun BannerPlaceholder(
    modifier: Modifier = Modifier,
    editor: Boolean,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = if (editor) 0.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (maxWidth < 320.dp) return@BoxWithConstraints
        BannerFrame(editor = editor) {
            Surface(
                modifier = if (editor) Modifier.size(320.dp, 50.dp)
                else Modifier.fillMaxWidth().height(70.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.ads_preview_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
