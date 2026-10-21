package com.joseleandro.scaniqo.ui.documentEditor.components

import android.content.Context
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import kotlinx.serialization.json.Json

/** Preferências visuais por ferramenta; nenhum conteúdo de documento é persistido aqui. */
internal class FillMarkStylePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("fill_mark_styles", Context.MODE_PRIVATE)

    fun newOverlay(kind: PdfOverlayKind): PdfOverlay {
        val overlay = com.joseleandro.scaniqo.ui.documentEditor.components.newOverlay(kind)
        if (kind !in repeatableFillMarkKinds) return overlay
        val style = runCatching {
            preferences.getString(kind.name, null)?.let { Json.decodeFromString<FillMarkStyle>(it) }
        }.getOrNull()
        return style?.applyTo(overlay) ?: overlay
    }

    fun remember(overlay: PdfOverlay) {
        if (overlay.kind !in repeatableFillMarkKinds) return
        val style = FillMarkStyle.from(overlay)
        if (style.isValid()) preferences.edit().putString(overlay.kind.name, Json.encodeToString(style)).apply()
    }
}
