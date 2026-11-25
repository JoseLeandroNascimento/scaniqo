package com.joseleandro.scaniqo.widget

import com.joseleandro.scaniqo.core.navigation.AppRoute
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentMerge.state.DocumentQuickMergeUiState

internal fun canOpenWidgetDestination(
    importActive: Boolean,
    signaturePickerVisible: Boolean,
    actions: DocumentActionsUiState,
    tools: DocumentToolsUiState,
    merge: DocumentQuickMergeUiState,
): Boolean = !importActive && !signaturePickerVisible &&
    !actions.isBusy && actions.editor == null && actions.pendingDeletion == null &&
    actions.pendingCreation == null && actions.generatedResult == null &&
    !tools.isBusy && !tools.hasChanges && !tools.recoveryRequired && !tools.confirmDiscard &&
    tools.signatureDraft == null && !tools.signatureAwaitingPlacement &&
    tools.pendingDeletePageId == null && !tools.isUpdatingFormValues &&
    !merge.isActive && !merge.isWorking && merge.pending == null

internal fun dispatchWidgetDestination(
    destination: WidgetDestination,
    navigate: (AppRoute) -> Unit,
    scan: () -> Unit,
    importPdf: () -> Unit,
) {
    // Push instead of clearing the stack, preserving the screen the user left open.
    when (destination.action) {
        WidgetAction.SCAN -> {
            navigate(AppRoute.Documents)
            scan()
        }
        WidgetAction.IMPORT -> {
            navigate(AppRoute.Documents)
            importPdf()
        }
        WidgetAction.SEARCH -> navigate(AppRoute.DocumentSearch)
        WidgetAction.MERGE -> navigate(AppRoute.DocumentMerge())
        WidgetAction.LIBRARY -> navigate(AppRoute.Documents)
        WidgetAction.OPEN_DOCUMENT -> navigate(AppRoute.DocumentReader(checkNotNull(destination.documentId)))
    }
}
