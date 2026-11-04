package com.joseleandro.scaniqo.ui.documentEditor.effect

import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool

sealed interface DocumentToolsEffect {
    data object LaunchScanner : DocumentToolsEffect
    data object LaunchPdfMergePicker : DocumentToolsEffect
    data object NeedUnlock : DocumentToolsEffect
    data object NeedDocumentPassword : DocumentToolsEffect
    data object NavigateSignatures : DocumentToolsEffect
    data class NavigateSignatureEditor(val documentId: Long) : DocumentToolsEffect
    data class NavigateMergeEditor(val documentId: Long) : DocumentToolsEffect
    data class MergeCompleted(val documentId: Long) : DocumentToolsEffect
    data class NavigateEditorTool(val documentId: Long, val tool: DocumentTool) : DocumentToolsEffect
    data class EditorToolApplied(val documentId: Long, val tool: DocumentTool) : DocumentToolsEffect
    data object NavigatePasswordSettings : DocumentToolsEffect
    data object NavigateBack : DocumentToolsEffect
}
