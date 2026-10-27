package com.joseleandro.scaniqo.core.ads

import android.content.Context
import android.content.SharedPreferences

enum class RewardedTool {
    COMPRESSION, EXTRACT_PAGES, PDF_TO_IMAGES, SIGNATURE, FILL_PAGE, IMPORT_PDF, MERGE_PDFS,
}

enum class ToolAdAccess { REWARDED, AD_REQUIRED }

class ToolAdPermit internal constructor(
    val tool: RewardedTool,
    val operationId: String,
    internal val origin: ToolAdAccess,
    internal val rewardId: String? = null,
)

interface ToolAdAccessPolicy {
    fun access(tool: RewardedTool): ToolAdAccess
    fun acquire(tool: RewardedTool, operationId: String, adUnavailable: Boolean = false): ToolAdPermit?
    fun finish(permit: ToolAdPermit, success: Boolean)
    fun recordAdShown(adId: String)
    fun grantReward(tool: RewardedTool, adId: String)
}

/**
 * Each successful tool operation uses one earned video credit, including the first operation.
 * Failed, cancelled or interrupted work keeps its credit until a result is delivered.
 */
class ToolAdAccessStore(context: Context) : ToolAdAccessPolicy {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .also { migrateCompressionPreferences(context.applicationContext, it) }
    private val rewardIds = RewardedTool.entries.associateWith {
        preferences.readString(rewardKey(it))?.takeIf(String::isNotBlank)
    }.toMutableMap()
    private val rewardedIds = preferences.readIds(REWARDED_IDS).also { receipts ->
        // A restored pending credit is already earned even if an older receipt set was incomplete.
        if (receipts.addAll(rewardIds.values.filterNotNull())) {
            preferences.edit().putStringSet(REWARDED_IDS, receipts.toSet()).apply()
        }
    }
    private var reservation: ToolAdPermit? = null

    @Synchronized
    override fun access(tool: RewardedTool): ToolAdAccess =
        if (rewardIds[tool] != null) ToolAdAccess.REWARDED else ToolAdAccess.AD_REQUIRED

    @Synchronized
    override fun acquire(
        tool: RewardedTool,
        operationId: String,
        adUnavailable: Boolean,
    ): ToolAdPermit? {
        if (operationId.isBlank()) return null
        reservation?.let {
            return it.takeIf { active -> active.tool == tool && active.operationId == operationId }
        }
        val access = access(tool)
        if (access == ToolAdAccess.AD_REQUIRED && !adUnavailable) return null
        return ToolAdPermit(tool, operationId, access, rewardIds[tool]).also { reservation = it }
    }

    @Synchronized
    override fun finish(permit: ToolAdPermit, success: Boolean) {
        if (reservation !== permit) return
        reservation = null
        if (!success || permit.origin != ToolAdAccess.REWARDED) return
        if (rewardIds[permit.tool] == permit.rewardId) {
            rewardIds[permit.tool] = null
            preferences.edit().remove(rewardKey(permit.tool)).apply()
        }
    }

    /** An impression alone never unlocks a tool or starts a free-use interval. */
    override fun recordAdShown(adId: String) = Unit

    /** A receipt can credit only its original tool, including repeated callbacks after restart. */
    @Synchronized
    override fun grantReward(tool: RewardedTool, adId: String) {
        if (adId.isBlank() || !rewardedIds.add(adId)) return
        val editor = preferences.edit().putStringSet(REWARDED_IDS, rewardedIds.toSet())
        if (rewardIds[tool] == null) {
            rewardIds[tool] = adId
            editor.putString(rewardKey(tool), adId)
        }
        editor.apply()
    }

    private fun migrateCompressionPreferences(context: Context, target: SharedPreferences) {
        if (target.readBoolean(LEGACY_MIGRATED)) return
        val legacy = context.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val editor = target.edit()
        val rewardKey = rewardKey(RewardedTool.COMPRESSION)
        val legacyReward = legacy.readString("reward_id")?.takeIf(String::isNotBlank)
        if (!target.contains(rewardKey) && legacyReward != null) {
            editor.putString(rewardKey, legacyReward)
        }
        val receipts = target.readIds(REWARDED_IDS).apply {
            addAll(legacy.readIds(REWARDED_IDS))
            legacyReward?.let(::add)
        }
        editor.putStringSet(REWARDED_IDS, receipts.toSet())
        // Trial flags, day counts and timestamps from previous versions intentionally have no effect.
        // One editor batch prevents the marker from preceding the credit it describes.
        editor.putBoolean(LEGACY_MIGRATED, true).apply()
    }

    companion object {
        internal const val PREFERENCES_NAME = "tool_ad_access"
        internal const val LEGACY_PREFERENCES_NAME = "strong_compression_access"
        private const val LEGACY_MIGRATED = "legacy_compression_migrated"
        private const val REWARDED_IDS = "rewarded_ids"
        private fun rewardKey(tool: RewardedTool) = "reward_id_${tool.name}"
    }
}

private fun SharedPreferences.readBoolean(key: String): Boolean =
    runCatching { getBoolean(key, false) }.getOrDefault(false)

private fun SharedPreferences.readString(key: String): String? =
    runCatching { getString(key, null) }.getOrNull()

private fun SharedPreferences.readIds(key: String): MutableSet<String> =
    runCatching { getStringSet(key, emptySet())?.toMutableSet() }.getOrNull() ?: mutableSetOf()
