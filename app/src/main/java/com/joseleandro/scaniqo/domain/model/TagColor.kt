package com.joseleandro.scaniqo.domain.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.joseleandro.scaniqo.R

/** Paleta compartilhada pelo cadastro e pela edição; a cor é salva em ARGB. */
enum class TagColor(
    val color: Color,
    @get:StringRes val labelRes: Int,
) {
    BLUE(Color(0xFF4F7CAC), R.string.tag_color_blue),
    SKY(Color(0xFF0284C7), R.string.tag_color_sky),
    CYAN(Color(0xFF0891B2), R.string.tag_color_cyan),
    TEAL(Color(0xFF0F766E), R.string.tag_color_teal),
    EMERALD(Color(0xFF059669), R.string.tag_color_emerald),
    GREEN(Color(0xFF4D7C0F), R.string.tag_color_green),
    LIME(Color(0xFF84A61D), R.string.tag_color_lime),
    OLIVE(Color(0xFF737A34), R.string.tag_color_olive),
    YELLOW(Color(0xFFD4A017), R.string.tag_color_yellow),
    AMBER(Color(0xFFD97706), R.string.tag_color_amber),
    ORANGE(Color(0xFFEA580C), R.string.tag_color_orange),
    CORAL(Color(0xFFE76F51), R.string.tag_color_coral),
    RED(Color(0xFFDC2626), R.string.tag_color_red),
    ROSE(Color(0xFFE11D48), R.string.tag_color_rose),
    PINK(Color(0xFFDB2777), R.string.tag_color_pink),
    MAGENTA(Color(0xFFC026D3), R.string.tag_color_magenta),
    PURPLE(Color(0xFF9333EA), R.string.tag_color_purple),
    VIOLET(Color(0xFF7C3AED), R.string.tag_color_violet),
    INDIGO(Color(0xFF4F46E5), R.string.tag_color_indigo),
    NAVY(Color(0xFF334D75), R.string.tag_color_navy),
    BROWN(Color(0xFF8B5E3C), R.string.tag_color_brown),
    TERRACOTTA(Color(0xFFA45A48), R.string.tag_color_terracotta),
    SLATE(Color(0xFF64748B), R.string.tag_color_slate),
    GRAPHITE(Color(0xFF52525B), R.string.tag_color_graphite);
}
