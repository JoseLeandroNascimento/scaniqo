package com.joseleandro.scaniqo.ui.documentCommon

import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.core.ads.ToolAdAccess
import com.joseleandro.scaniqo.core.ads.ToolAdAccessPolicy
import com.joseleandro.scaniqo.core.ads.ToolAdPermit

/** Models a reserved credit so tests catch both repeated consumption and leaked reservations. */
internal class FakeDocumentToolAdPolicy : ToolAdAccessPolicy {
    val rewards = mutableSetOf<RewardedTool>()
    val acquisitions = mutableListOf<RewardedTool>()
    val finishes = mutableListOf<Pair<RewardedTool, Boolean>>()
    var reservation: ToolAdPermit? = null
        private set

    override fun access(tool: RewardedTool) =
        if (tool in rewards) ToolAdAccess.REWARDED else ToolAdAccess.AD_REQUIRED

    override fun acquire(tool: RewardedTool, operationId: String, adUnavailable: Boolean): ToolAdPermit? {
        acquisitions += tool
        if (reservation != null || (tool !in rewards && !adUnavailable)) return null
        return ToolAdPermit(tool, operationId, access(tool)).also { reservation = it }
    }

    override fun finish(permit: ToolAdPermit, success: Boolean) {
        if (reservation !== permit) return
        reservation = null
        finishes += permit.tool to success
        if (success) rewards.remove(permit.tool)
    }

    override fun grantReward(tool: RewardedTool, adId: String) { rewards += tool }
    override fun recordAdShown(adId: String) = Unit
}
