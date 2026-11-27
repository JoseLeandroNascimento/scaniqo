package com.joseleandro.scaniqo.ui.documents

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.takeScreenshot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.feature.library.R
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentCommon.state.DocumentActionsUiState
import com.joseleandro.scaniqo.ui.documents.state.DocumentsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

class DocumentsAdaptiveLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val documents = List(8) { index ->
        DocumentModel(
            id = index.toLong() + 1,
            name = "Document ${index + 1}",
            pdfUri = "preview.pdf",
            pageCount = 3,
            createdAt = 1781431200000L,
            sizeBytes = 248_000L,
            thumbnailUri = null,
            tagId = null,
        )
    }

    @Test
    fun resizingReflowsDocumentsAndPreservesOpenAndShareActions() {
        val size = mutableStateOf(DpSize(390.dp, 800.dp))
        val menuVisible = mutableStateOf(true)
        val opened = mutableListOf<Long>()
        val events = mutableListOf<DocumentActionEvent>()
        setLibrary(size, menuVisible, onOpen = opened::add, onAction = events::add)

        val firstPhone = compose.onNodeWithText(documents[0].name, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val secondPhone = compose.onNodeWithText(documents[1].name, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Phone documents should form a vertical list", secondPhone.top > firstPhone.bottom)
        compose.onNodeWithContentDescription("Abrir menu").assertIsDisplayed()

        compose.runOnIdle {
            size.value = DpSize(1280.dp, 800.dp)
            menuVisible.value = false
        }

        val firstTablet = compose.onNodeWithText(documents[0].name, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val secondTablet = compose.onNodeWithText(documents[1].name, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Tablet documents should share the row", firstTablet.top, secondTablet.top, 1f)
        assertTrue("Tablet documents should occupy separate columns", secondTablet.left > firstTablet.right)
        compose.onNodeWithContentDescription("Abrir menu").assertDoesNotExist()

        compose.onNodeWithText(documents[0].name).performClick()
        compose.onNodeWithContentDescription(actionsDescription(documents[0])).performClick()
        compose.onNodeWithText(compose.activity.getString(CoreResourcesR.string.document_share))
            .assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(documents[0].id), opened)
            assertEquals(listOf(DocumentActionEvent.Share(documents[0])), events)
        }
    }

    @Test
    fun shortWindowAndLargeFontsKeepLastDocumentActionsAndScanReachable() {
        val events = mutableListOf<DocumentActionEvent>()
        setLibrary(
            size = mutableStateOf(DpSize(600.dp, 480.dp)),
            menuVisible = mutableStateOf(false),
            fontScale = 2f,
            onAction = events::add,
        )

        captureLayoutDiagnostics()
        val scanLabel = compose.activity.getString(R.string.documents_scan)
        compose.onNodeWithText(scanLabel, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(scanLabel).assertIsDisplayed()
        compose.onNode(
            hasScrollToIndexAction() and !hasAnyAncestor(hasScrollToIndexAction()),
        ).performScrollToNode(hasText(documents.last().name))
        compose.onNodeWithText(documents.last().name).assertIsDisplayed()
        compose.onNodeWithText(scanLabel, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(scanLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(actionsDescription(documents.last()))
            .assertIsDisplayed().performClick()
        compose.onNodeWithText(compose.activity.getString(CoreResourcesR.string.document_share))
            .performClick()
        compose.onNodeWithText(scanLabel, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(scanLabel).assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals(DocumentActionEvent.Share(documents.last()), events.first())
            assertTrue(events.last() is DocumentActionEvent.StartScan)
        }
    }

    private fun setLibrary(
        size: State<DpSize>,
        menuVisible: State<Boolean>,
        fontScale: Float = 1f,
        onOpen: (Long) -> Unit = {},
        onAction: (DocumentActionEvent) -> Unit = {},
    ) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        ScaniqoTheme(dynamicColor = false) {
                            DocumentsScreen(
                                uiState = DocumentsUiState(isLoading = false, documents = documents),
                                actionsState = DocumentActionsUiState(isLoadingTags = false),
                                onEvent = {},
                                onDocumentAction = onAction,
                                onOpenMenu = {},
                                onImportPdf = {},
                                onSearch = {},
                                onOpenDocument = onOpen,
                                onMergePdfs = {},
                                onSignDocument = {},
                                onAllTools = {},
                                showNavigationMenu = menuVisible.value,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun captureLayoutDiagnostics() {
        compose.waitForIdle()
        val directory = File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: requireNotNull(compose.activity.getExternalFilesDir("screenshots")).absolutePath,
        ).apply { mkdirs() }
        File(directory, "library-short-window.txt").writeText(
            compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 20),
        )
        File(directory, "library-short-window.png").outputStream().use {
            takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun actionsDescription(document: DocumentModel): String =
        compose.activity.getString(
            CoreResourcesR.string.document_actions,
            bidiArgument(document.name, compose.activity.resources.configuration.locales[0]),
        )
}
