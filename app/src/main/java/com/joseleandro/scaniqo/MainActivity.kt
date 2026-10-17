package com.joseleandro.scaniqo

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.joseleandro.scaniqo.core.navigation.AppNavigation
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScaniqoTheme {
                AppNavigation()
            }
        }
    }
}
