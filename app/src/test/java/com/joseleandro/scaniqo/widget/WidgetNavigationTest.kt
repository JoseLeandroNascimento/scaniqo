package com.joseleandro.scaniqo.widget

import androidx.lifecycle.SavedStateHandle
import com.joseleandro.scaniqo.core.navigation.AppRoute
import com.joseleandro.scaniqo.core.navigation.NavigationViewModel
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentMerge.state.DocumentQuickMergeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetNavigationTest {
    @Test
    fun scannerAndImportLaunchOnceAfterTheirLibraryDestinationIsReady() {
        listOf(WidgetAction.SCAN, WidgetAction.IMPORT).forEach { action ->
            val events = mutableListOf<Any>()
            val requests = WidgetLaunchViewModel(SavedStateHandle())
            requests.enqueue(WidgetDestination(action))
            val pending = checkNotNull(requests.pending.value)

            dispatchWidgetDestination(
                checkNotNull(requests.consume(pending)),
                navigate = { events += it },
                scan = { events += "scan" },
                importPdf = { events += "import" },
            )

            assertEquals(listOf(AppRoute.Documents, if (action == WidgetAction.SCAN) "scan" else "import"), events)
            assertNull(requests.consume(pending))
        }
    }

    @Test
    fun navigationActionsReuseExistingDestinationsWithoutLaunchingExternalPickers() {
        val expected = mapOf(
            WidgetAction.SEARCH to AppRoute.DocumentSearch,
            WidgetAction.MERGE to AppRoute.DocumentMerge(),
            WidgetAction.LIBRARY to AppRoute.Documents,
            WidgetAction.OPEN_DOCUMENT to AppRoute.DocumentReader(57),
        )
        expected.forEach { (action, route) ->
            val actual = mutableListOf<AppRoute>()
            dispatchWidgetDestination(
                WidgetDestination(action, 57L.takeIf { action == WidgetAction.OPEN_DOCUMENT }),
                navigate = actual::add,
                scan = { error("Unexpected scanner") },
                importPdf = { error("Unexpected picker") },
            )
            assertEquals(listOf(route), actual)
        }
    }

    @Test
    fun widgetNavigationPreservesTheScreenAndBackstackAlreadyOpen() {
        val navigation = NavigationViewModel(SavedStateHandle())
        navigation.navigateTo(AppRoute.Settings)
        navigation.navigateTo(AppRoute.DriveBackup)
        val original = navigation.backStack

        dispatchWidgetDestination(
            WidgetDestination(WidgetAction.SEARCH),
            navigate = navigation::navigateTo,
            scan = {},
            importPdf = {},
        )

        assertEquals(original + AppRoute.DocumentSearch, navigation.backStack)
        navigation.goBack()
        assertEquals(original, navigation.backStack)
    }

    @Test
    fun importScannerAndMergeOperationsDeferTheRequest() {
        assertTrue(ready())
        assertFalse(ready(importActive = true))
        assertFalse(ready(signaturePickerVisible = true))
        listOf(
            DocumentActionsUiState(isScanning = true),
            DocumentActionsUiState(isPreparingScan = true),
            DocumentActionsUiState(isDeleting = true),
            DocumentActionsUiState(busyDocumentId = 7),
        ).forEach { assertFalse(ready(actions = it)) }
        listOf(
            DocumentQuickMergeUiState(isActive = true),
            DocumentQuickMergeUiState(isWorking = true),
        ).forEach { assertFalse(ready(merge = it)) }
    }

    @Test
    fun editorWorkAndUnresolvedDraftsMustFinishBeforeNavigation() {
        listOf(
            DocumentToolsUiState(isLoading = true),
            DocumentToolsUiState(isSaving = true),
            DocumentToolsUiState(isScanning = true),
            DocumentToolsUiState(hasChanges = true),
            DocumentToolsUiState(recoveryRequired = true),
            DocumentToolsUiState(confirmDiscard = true),
            DocumentToolsUiState(signatureAwaitingPlacement = true),
            DocumentToolsUiState(pendingDeletePageId = "page"),
            DocumentToolsUiState(isUpdatingFormValues = true),
        ).forEach { assertFalse(ready(tools = it)) }
        assertTrue(ready(tools = DocumentToolsUiState()))
    }

    private fun ready(
        importActive: Boolean = false,
        signaturePickerVisible: Boolean = false,
        actions: DocumentActionsUiState = DocumentActionsUiState(),
        tools: DocumentToolsUiState = DocumentToolsUiState(),
        merge: DocumentQuickMergeUiState = DocumentQuickMergeUiState(),
    ) = canOpenWidgetDestination(importActive, signaturePickerVisible, actions, tools, merge)
}
