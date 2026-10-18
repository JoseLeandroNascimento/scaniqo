package com.joseleandro.scaniqo.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.ui.documentEditor.DocumentToolsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerRoute
import com.joseleandro.scaniqo.ui.documentEditor.DocumentEditorToolRoute
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.platform.DocumentToolsHandler
import com.joseleandro.scaniqo.ui.documentMerge.DocumentQuickMergeViewModel
import com.joseleandro.scaniqo.ui.documentMerge.DocumentMergeDestination
import com.joseleandro.scaniqo.ui.documentSignature.DocumentSignatureRoute
import com.joseleandro.scaniqo.ui.documents.platform.DocumentQuickActionsHost
import com.joseleandro.scaniqo.ui.documents.DocumentsRoute
import com.joseleandro.scaniqo.ui.documents.DocumentsViewModel
import com.joseleandro.scaniqo.ui.documents.PdfImportViewModel
import com.joseleandro.scaniqo.ui.documentCommon.platform.DocumentActionsHandler
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerViewModel
import com.joseleandro.scaniqo.ui.documents.event.DocumentsEvent
import com.joseleandro.scaniqo.ui.documents.platform.PdfImportHandler
import com.joseleandro.scaniqo.ui.backup.DriveBackupRoute
import com.joseleandro.scaniqo.ui.security.PasswordSettingsScreen
import com.joseleandro.scaniqo.ui.signatures.SignaturesScreen
import com.joseleandro.scaniqo.ui.tags.TagsRoute
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppNavigation(
    viewModel: NavigationViewModel = koinViewModel(),
    documentsViewModel: DocumentsViewModel = koinViewModel(),
    actionsViewModel: DocumentActionsViewModel = koinViewModel(),
    viewerViewModel: DocumentViewerViewModel = koinViewModel(),
    toolsViewModel: DocumentToolsViewModel = koinViewModel(),
    importViewModel: PdfImportViewModel = koinViewModel(),
    mergeViewModel: DocumentQuickMergeViewModel = koinViewModel(),
    vault: PasswordVault = koinInject(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentRoute = viewModel.backStack.lastOrNull()
    var showSignaturePicker by rememberSaveable { mutableStateOf(false) }
    val documentsState by documentsViewModel.uiState.collectAsStateWithLifecycle()
    val actionsState by actionsViewModel.uiState.collectAsStateWithLifecycle()
    val canStartDocumentAction = !actionsState.isBusy &&
        actionsState.editor == null && actionsState.pendingDeletion == null
    val navigate: (AppRoute) -> Unit = { route ->
        scope.launch { drawerState.close(); viewModel.navigateTo(route) }
    }
    val importPdf: () -> Unit = {
        if (canStartDocumentAction) scope.launch { drawerState.close(); importViewModel.chooseFile() }
    }

    DocumentActionsHandler(
        viewModel = actionsViewModel,
        onDocumentCreated = { creation ->
            documentsViewModel.onEvent(DocumentsEvent.DocumentCreated(creation.documentId, creation.tagId))
        },
    )
    DocumentToolsHandler(
        viewModel = toolsViewModel,
        onNavigateBack = viewModel::goBack,
        onNavigateSignatures = { viewModel.navigateTo(AppRoute.Signatures) },
        onNavigateSignatureEditor = { viewModel.navigateTo(AppRoute.DocumentSignature(it)) },
        onNavigateMergeEditor = { viewModel.navigateTo(AppRoute.DocumentMerge(it)) },
        onNavigateEditorTool = { documentId, tool ->
            viewModel.navigateTo(AppRoute.DocumentEditorTool(documentId, tool))
        },
        onEditorToolApplied = { documentId, tool ->
            if (viewModel.backStack.lastOrNull() == AppRoute.DocumentEditorTool(documentId, tool)) {
                viewModel.goBack()
            }
        },
        onMergeCompleted = { documentId ->
            if (viewModel.backStack.lastOrNull() == AppRoute.DocumentMerge(documentId)) viewModel.goBack()
        },
        onNavigatePasswordSettings = { viewModel.navigateTo(AppRoute.PasswordSettings) },
    )
    PdfImportHandler(
        viewModel = importViewModel,
        actionsViewModel = actionsViewModel,
        defaultTagId = documentsState.selectedTagId,
        vault = vault,
        showDialogs = currentRoute == AppRoute.Documents,
        onPasswordSettings = { viewModel.navigateTo(AppRoute.PasswordSettings) },
    )
    DocumentQuickActionsHost(
        showSignaturePicker = showSignaturePicker && currentRoute == AppRoute.Documents,
        onMergeCompleted = {
            if (viewModel.backStack.lastOrNull() == AppRoute.DocumentMerge()) viewModel.goBack()
        },
        onDismissSignaturePicker = { showSignaturePicker = false },
        documents = documentsState.documents,
        isLoading = documentsState.isLoading,
        loadError = documentsState.loadError,
        onRetry = { documentsViewModel.onEvent(DocumentsEvent.RetryLoad) },
        onImport = importPdf,
        onSign = { documentId ->
            viewModel.navigateTo(AppRoute.DocumentViewer(documentId))
            viewModel.navigateTo(AppRoute.DocumentSignature(documentId))
        },
        actionsViewModel = actionsViewModel,
        mergeViewModel = mergeViewModel,
        vault = vault,
    )
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == AppRoute.Documents && canStartDocumentAction,
        drawerContent = {
            AppNavigationDrawer(
                currentRoute = currentRoute,
                onClose = { scope.launch { drawerState.close() } },
                onImportPdf = importPdf,
                onNavigate = navigate,
            )
        },
    ) {
        NavDisplay(
            backStack = viewModel.backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = {
                if (currentRoute is AppRoute.DocumentViewer) toolsViewModel.onEvent(DocumentToolsEvent.RequestBack)
                else viewModel.goBack()
            },
            entryProvider = entryProvider {
                entry<AppRoute.Documents> {
                    DocumentsRoute(
                        viewModel = documentsViewModel,
                        actionsViewModel = actionsViewModel,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onImportPdf = importPdf,
                        onMergePdfs = { if (canStartDocumentAction) viewModel.navigateTo(AppRoute.DocumentMerge()) },
                        onSignDocument = { if (canStartDocumentAction) showSignaturePicker = true },
                        onOpenDocument = { viewModel.navigateTo(AppRoute.DocumentViewer(it)) },
                    )
                }
                entry<AppRoute.DocumentViewer> { route ->
                    DocumentViewerRoute(
                        documentId = route.documentId,
                        viewModel = viewerViewModel,
                        actionsViewModel = actionsViewModel,
                        toolsViewModel = toolsViewModel,
                        onNavigationBack = viewModel::goBack,
                    )
                }
                entry<AppRoute.DocumentSignature> { route ->
                    DocumentSignatureRoute(
                        documentId = route.documentId,
                        viewerViewModel = viewerViewModel,
                        toolsViewModel = toolsViewModel,
                        isActive = currentRoute == route,
                        onBack = viewModel::goBack,
                    )
                }
                entry<AppRoute.DocumentMerge> { route ->
                    DocumentMergeDestination(
                        documentId = route.documentId,
                        documentsState = documentsState,
                        canStart = canStartDocumentAction,
                        viewerViewModel = viewerViewModel,
                        toolsViewModel = toolsViewModel,
                        actionsViewModel = actionsViewModel,
                        mergeViewModel = mergeViewModel,
                        isActive = currentRoute == route,
                        onRetryDocuments = { documentsViewModel.onEvent(DocumentsEvent.RetryLoad) },
                        onBack = viewModel::goBack,
                    )
                }
                entry<AppRoute.DocumentEditorTool> { route ->
                    DocumentEditorToolRoute(
                        documentId = route.documentId,
                        tool = route.tool,
                        viewerViewModel = viewerViewModel,
                        toolsViewModel = toolsViewModel,
                        isActive = currentRoute == route,
                        onBack = viewModel::goBack,
                    )
                }
                entry<AppRoute.Tags> { TagsRoute(onNavigationBack = viewModel::goBack) }
                entry<AppRoute.Signatures> { SignaturesScreen(onBack = viewModel::goBack) }
                entry<AppRoute.PasswordSettings> { PasswordSettingsScreen(onBack = viewModel::goBack) }
                entry<AppRoute.DriveBackup> { DriveBackupRoute(onBack = viewModel::goBack) }
            },
        )
    }
}
