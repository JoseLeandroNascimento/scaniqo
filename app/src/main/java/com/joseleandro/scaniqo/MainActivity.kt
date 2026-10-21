package com.joseleandro.scaniqo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joseleandro.scaniqo.core.navigation.AppNavigation
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.joseleandro.scaniqo.ui.branding.LaunchAnimationSession
import com.joseleandro.scaniqo.ui.branding.ScaniqoSplashController
import com.joseleandro.scaniqo.ui.branding.systemAnimationScale
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.domain.model.AppThemeMode
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import org.koin.compose.koinInject

class MainActivity : AppCompatActivity() {
    private var launchContentReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val animationScale = systemAnimationScale(this)
        val animateLaunch = LaunchAnimationSession.process.shouldAnimate(
            restored = savedInstanceState != null,
            animationScale = animationScale,
        )
        launchContentReady = !animateLaunch
        enableEdgeToEdge()
        setContent {
            val repository: AppSettingsRepository = koinInject()
            val preferences by repository.settings.collectAsStateWithLifecycle()
            val dark = when (preferences.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = launchContentReady && !dark
                    isAppearanceLightNavigationBars = launchContentReady && !dark
                }
            }
            ScaniqoTheme(darkTheme = dark, colorTheme = preferences.colorTheme) {
                if (launchContentReady) {
                    AppNavigation()
                } else {
                    // Draw a first frame for the native splash handoff without mounting auth handlers.
                    Box(Modifier.fillMaxSize().background(colorResource(R.color.scaniqo_brand_background)))
                }
            }
        }
        ScaniqoSplashController(this, splash, animateLaunch, animationScale) {
            launchContentReady = true
        }.install()
    }
}
