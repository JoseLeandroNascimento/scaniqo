package com.joseleandro.scaniqo.ui.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.ads.AdMobManager
import com.joseleandro.scaniqo.core.ads.AdPlacement
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
internal fun rememberDocumentsNativeAds(
    slotCount: Int,
    manager: AdMobManager? = null,
): Map<Int, NativeAd> {
    if (slotCount <= 0) return emptyMap()
    if (LocalInspectionMode.current) return emptyMap()
    val adManager = manager ?: koinInject<AdMobManager>()
    val state by adManager.state.collectAsStateWithLifecycle()
    val unitId = adManager.unitId(AdPlacement.DOCUMENTS_NATIVE) ?: return emptyMap()
    val lifecycleOwner = LocalLifecycleOwner.current
    var started by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var lifecycleGeneration by remember(lifecycleOwner) { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            started = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (event == Lifecycle.Event.ON_STOP) lifecycleGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!started || !state.displayAdsAllowed) return emptyMap()

    val count = slotCount.coerceIn(0, 2)
    val ads = remember(unitId, count, state.requestGeneration, lifecycleOwner, lifecycleGeneration) {
        mutableStateMapOf<Int, NativeAd>()
    }
    DisposableEffect(ads) {
        val mainHandler = Handler(Looper.getMainLooper())
        val expirationCallbacks = mutableListOf<Runnable>()
        var released = false
        fun release() {
            if (released) return
            released = true
            expirationCallbacks.forEach(mainHandler::removeCallbacks)
            expirationCallbacks.clear()
            ads.values.toList().forEach(NativeAd::destroy)
            ads.clear()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        repeat(count) { slot ->
            val request = NativeAdRequest.Builder(unitId, listOf(NativeAd.NativeAdType.NATIVE)).build()
            try {
                NativeAdLoader.load(
                    request,
                    object : NativeAdLoaderCallback {
                        override fun onNativeAdLoaded(nativeAd: NativeAd) {
                            mainHandler.post {
                                if (released) {
                                    nativeAd.destroy()
                                } else {
                                    ads.put(slot, nativeAd)?.destroy()
                                    val expire = Runnable {
                                        if (ads[slot] === nativeAd) {
                                            ads.remove(slot)
                                            nativeAd.destroy()
                                        }
                                    }
                                    expirationCallbacks += expire
                                    mainHandler.postDelayed(expire, 55 * 60 * 1_000L)
                                }
                            }
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d("DocumentsNativeAd", "Native ad unavailable: ${adError.message}")
                        }
                    },
                )
            } catch (error: Exception) {
                Log.w("DocumentsNativeAd", "Native ad request could not start", error)
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            release()
        }
    }
    return ads
}

@Composable
internal fun DocumentsNativeAd(
    ad: NativeAd,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = NativeAdPalette(
        background = colors.surfaceContainerLow.toArgb(),
        outline = colors.outlineVariant.toArgb(),
        foreground = colors.onSurface.toArgb(),
        secondary = colors.onSurfaceVariant.toArgb(),
        accent = colors.primary.toArgb(),
        onAccent = colors.onPrimary.toArgb(),
    )
    key(ad, palette) {
        AndroidView(
            factory = { context -> createNativeAdView(context, ad, palette) },
            modifier = modifier.fillMaxWidth(),
            onRelease = NativeAdView::destroy,
        )
    }
}

private data class NativeAdPalette(
    val background: Int,
    val outline: Int,
    val foreground: Int,
    val secondary: Int,
    val accent: Int,
    val onAccent: Int,
)

private fun createNativeAdView(context: Context, ad: NativeAd, palette: NativeAdPalette): NativeAdView {
    val root = NativeAdView(context)
    val views = nativeAdContent(
        context = context,
        palette = palette,
        headline = ad.headline.orEmpty(),
        body = ad.body,
        advertiser = ad.advertiser,
        callToAction = ad.callToAction,
    )
    root.background = nativeAdBackground(context, palette)
    root.addView(views.content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    ad.icon?.drawable?.let { icon ->
        views.icon.visibility = View.VISIBLE
        views.icon.setImageDrawable(icon)
        root.iconView = views.icon
    }
    val media = if (ad.mediaContent.hasVideoContent) {
        MediaView(context).apply {
            imageScaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(180)).apply {
                topMargin = context.dp(12)
            }
            views.content.addView(this, views.content.childCount - 1)
        }
    } else null
    root.headlineView = views.headline
    root.bodyView = views.body
    root.advertiserView = views.advertiser
    root.callToActionView = views.callToAction
    root.registerNativeAd(ad, media)
    return root
}

private data class NativeAssetViews(
    val content: LinearLayout,
    val icon: ImageView,
    val headline: TextView,
    val body: TextView,
    val advertiser: TextView,
    val callToAction: TextView,
)

private fun nativeAdContent(
    context: Context,
    palette: NativeAdPalette,
    headline: String,
    body: String?,
    advertiser: String?,
    callToAction: String?,
): NativeAssetViews {
    fun text(value: String?, size: Float, color: Int) = TextView(context).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(16))
    }
    val attribution = text(context.getString(R.string.ads_label), 12f, palette.secondary).apply {
        gravity = Gravity.CENTER_VERTICAL
        minHeight = context.dp(28)
        setPadding(0, 0, context.dp(48), 0)
    }
    content.addView(attribution, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val icon = ImageView(context).apply {
        visibility = View.GONE
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    row.addView(icon, LinearLayout.LayoutParams(context.dp(56), context.dp(56)).apply {
        marginEnd = context.dp(12)
    })
    val titleColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val title = text(headline, 16f, palette.foreground).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val advertiserView = text(advertiser, 12f, palette.secondary)
    titleColumn.addView(title)
    titleColumn.addView(advertiserView)
    row.addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = context.dp(4)
    })
    val bodyView = text(body, 14f, palette.secondary)
    content.addView(bodyView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = context.dp(8)
    })
    val actionView = text(callToAction, 14f, palette.onAccent).apply {
        gravity = Gravity.CENTER
        minHeight = context.dp(48)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
        background = GradientDrawable().apply {
            setColor(palette.accent)
            cornerRadius = context.dp(24).toFloat()
        }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        }
    }
    content.addView(actionView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = context.dp(12)
    })
    return NativeAssetViews(content, icon, title, bodyView, advertiserView, actionView)
}

private fun nativeAdBackground(context: Context, palette: NativeAdPalette) = GradientDrawable().apply {
    setColor(palette.background)
    cornerRadius = context.dp(24).toFloat()
    setStroke(context.dp(1), palette.outline)
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

@Preview(name = "Anúncio na lista", widthDp = 390, locale = "pt-rBR", showBackground = true)
@Preview(name = "Anúncio na lista escuro", widthDp = 390, locale = "pt-rBR", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DocumentsNativeAdPreview() {
    ScaniqoTheme {
        val colors = MaterialTheme.colorScheme
        val palette = NativeAdPalette(
            colors.surfaceContainerLow.toArgb(), colors.outlineVariant.toArgb(),
            colors.onSurface.toArgb(), colors.onSurfaceVariant.toArgb(),
            colors.primary.toArgb(), colors.onPrimary.toArgb(),
        )
        AndroidView(
            factory = { context ->
                nativeAdContent(
                    context, palette,
                    headline = context.getString(R.string.ads_preview_label),
                    body = "Conheça uma novidade para o seu dia a dia.",
                    advertiser = "Exemplo de anunciante",
                    callToAction = "Saiba mais",
                ).content.apply { background = nativeAdBackground(context, palette) }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
