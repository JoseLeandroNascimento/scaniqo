package com.joseleandro.scaniqo.ui.documents.effect

sealed interface PdfImportEffect {
    data object PickFile : PdfImportEffect
    data object Cancelled : PdfImportEffect
}
