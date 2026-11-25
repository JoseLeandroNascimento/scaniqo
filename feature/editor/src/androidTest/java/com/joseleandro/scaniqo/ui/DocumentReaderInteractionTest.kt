package com.joseleandro.scaniqo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ReadableDocument
import com.joseleandro.scaniqo.ui.documentCommon.event.DocumentActionEvent
import com.joseleandro.scaniqo.ui.documentEditor.DocumentReaderScreen
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentReaderUiState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentViewerUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.editor.R

class DocumentReaderInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val document = DocumentModel(1, "Contrato", "preview.pdf", 2, 0, 500)

    @Test fun readerOffersTheEditorsExportMenuAndAnEditButtonWithoutEditingTools() {
        var editedPage: Int? = null
        var action: DocumentActionEvent? = null
        compose.setContent {
            Reader(onEdit = { editedPage = it }, onAction = { action = it })
        }
        compose.onNodeWithText(context.getString(R.string.tools_filters)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.pages_scan)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_edit_document))
            .assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, editedPage) }

        openMenu()
        compose.onNodeWithText(context.getString(R.string.document_metadata_edit)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(CoreResourcesR.string.document_download))
            .assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(DocumentActionEvent.Download(document), action) }
        openMenu()
        compose.onNodeWithText(context.getString(CoreResourcesR.string.document_share))
            .assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(DocumentActionEvent.Share(document), action) }
    }

    @Test fun editingStartsOnThePageOpenedFromSearch() {
        var editedPage: Int? = null
        compose.setContent { Reader(initialPageNumber = 2, onEdit = { editedPage = it }) }
        compose.onNodeWithContentDescription(context.getString(R.string.reader_edit_document)).performClick()
        compose.runOnIdle { assertEquals(2, editedPage) }
    }

    @Test fun lockedDocumentsHideContentAndEditingUntilUnlocked() {
        val protected = document.copy(protectionKeyId = "protected")
        var unlockRequested = false
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    DocumentReaderScreen(
                        documentId = protected.id,
                        uiState = DocumentViewerUiState(document = protected, isLoading = false),
                        readerState = DocumentReaderUiState(document = protected, isLocked = true),
                        onDocumentAction = {}, onRetryLoad = {}, onNavigationBack = {}, onEdit = {},
                        onUnlock = { unlockRequested = true },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.reader_edit_document)).assertDoesNotExist()
        compose.onNodeWithText(document.name).assertDoesNotExist()
        compose.onNodeWithText(context.getString(CoreResourcesR.string.tools_unlock)).performClick()
        compose.runOnIdle { assertTrue(unlockRequested) }
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription(
            context.getString(CoreResourcesR.string.document_actions, bidiArgument(document.name, context.resources.configuration.locales[0])),
        ).performClick()
    }

    @Composable
    private fun Reader(
        initialPageNumber: Int? = null,
        onEdit: (Int?) -> Unit = {},
        onAction: (DocumentActionEvent) -> Unit = {},
    ) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            ScaniqoTheme {
                DocumentReaderScreen(
                    documentId = document.id,
                    uiState = DocumentViewerUiState(document = document, isLoading = false),
                    readerState = DocumentReaderUiState(
                        document = document, readable = ReadableDocument(document.pdfUri, document.pageCount),
                    ),
                    onDocumentAction = onAction, onRetryLoad = {}, onNavigationBack = {},
                    onEdit = onEdit, onUnlock = {}, initialPageNumber = initialPageNumber,
                )
            }
        }
    }
}
