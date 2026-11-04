package com.joseleandro.scaniqo.domain.model

/** Paleta compartilhada pelo cadastro e pela edição; a cor é salva em ARGB. */
enum class TagColor(
    val argb: Int,
) {
    BLUE(0xFF4F7CAC.toInt()),
    SKY(0xFF0284C7.toInt()),
    CYAN(0xFF0891B2.toInt()),
    TEAL(0xFF0F766E.toInt()),
    EMERALD(0xFF059669.toInt()),
    GREEN(0xFF4D7C0F.toInt()),
    LIME(0xFF84A61D.toInt()),
    OLIVE(0xFF737A34.toInt()),
    YELLOW(0xFFD4A017.toInt()),
    AMBER(0xFFD97706.toInt()),
    ORANGE(0xFFEA580C.toInt()),
    CORAL(0xFFE76F51.toInt()),
    RED(0xFFDC2626.toInt()),
    ROSE(0xFFE11D48.toInt()),
    PINK(0xFFDB2777.toInt()),
    MAGENTA(0xFFC026D3.toInt()),
    PURPLE(0xFF9333EA.toInt()),
    VIOLET(0xFF7C3AED.toInt()),
    INDIGO(0xFF4F46E5.toInt()),
    NAVY(0xFF334D75.toInt()),
    BROWN(0xFF8B5E3C.toInt()),
    TERRACOTTA(0xFFA45A48.toInt()),
    SLATE(0xFF64748B.toInt()),
    GRAPHITE(0xFF52525B.toInt());
}
