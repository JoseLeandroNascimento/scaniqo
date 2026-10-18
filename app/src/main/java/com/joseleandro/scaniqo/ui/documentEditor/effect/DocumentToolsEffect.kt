package com.joseleandro.scaniqo.ui.documentEditor.effect

sealed interface DocumentToolsEffect {
    data object LaunchScanner : DocumentToolsEffect
    data object LaunchPdfMergePicker : DocumentToolsEffect
    data object NeedUnlock : DocumentToolsEffect
    data object NeedDocumentPassword : DocumentToolsEffect
    data object NavigateSignatures : DocumentToolsEffect
    data object NavigatePasswordSettings : DocumentToolsEffect
    data object NavigateBack : DocumentToolsEffect
}
