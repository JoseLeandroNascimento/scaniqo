package com.joseleandro.scaniqo.ui.theme

/**
 * Colors for feedback drawn directly on opaque document paper.
 * Paper and signature ink belong to the document, so they do not invert with the app theme.
 * Surrounding controls continue to use MaterialTheme.colorScheme.
 */
object DocumentPaperColors {
    val background = ScaniqoPalette.Paper
    val onBackground = ScaniqoPalette.TextDark
    val supportingText = ScaniqoPalette.NeutralVariant30
    val indicator = ScaniqoPalette.Ink40
}
