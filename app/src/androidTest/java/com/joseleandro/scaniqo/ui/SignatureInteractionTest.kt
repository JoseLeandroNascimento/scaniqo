package com.joseleandro.scaniqo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentSignature.DocumentSignatureScreen
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SignatureInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun confirmElementAndFinishToolInvokeSeparateCallbacksOnce() {
        var applies = 0
        var finishes = 0
        var backs = 0
        val document = DocumentModel(
            id = 1, name = "Contrato", pdfUri = "preview.pdf", pageCount = 1,
            createdAt = 0, sizeBytes = 1,
        )
        val page = PdfPageSpec(id = "page", sourceUri = "preview.pdf", sourcePageIndex = 0)
        val draft = PdfSignaturePlacement(
            id = "signature", imageUri = "signature.png",
            x = .2f, y = .5f, width = .4f, height = .1f,
        )
        val state = DocumentToolsUiState(
            document = document,
            workspace = PdfWorkspace(1, "interaction", "preview", listOf(page)),
            selectedPageId = page.id,
            signatureDraft = draft,
            signaturePageId = page.id,
        )
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    DocumentSignatureScreen(
                        documentId = document.id,
                        state = state,
                        documentName = document.name,
                        isLoadingDocument = false,
                        documentError = false,
                        onEvent = {},
                        onBack = { backs++ },
                        onCreateSignature = {},
                        onRetry = {},
                        onApply = { applies++ },
                        onDone = { finishes++ },
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.fill_mark_confirm_element))
            .assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, applies)
            assertEquals(0, finishes)
            assertEquals(0, backs)
        }

        compose.onNodeWithText(context.getString(R.string.signature_finish_tool))
            .assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, applies)
            assertEquals(1, finishes)
            assertEquals(0, backs)
        }
    }
}
