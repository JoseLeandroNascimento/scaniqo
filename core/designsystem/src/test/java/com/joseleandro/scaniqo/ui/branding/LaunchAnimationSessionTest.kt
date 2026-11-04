package com.joseleandro.scaniqo.ui.branding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchAnimationSessionTest {
    @Test
    fun onlyTheFirstFreshActivityAnimatesInAProcess() {
        val session = LaunchAnimationSession()
        assertTrue(session.shouldAnimate(restored = false, animationScale = 1f))
        assertFalse(session.shouldAnimate(restored = false, animationScale = 1f))
    }

    @Test
    fun restoredActivityConsumesTheSessionWithoutReplaying() {
        val session = LaunchAnimationSession()
        assertFalse(session.shouldAnimate(restored = true, animationScale = 1f))
        assertFalse(session.shouldAnimate(restored = false, animationScale = 1f))
    }

    @Test
    fun disablingAnimationsSkipsTheIntroAndConsumesIt() {
        val session = LaunchAnimationSession()
        assertFalse(session.shouldAnimate(restored = false, animationScale = 0f))
        assertFalse(session.shouldAnimate(restored = false, animationScale = 1f))
        assertEquals(0L, remainingSplashAnimationMillis(0, 0, 3000, 0f))
    }

    @Test
    fun nativeHandoffWaitsOnlyForTheUnplayedPart() {
        assertEquals(3000L, remainingSplashAnimationMillis(0, 0, 3000, 1f))
        assertEquals(2650L, remainingSplashAnimationMillis(10_000, 10_350, 3000, 1f))
        assertEquals(100L, remainingSplashAnimationMillis(10_000, 12_900, 3000, 1f))
        assertEquals(0L, remainingSplashAnimationMillis(10_000, 13_200, 3000, 1f))
    }

    @Test
    fun fasterSystemAnimationsShortenTheWait() {
        assertEquals(1150L, remainingSplashAnimationMillis(10_000, 10_350, 3000, .5f))
        assertEquals(1500L, remainingSplashAnimationMillis(0, 0, 3000, .5f))
    }

    @Test
    fun slowSystemAnimationScaleIsCappedAtSevenAndAHalfSeconds() {
        assertEquals(7500L, remainingSplashAnimationMillis(0, 0, 3000, 10f))
        assertEquals(1000L, remainingSplashAnimationMillis(10_000, 16_500, 3000, 10f))
        assertEquals(0L, remainingSplashAnimationMillis(10_000, 18_000, 3000, 10f))
    }

    @Test
    fun invalidTimingOrAClockChangeCannotExtendStartup() {
        assertEquals(0L, remainingSplashAnimationMillis(11_000, 10_000, 1000, 1f))
        assertEquals(0L, remainingSplashAnimationMillis(0, 0, 1000, Float.NaN))
        assertEquals(0L, remainingSplashAnimationMillis(0, 0, 0, 1f))
        assertEquals(0L, remainingSplashAnimationMillis(0, 0, 1000, -1f))
    }
}
