package com.joseleandro.scaniqo.core.ads

import android.content.Context
import com.joseleandro.scaniqo.R

internal data class AdMobConfiguration(
    val appId: String,
    val bannerUnitId: String? = null,
    val editorBannerUnitId: String? = null,
    val documentsNativeUnitId: String? = null,
    val toolsRewardedUnitId: String? = null,
) {
    val enabled: Boolean
        get() = AdPlacement.entries.any { unitId(it) != null }

    fun unitId(placement: AdPlacement): String? {
        if (!appIdPattern.matches(appId)) return null
        val value = when (placement) {
            AdPlacement.TOOLS_BANNER -> bannerUnitId
            AdPlacement.EDITOR_BANNER -> editorBannerUnitId
            AdPlacement.DOCUMENTS_NATIVE -> documentsNativeUnitId
            AdPlacement.TOOLS_REWARDED -> toolsRewardedUnitId
        }
        return value?.takeIf(adUnitIdPattern::matches)
    }

    companion object {
        private val appIdPattern = Regex("ca-app-pub-[0-9]{16}~[0-9]{10}")
        private val adUnitIdPattern = Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")

        fun from(context: Context) = AdMobConfiguration(
            appId = context.getString(R.string.admob_app_id).trim(),
            bannerUnitId = context.adUnit(R.string.admob_banner_unit_id),
            editorBannerUnitId = context.adUnit(R.string.admob_editor_banner_unit_id),
            documentsNativeUnitId = context.adUnit(R.string.admob_documents_native_unit_id),
            toolsRewardedUnitId = context.adUnit(R.string.admob_tools_rewarded_unit_id),
        )

        private fun Context.adUnit(resourceId: Int): String? =
            getString(resourceId).trim().takeIf(String::isNotEmpty)
    }
}
