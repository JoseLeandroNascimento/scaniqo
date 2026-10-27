package com.joseleandro.scaniqo.core.ads

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback

internal interface RewardedAdLoader {
    fun load(unitId: String, onLoaded: (LoadedRewardedAd) -> Unit, onFailed: () -> Unit)
}

internal interface LoadedRewardedAd {
    fun show(
        activity: Activity,
        tool: RewardedTool,
        onShown: () -> Unit,
        onEarned: () -> Unit,
        onDismissed: () -> Unit,
        onFailed: () -> Unit,
    )

    fun destroy()
}

/** SDK callbacks can arrive on background threads; the owner dispatches them to Main. */
internal class GoogleRewardedAdLoader : RewardedAdLoader {
    override fun load(unitId: String, onLoaded: (LoadedRewardedAd) -> Unit, onFailed: () -> Unit) {
        RewardedAd.load(
            AdRequest.Builder(unitId).build(),
            object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd) {
                    onLoaded(GoogleLoadedRewardedAd(ad))
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w("ScaniqoAds", "Rewarded ad failed to load (${adError.code}): ${adError.message}")
                    onFailed()
                }
            },
        )
    }
}

private class GoogleLoadedRewardedAd(private val ad: RewardedAd) : LoadedRewardedAd {
    override fun show(
        activity: Activity,
        tool: RewardedTool,
        onShown: () -> Unit,
        onEarned: () -> Unit,
        onDismissed: () -> Unit,
        onFailed: () -> Unit,
    ) {
        val placement = when (tool) {
            RewardedTool.COMPRESSION -> "compress_pdf"
            RewardedTool.EXTRACT_PAGES -> "extract_pages"
            RewardedTool.PDF_TO_IMAGES -> "pdf_to_images"
            RewardedTool.SIGNATURE -> "signature"
            RewardedTool.FILL_PAGE -> "fill_page"
            RewardedTool.IMPORT_PDF -> "import_pdf"
            RewardedTool.MERGE_PDFS -> "merge_pdfs"
        }
        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdShowedFullScreenContent() = onShown()

            override fun onAdDismissedFullScreenContent() = onDismissed()

            override fun onAdPaid(value: AdValue) {
                Log.i(
                    "ScaniqoAds",
                    "Ad revenue placement=$placement valueMicros=${value.valueMicros} " +
                        "currency=${value.currencyCode} precision=${value.precisionType}",
                )
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.w(
                    "ScaniqoAds",
                    "Rewarded ad failed to show (${fullScreenContentError.code}): ${fullScreenContentError.message}",
                )
                onFailed()
            }
        }
        ad.show(activity) { onEarned() }
    }

    override fun destroy() {
        ad.adEventCallback = null
        ad.destroy()
    }
}
