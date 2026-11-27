package com.joseleandro.scaniqo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** A category surface and the readable foreground paired with it. */
@Immutable
data class DocumentQuickToolColorPair(
    val container: Color,
    val onContainer: Color,
)

/** Category colors for document shortcuts; these preserve the established action colors. */
@Immutable
data class DocumentQuickToolColors(
    val importPdf: DocumentQuickToolColorPair,
    val mergePdf: DocumentQuickToolColorPair,
    val signDocument: DocumentQuickToolColorPair,
    val allTools: DocumentQuickToolColorPair,
)

private val LightDocumentQuickToolColors = DocumentQuickToolColors(
    importPdf = DocumentQuickToolColorPair(
        container = Color(0xFFDCEEFF),
        onContainer = Color(0xFF173D58),
    ),
    mergePdf = DocumentQuickToolColorPair(
        container = Color(0xFFEDE4FF),
        onContainer = Color(0xFF42345E),
    ),
    signDocument = DocumentQuickToolColorPair(
        container = Color(0xFFDDF3E9),
        onContainer = Color(0xFF21483E),
    ),
    allTools = DocumentQuickToolColorPair(
        container = Color(0xFFFFEED6),
        onContainer = Color(0xFF4B4031),
    ),
)

private fun DocumentQuickToolColorPair.reversed() = DocumentQuickToolColorPair(
    container = onContainer,
    onContainer = container,
)

private val DarkDocumentQuickToolColors = with(LightDocumentQuickToolColors) {
    DocumentQuickToolColors(
        importPdf = importPdf.reversed(),
        mergePdf = mergePdf.reversed(),
        signDocument = signDocument.reversed(),
        allTools = allTools.reversed(),
    )
}

fun documentQuickToolColors(dark: Boolean): DocumentQuickToolColors =
    if (dark) DarkDocumentQuickToolColors else LightDocumentQuickToolColors
