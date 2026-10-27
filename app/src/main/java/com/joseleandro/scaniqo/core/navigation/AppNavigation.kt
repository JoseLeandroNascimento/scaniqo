package com.joseleandro.scaniqo.core.navigation

import androidx.compose.foundation.layout.Box
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
import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.domain.model.PdfUtility
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documents.model.PdfImportDestination
import com.joseleandro.scaniqo.ui.ads.ToolsBannerAd
import com.joseleandro.scaniqo.ui.ads.EditorBannerAd
import com.joseleandro.scaniqo.ui.toolsHub.ToolsHubScreen
import com.joseleandro.scaniqo.ui.pdfTools.PdfUtilityRoute
import com.joseleandro.scaniqo.ui.documentCertificates.DocumentCertificatesRoute
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentActionOverlays
import com.joseleandro.scaniqo.ui.documentCommon.feedback.rememberDocumentToastState
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.components.toast.AppToastHost
import com.joseleandro.scaniqo.ui.documentEditor.DocumentToolsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerRoute
import com.joseleandro.scaniqo.ui.documentEditor.DocumentEditorToolRoute
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.platform.DocumentToolsHandler
import com.joseleandro.scaniqo.ui.documentMerge.DocumentQuickMergeViewModel
import com.joseleandro.scaniqo.ui.documentMerge.DocumentMergeDestination
import com.joseleandro.scaniqo.ui.documentSignature.DocumentSignatureRoute
import com.joseleandro.scaniqo.ui.documents.platform.DocumentQuickActionsHost
import com.joseleandro.scaniqo.ui.documentSearch.DocumentSearchRoute
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
import com.joseleandro.scaniqo.ui.settings.SettingsRoute
import com.joseleandro.scaniqo.ui.settings.SettingsSection
import com.joseleandro.scaniqo.ui.signatures.SignaturesScreen
import com.joseleandro.scaniqo.ui.tags.TagsRoute
import com.joseleandro.scaniqo.ui.trash.TrashRoute
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
    val libraryActionsVisible = currentRoute == AppRoute.Documents || currentRoute == AppRoute.Tools
    var showSignaturePicker by rememberSaveable { mutableStateOf(false) }
    var utilityImportedDocumentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var utilityImportedTool by rememberSaveable { mutableStateOf<PdfUtility?>(null) }
    val importState by importViewModel.uiState.collectAsStateWithLifecycle()
    val documentsState by documentsViewModel.uiState.collectAsStateWithLifecycle()
    val actionsState by actionsViewModel.uiState.collectAsStateWithLifecycle()
    val canStartDocumentAction = !importState.isActive && !actionsState.isBusy &&
        actionsState.editor == null && actionsState.pendingDeletion == null
    val navigate: (AppRoute) -> Unit = { route ->
        scope.launch { drawerState.close(); viewModel.navigateTo(route) }
    }
    val importPdf: () -> Unit = {
        if (canStartDocumentAction) scope.launch { drawerState.close(); importViewModel.chooseFile() }
    }
    val importForTool: (PdfImportDestination) -> Unit = { destination ->
        if (canStartDocumentAction) scope.launch {
            drawerState.close()
            importViewModel.chooseFile(destination)
        }
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
        showDialogs = libraryActionsVisible || currentRoute is AppRoute.PdfUtilityTool,
        onPasswordSettings = { viewModel.navigateTo(AppRoute.PasswordSettings) },
        onToolImported = { id, destination ->
            documentsViewModel.onEvent(DocumentsEvent.DocumentCreated(id, null))
            when (destination) {
                PdfImportDestination.EXTRACT_PAGES, PdfImportDestination.PDF_TO_IMAGES,
                PdfImportDestination.COMPRESS_PDF -> {
                    val utility = when (destination) {
                        PdfImportDestination.EXTRACT_PAGES -> PdfUtility.EXTRACT_PAGES
                        PdfImportDestination.PDF_TO_IMAGES -> PdfUtility.PDF_TO_IMAGES
                        else -> PdfUtility.COMPRESS_PDF
                    }
                    utilityImportedTool = utility
                    utilityImportedDocumentId = id
                    if (viewModel.backStack.lastOrNull() != AppRoute.PdfUtilityTool(utility)) {
                        viewModel.navigateTo(AppRoute.PdfUtilityTool(utility))
                    }
                }
                else -> {
                    val destinationRoute = when (destination) {
                        PdfImportDestination.FILL_PAGE -> AppRoute.DocumentEditorTool(id, DocumentTool.FORMS)
                        PdfImportDestination.SIGN -> AppRoute.DocumentSignature(id)
                        else -> AppRoute.DocumentViewer(id)
                    }
                    if (viewModel.backStack.lastOrNull() != destinationRoute) {
                        val origin = when (destination) {
                            PdfImportDestination.SIGN -> DocumentToolOrigin.SIGNATURE
                            PdfImportDestination.FILL_PAGE -> DocumentToolOrigin.FILL_PAGE
                            else -> null
                        }
                        viewModel.navigateTo(AppRoute.DocumentViewer(id, toolOrigin = origin))
                        if (destinationRoute !is AppRoute.DocumentViewer) viewModel.navigateTo(destinationRoute)
                    }
                }
            }
        },
    )
    DocumentQuickActionsHost(
        showSignaturePicker = showSignaturePicker && libraryActionsVisible,
        onDismissSignaturePicker = { showSignaturePicker = false },
        documents = documentsState.documents,
        isLoading = documentsState.isLoading,
        loadError = documentsState.loadError,
        onRetry = { documentsViewModel.onEvent(DocumentsEvent.RetryLoad) },
        onImport = { importForTool(PdfImportDestination.SIGN) },
        onSign = { documentId ->
            viewModel.navigateTo(AppRoute.DocumentViewer(documentId, toolOrigin = DocumentToolOrigin.SIGNATURE))
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
        Box(Modifier.fillMaxSize()) {
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
                        onSearch = { viewModel.navigateTo(AppRoute.DocumentSearch) },
                        onAllTools = { if (canStartDocumentAction) viewModel.navigateTo(AppRoute.Tools) },
                        onMergePdfs = { if (canStartDocumentAction) viewModel.navigateTo(AppRoute.DocumentMerge()) },
                        onSignDocument = { if (canStartDocumentAction) showSignaturePicker = true },
                        onOpenDocument = { viewModel.navigateTo(AppRoute.DocumentViewer(it)) },
                        adsEnabled = currentRoute == AppRoute.Documents && canStartDocumentAction &&
                            !showSignaturePicker && drawerState.currentValue == DrawerValue.Closed &&
                            drawerState.targetValue == DrawerValue.Closed,
                    )
                }
                entry<AppRoute.Tools> {
                    val toast = rememberDocumentToastState(actionsState.message) {
                        actionsViewModel.onDocumentAction(DocumentActionEvent.DismissMessage)
                    }
                    ToolsHubScreen(
                        documents = documentsState,
                        enabled = canStartDocumentAction,
                        onBack = viewModel::goBack,
                        onImport = importPdf,
                        onMerge = { if (canStartDocumentAction) viewModel.navigateTo(AppRoute.DocumentMerge()) },
                        onSignDocument = { id ->
                            if (canStartDocumentAction) {
                                viewModel.navigateTo(AppRoute.DocumentViewer(id, toolOrigin = DocumentToolOrigin.SIGNATURE))
                                viewModel.navigateTo(AppRoute.DocumentSignature(id))
                            }
                        },
                        onImportForSign = { importForTool(PdfImportDestination.SIGN) },
                        onUtility = { if (canStartDocumentAction) viewModel.navigateTo(AppRoute.PdfUtilityTool(it)) },
                        onEdit = { id, tool ->
                            if (canStartDocumentAction) {
                                viewModel.navigateTo(AppRoute.DocumentViewer(id, toolOrigin =
                                    DocumentToolOrigin.FILL_PAGE.takeIf { tool == DocumentTool.FORMS }))
                                if (tool != null) viewModel.navigateTo(AppRoute.DocumentEditorTool(id, tool))
                            }
                        },
                        onImportForEditor = { tool -> importForTool(if (tool == DocumentTool.FORMS)
                            PdfImportDestination.FILL_PAGE else PdfImportDestination.EDITOR) },
                        onRetryDocuments = { documentsViewModel.onEvent(DocumentsEvent.RetryLoad) },
                        snackbarHost = { AppToastHost(toast) },
                        banner = {
                            if (currentRoute == AppRoute.Tools && !showSignaturePicker &&
                                drawerState.currentValue == DrawerValue.Closed &&
                                drawerState.targetValue == DrawerValue.Closed
                            ) ToolsBannerAd()
                        },
                    )
                }
                entry<AppRoute.PdfUtilityTool> { route ->
                    PdfUtilityRoute(
                        tool = route.tool,
                        onBack = viewModel::goBack,
                        onImport = {
                            when (route.tool) {
                                PdfUtility.EXTRACT_PAGES -> importForTool(PdfImportDestination.EXTRACT_PAGES)
                                PdfUtility.PDF_TO_IMAGES -> importForTool(PdfImportDestination.PDF_TO_IMAGES)
                                PdfUtility.COMPRESS_PDF -> importForTool(PdfImportDestination.COMPRESS_PDF)
                                else -> importPdf()
                            }
                        },
                        importBusy = importState.isActive,
                        actionsViewModel = actionsViewModel,
                        importedDocumentId = utilityImportedDocumentId.takeIf { utilityImportedTool == route.tool },
                        onImportedSelected = {
                            utilityImportedDocumentId = null
                            utilityImportedTool = null
                        },
                        onOpenDocument = { id -> viewModel.navigateTo(AppRoute.DocumentViewer(id)) },
                    )
                }
                entry<AppRoute.DocumentCertificates> { DocumentCertificatesRoute(onBack = viewModel::goBack) }
                entry<AppRoute.DocumentSearch> {
                    DocumentSearchRoute(
                        onBack = viewModel::goBack,
                        onOpenDocument = { documentId, pageNumber ->
                            viewModel.navigateTo(AppRoute.DocumentViewer(documentId, pageNumber))
                        },
                    )
                }
                entry<AppRoute.DocumentViewer> { route ->
                    DocumentViewerRoute(
                        documentId = route.documentId,
                        initialPageNumber = route.initialPageNumber,
                        isActive = currentRoute == route,
                        saveAdOrigin = when (route.toolOrigin) {
                            DocumentToolOrigin.SIGNATURE -> RewardedTool.SIGNATURE
                            DocumentToolOrigin.FILL_PAGE -> RewardedTool.FILL_PAGE
                            null -> null
                        },
                        viewModel = viewerViewModel,
                        actionsViewModel = actionsViewModel,
                        toolsViewModel = toolsViewModel,
                        onNavigationBack = viewModel::goBack,
                        banner = {
                            if (currentRoute == route && drawerState.currentValue == DrawerValue.Closed &&
                                drawerState.targetValue == DrawerValue.Closed
                            ) EditorBannerAd()
                        },
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
                        onOpenDocument = { id -> viewModel.navigateTo(AppRoute.DocumentViewer(id)) },
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
                entry<AppRoute.Trash> { TrashRoute(onBack = viewModel::goBack) }
                entry<AppRoute.Tags> { TagsRoute(onNavigationBack = viewModel::goBack) }
                entry<AppRoute.Signatures> { SignaturesScreen(onBack = viewModel::goBack) }
                entry<AppRoute.Settings> {
                    SettingsRoute(
                        section = SettingsSection.HOME, onBack = viewModel::goBack,
                        onPersonalization = { viewModel.navigateTo(AppRoute.Personalization) },
                        onPdf = { viewModel.navigateTo(AppRoute.PdfPreferences) },
                        onBackup = { viewModel.navigateTo(AppRoute.DriveBackup) },
                        onPasswords = { viewModel.navigateTo(AppRoute.PasswordSettings) },
                    )
                }
                entry<AppRoute.Personalization> {
                    SettingsRoute(section = SettingsSection.PERSONALIZATION, onBack = viewModel::goBack)
                }
                entry<AppRoute.PdfPreferences> {
                    SettingsRoute(section = SettingsSection.PDF, onBack = viewModel::goBack)
                }
                entry<AppRoute.PasswordSettings> { PasswordSettingsScreen(onBack = viewModel::goBack) }
                entry<AppRoute.DriveBackup> { DriveBackupRoute(onBack = viewModel::goBack) }
            },
        )
        DocumentActionOverlays(actionsState, actionsViewModel::onDocumentAction)
        }
    }
}
