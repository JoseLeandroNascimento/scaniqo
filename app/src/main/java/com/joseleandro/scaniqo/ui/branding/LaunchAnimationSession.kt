package com.joseleandro.scaniqo.ui.branding

/** Consumed at entry, including restored/disabled launches, so interruptions never replay the intro. */
internal class LaunchAnimationSession {
    private var consumed = false

    fun shouldAnimate(restored: Boolean, animationScale: Float): Boolean {
        val firstEntry = !consumed
        consumed = true
        return firstEntry && !restored && animationScale.isFinite() && animationScale > 0f
    }

    companion object {
        val process = LaunchAnimationSession()
    }
}

/** Native icon start time uses epoch milliseconds. A clock change must never extend the splash. */
internal fun remainingSplashAnimationMillis(
    startMillis: Long,
    nowMillis: Long,
    durationMillis: Long,
    animationScale: Float,
): Long {
    if (!animationScale.isFinite() || animationScale <= 0f || durationMillis <= 0L) return 0L
    // Allow the full five-second search, but bound delays from very slow system animator scales.
    val duration = (durationMillis.toDouble() * animationScale).coerceIn(0.0, 7_500.0).toLong()
    if (startMillis <= 0L) return duration
    if (startMillis > nowMillis) return 0L
    return (duration - (nowMillis - startMillis)).coerceIn(0L, duration)
}
