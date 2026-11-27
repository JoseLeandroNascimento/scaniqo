package com.joseleandro.scaniqo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.feature.editor.R
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentProtectionOptions
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorProtectionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun explicitPasswordNeedsMatchingConfirmationAndUsesBoundedTabletAction() {
        val events = mutableListOf<DocumentToolsEvent>()
        val draftUpdates = mutableListOf<Boolean>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(1024.dp, 800.dp))) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    ScaniqoTheme {
                        DocumentProtectionOptions(
                            state = DocumentToolsUiState(workspace = PdfWorkspace(
                                documentId = 1, workspaceId = "test", directoryUri = "test",
                                pages = listOf(PdfPageSpec("page", "test", 0)),
                            )),
                            onEvent = events::add, modifier = Modifier.fillMaxSize(),
                            onDraftChanged = draftUpdates::add,
                        )
                    }
                }
            }
        }
        val apply = compose.onNodeWithText(context.getString(R.string.document_password_apply))
        apply.assertIsDisplayed().assertIsNotEnabled()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("my-document-password")
        fields[1].performTextInput("different")
        apply.assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.document_password_mismatch)).assertIsDisplayed()
        fields[1].performTextReplacement("my-document-password")
        apply.assertIsEnabled()
        val buttonBounds = apply.fetchSemanticsNode().boundsInRoot
        val fieldBounds = fields[0].fetchSemanticsNode().boundsInRoot
        assertTrue("Tablet action should be narrower than the form", buttonBounds.width < fieldBounds.width)
        apply.performClick()
        compose.runOnIdle {
            assertEquals(listOf(DocumentToolsEvent.SetDocumentPassword("my-document-password", "my-document-password")), events)
            assertTrue(draftUpdates.contains(true))
        }
    }
}
