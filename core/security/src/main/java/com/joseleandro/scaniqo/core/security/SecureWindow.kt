package com.joseleandro.scaniqo.core.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import java.util.WeakHashMap

/**
 * Impede capturas enquanto este conteúdo sensível estiver na composição.
 *
 * As telas compartilham a contagem por janela: uma transição de navegação não
 * remove a proteção enquanto outra tela ou diálogo ainda precisar dela.
 * Uma proteção que já existia antes da primeira chamada é preservada.
 */
@Composable
fun SecureWindow(enabled: Boolean = true) {
    if (LocalInspectionMode.current) return
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
        ?: LocalContext.current.secureActivity()?.window
    DisposableEffect(window, enabled) {
        if (enabled && window != null) SecureWindowOwners.acquire(window)
        onDispose {
            if (enabled && window != null) SecureWindowOwners.release(window)
        }
    }
}

private object SecureWindowOwners {
    private data class Ownership(var count: Int, val wasSecure: Boolean)

    // Os valores não retêm Window, permitindo coletar uma Activity destruída.
    private val owners = WeakHashMap<Window, Ownership>()

    fun acquire(window: Window) {
        checkMainThread()
        val owner = owners[window]
        if (owner != null) {
            owner.count++
        } else {
            val wasSecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            owners[window] = Ownership(count = 1, wasSecure = wasSecure)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        checkMainThread()
        val owner = owners[window] ?: return
        owner.count--
        if (owner.count == 0) {
            owners.remove(window)
            if (!owner.wasSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "A proteção da janela deve ser alterada na thread principal."
        }
    }
}

private tailrec fun Context.secureActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.secureActivity()
    else -> null
}
