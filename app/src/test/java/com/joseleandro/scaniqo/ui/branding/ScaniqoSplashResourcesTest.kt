package com.joseleandro.scaniqo.ui.branding

import android.app.Application
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import com.joseleandro.scaniqo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33], application = Application::class)
class ScaniqoSplashResourcesTest {
    @Test
    fun animationInflatesOnBothLegacyAndNativeSplashPlatforms() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(context.getDrawable(R.drawable.scaniqo_splash_animated) is AnimatedVectorDrawable)
        assertTrue(context.getDrawable(R.drawable.scaniqo_splash_vector) is VectorDrawable)
    }

    @Test
    fun startingThemeSelectsTheCorrectIconAndRestoresTheAppCompatTheme() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Scaniqo_Starting)
        val attributes = context.obtainStyledAttributes(intArrayOf(
            androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon,
            androidx.core.splashscreen.R.attr.postSplashScreenTheme,
        ))
        try {
            val drawable = attributes.getDrawable(0)
            if (Build.VERSION.SDK_INT >= 31) assertTrue(drawable is AnimatedVectorDrawable)
            else assertTrue(drawable is VectorDrawable)
            assertEquals(R.style.Theme_Scaniqo, attributes.getResourceId(1, 0))
        } finally {
            attributes.recycle()
        }
    }
}
