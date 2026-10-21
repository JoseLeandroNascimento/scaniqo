package com.joseleandro.scaniqo.ui.branding

import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.joseleandro.scaniqo.R

/**
 * Finishes the platform's existing splash; no second Activity or Compose splash is added.
 * Before Android 12 the compat exit view plays the same AVD as the native starting window.
 */
internal class ScaniqoSplashController(
    private val activity: ComponentActivity,
    private val splash: SplashScreen,
    private val animateLaunch: Boolean,
    private val animationScale: Float,
    private val onContentReady: () -> Unit,
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var provider: SplashScreenViewProvider? = null
    private var legacyAnimation: AnimatedVectorDrawable? = null
    private var dismissed = false
    private var contentReady = false
    private val finishAnimation = Runnable { finish(animateExit = true) }

    fun install() {
        activity.lifecycle.addObserver(this)
        splash.setOnExitAnimationListener { view ->
            if (dismissed) {
                removePreservingSystemBars(view)
                return@setOnExitAnimationListener
            }
            provider = view
            if (!animateLaunch || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                finish(animateExit = false)
                return@setOnExitAnimationListener
            }

            val duration = activity.resources.getInteger(R.integer.scaniqo_splash_duration).toLong()
            val remaining = if (Build.VERSION.SDK_INT < 31) {
                val image = view.iconView as? ImageView
                val drawable = activity.getDrawable(R.drawable.scaniqo_splash_animated) as? AnimatedVectorDrawable
                if (image == null || drawable == null) {
                    finish(animateExit = false)
                    return@setOnExitAnimationListener
                }
                legacyAnimation = drawable
                image.setImageDrawable(drawable)
                drawable.start()
                remainingSplashAnimationMillis(0L, 0L, duration, animationScale)
            } else {
                // Continue the animation already started by Android; never restart it on handoff.
                remainingSplashAnimationMillis(
                    startMillis = view.iconAnimationStartMillis,
                    nowMillis = System.currentTimeMillis(),
                    durationMillis = view.iconAnimationDurationMillis.takeIf { it > 0L } ?: duration,
                    animationScale = animationScale,
                )
            }
            if (remaining == 0L) finish(animateExit = true)
            else handler.postDelayed(finishAnimation, remaining)
        }
    }

    private fun finish(animateExit: Boolean) {
        if (dismissed) return
        handler.removeCallbacks(finishAnimation)
        if (!contentReady) {
            contentReady = true
            onContentReady()
        }
        val view = provider?.view
        if (animateExit && animationScale > 0f && view != null &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            view.animate().alpha(0f).setDuration(120L).withEndAction { dismiss() }.start()
        } else {
            dismiss()
        }
    }

    private fun dismiss() {
        if (dismissed) return
        dismissed = true
        handler.removeCallbacks(finishAnimation)
        legacyAnimation?.stop()
        legacyAnimation = null
        provider?.view?.animate()?.cancel()
        provider?.let(::removePreservingSystemBars)
        provider = null
        activity.lifecycle.removeObserver(this)
    }

    private fun removePreservingSystemBars(view: SplashScreenViewProvider) {
        val bars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        val lightStatusBars = bars.isAppearanceLightStatusBars
        val lightNavigationBars = bars.isAppearanceLightNavigationBars
        view.remove()
        // Core splashscreen reapplies the XML theme on API 31–32. Keep the in-app theme's flags.
        bars.isAppearanceLightStatusBars = lightStatusBars
        bars.isAppearanceLightNavigationBars = lightNavigationBars
    }

    // Backgrounding, rotation, and language recreation must not leave callbacks or restart a chase.
    override fun onStop(owner: LifecycleOwner) = finish(animateExit = false)
    override fun onDestroy(owner: LifecycleOwner) = dismiss()
}

internal fun systemAnimationScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        .let { if (it.isFinite()) it.coerceAtLeast(0f) else 1f }
