package com.joseleandro.scaniqo.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.ads.EditorBannerAd
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentMetadataUiState
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerScreen
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentViewerUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Rule
import org.junit.Test

/** The viewer owns an injectable slot; these tests never initialize or request real ads. */
class EditorBannerInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun selectingAToolRemovesTheBannerWithoutRemovingToolControls() {
        var tools by mutableStateOf(readyTools())
        compose.setContent {
            Viewer(tools = tools, onEvent = { event ->
                if (event is DocumentToolsEvent.OpenTool) tools = tools.copy(tool = event.tool)
            })
        }
        val banner = compose.onNodeWithText(FAKE_BANNER)
        banner.assertIsDisplayed()

        // The open panel repeats this title; target the actionable toolbar control.
        val filters = compose.onNode(
            hasText(context.getString(R.string.tools_filters)) and hasClickAction(),
        )
        filters.performClick()
        banner.assertDoesNotExist()
        filters.assertIsDisplayed().assertIsSelected()

        compose.runOnIdle { tools = tools.copy(tool = null) }
        banner.assertIsDisplayed()
        compose.runOnIdle { tools = tools.copy(hasChanges = true) }
        banner.assertDoesNotExist()
        compose.runOnIdle { tools = tools.copy(hasChanges = false) }
        banner.assertIsDisplayed()
    }

    @Test
    fun processingSignaturePlacementAndSaveSheetRemoveTheBanner() {
        var tools by mutableStateOf(readyTools())
        var actions by mutableStateOf(DocumentActionsUiState(isLoadingTags = false))
        compose.setContent { Viewer(tools, actions) }
        val banner = compose.onNodeWithText(FAKE_BANNER)
        banner.assertIsDisplayed()

        compose.runOnIdle { tools = tools.copy(isSaving = true) }
        banner.assertDoesNotExist()
        compose.runOnIdle { tools = tools.copy(isSaving = false, signatureAwaitingPlacement = true) }
        banner.assertDoesNotExist()
        compose.runOnIdle { tools = tools.copy(signatureAwaitingPlacement = false) }
        banner.assertIsDisplayed()

        compose.runOnIdle {
            actions = actions.copy(editor = DocumentMetadataUiState(documentId = 1, name = "Contrato"))
        }
        banner.assertDoesNotExist()
        compose.runOnIdle { actions = actions.copy(editor = null) }
        banner.assertIsDisplayed()
    }

    @Test
    fun editorBannerPreviewFitsAt320DpAndDisappearsInCompactWindowsWithoutSdk() {
        var width by mutableIntStateOf(320)
        var height by mutableIntStateOf(800)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { screenHeightDp = height }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalConfiguration provides configuration,
            ) {
                ScaniqoTheme {
                    Box(Modifier.width(width.dp)) { EditorBannerAd() }
                }
            }
        }
        val preview = compose.onNodeWithText(context.getString(R.string.ads_preview_label))
        preview.assertIsDisplayed()
        compose.runOnIdle { width = 319 }
        preview.assertDoesNotExist()
        compose.runOnIdle { width = 320 }
        preview.assertIsDisplayed()
        compose.runOnIdle { height = 500 }
        preview.assertDoesNotExist()
    }

    @Composable
    private fun Viewer(
        tools: DocumentToolsUiState,
        actions: DocumentActionsUiState = DocumentActionsUiState(isLoadingTags = false),
        onEvent: (DocumentToolsEvent) -> Unit = {},
    ) {
        ScaniqoTheme {
            DocumentViewerScreen(
                documentId = 1,
                uiState = DocumentViewerUiState(document = document, actions = actions, isLoading = false),
                onDocumentAction = {},
                onRetryLoad = {},
                onNavigationBack = {},
                toolsState = tools,
                onToolsEvent = onEvent,
                banner = { Text(FAKE_BANNER) },
            )
        }
    }

    private fun readyTools() = DocumentToolsUiState(
        document = document,
        workspace = PdfWorkspace(
            documentId = 1, workspaceId = "banner-test", directoryUri = "preview",
            pages = listOf(PdfPageSpec(id = "page", sourceUri = "preview.pdf", sourcePageIndex = 0)),
        ),
        selectedPageId = "page",
        // A deterministic error preview avoids PDF rendering or an indeterminate progress spinner.
        pagePreviews = mapOf("page" to DocumentPagePreviewState(error = true)),
    )

    private companion object {
        const val FAKE_BANNER = "Editor test banner"
        val document = DocumentModel(1, "Contrato", "preview.pdf", 1, 0, 0)
    }
}
