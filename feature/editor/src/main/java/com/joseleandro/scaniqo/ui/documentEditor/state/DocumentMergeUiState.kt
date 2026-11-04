package com.joseleandro.scaniqo.ui.documentEditor.state

/** Etapas mutuamente exclusivas da mesclagem; senhas nunca são publicadas na interface. */
sealed interface DocumentMergeUiState {
    data object Idle : DocumentMergeUiState
    data object Picking : DocumentMergeUiState
    data object AwaitingUnlock : DocumentMergeUiState
    data object Merging : DocumentMergeUiState
    data class PasswordRequired(
        val uri: String,
        val name: String?,
        val incorrect: Boolean = false,
    ) : DocumentMergeUiState
}
