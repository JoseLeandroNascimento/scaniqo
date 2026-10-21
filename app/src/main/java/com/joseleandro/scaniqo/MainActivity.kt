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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.scaniqo.domain.model.AppThemeMode
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import org.koin.compose.koinInject

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            ScaniqoTheme(darkTheme = dark, colorTheme = preferences.colorTheme) {
                AppNavigation()
            }
        }
    }
}
