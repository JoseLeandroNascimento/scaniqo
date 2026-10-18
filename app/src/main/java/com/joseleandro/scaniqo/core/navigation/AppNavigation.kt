package com.joseleandro.scaniqo.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.joseleandro.scaniqo.core.security.PasswordVault
import com.joseleandro.scaniqo.ui.documentEditor.DocumentToolsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerRoute
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.platform.DocumentToolsHandler
import com.joseleandro.scaniqo.ui.documents.DocumentsRoute
import com.joseleandro.scaniqo.ui.documents.DocumentsViewModel
import com.joseleandro.scaniqo.ui.documents.PdfImportViewModel
import com.joseleandro.scaniqo.ui.documents.platform.DocumentActionsHandler
import com.joseleandro.scaniqo.ui.documents.platform.PdfImportHandler
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
    toolsViewModel: DocumentToolsViewModel = koinViewModel(),
    importViewModel: PdfImportViewModel = koinViewModel(),
    vault: PasswordVault = koinInject(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentRoute = viewModel.backStack.lastOrNull()
    val documentsState by documentsViewModel.uiState.collectAsStateWithLifecycle()
    val canStartDocumentAction = !documentsState.isScanning && !documentsState.isPreparingScan &&
        !documentsState.isDeleting && documentsState.busyDocumentId == null &&
        documentsState.editor == null && documentsState.pendingDeletion == null
    val navigate: (AppRoute) -> Unit = { route ->
        scope.launch { drawerState.close(); viewModel.navigateTo(route) }
    }
    val importPdf: () -> Unit = {
        if (canStartDocumentAction) scope.launch { drawerState.close(); importViewModel.chooseFile() }
    }

    DocumentActionsHandler(viewModel = documentsViewModel)
    DocumentToolsHandler(
        viewModel = toolsViewModel,
        onNavigateBack = viewModel::goBack,
        onNavigateSignatures = { viewModel.navigateTo(AppRoute.Signatures) },
        onNavigatePasswordSettings = { viewModel.navigateTo(AppRoute.PasswordSettings) },
    )
    PdfImportHandler(
        viewModel = importViewModel,
        documents = documentsViewModel,
        vault = vault,
        showDialogs = currentRoute == AppRoute.Documents,
        onPasswordSettings = { viewModel.navigateTo(AppRoute.PasswordSettings) },
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
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        onImportPdf = importPdf,
                        onOpenDocument = { viewModel.navigateTo(AppRoute.DocumentViewer(it)) },
                    )
                }
                entry<AppRoute.DocumentViewer> { route ->
                    DocumentViewerRoute(
                        documentId = route.documentId,
                        viewModel = documentsViewModel,
                        toolsViewModel = toolsViewModel,
                        onNavigationBack = viewModel::goBack,
                    )
                }
                entry<AppRoute.Tags> { TagsRoute(onNavigationBack = viewModel::goBack) }
                entry<AppRoute.Signatures> { SignaturesScreen(onBack = viewModel::goBack) }
                entry<AppRoute.PasswordSettings> { PasswordSettingsScreen(onBack = viewModel::goBack) }
            },
        )
    }
}
