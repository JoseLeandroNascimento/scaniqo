package com.joseleandro.scaniqo.ui

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.core.documentui.R
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeLibrarySheet
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentMergeSelectionRow
import com.joseleandro.scaniqo.ui.documentCommon.components.DocumentSourceStep
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PdfSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()

    @After fun cleanUp() { files.forEach(File::delete) }

    @Test fun singleSelectionDeliversOnlyTheChosenDocumentAndClosesLibrary() {
        val first = pdf("First PDF", Color.RED)
        val second = pdf("Second PDF", Color.BLUE).copy(id = 2)
        val selected = mutableListOf<DocumentModel>()
        compose.setContent {
            ScaniqoTheme {
                DocumentSourceStep(
                    toolTitle = "PDF", documents = listOf(first, second), isLoading = false,
                    loadError = false, enabled = true, onSelectDocument = selected::add,
                    onPickDevice = {}, onRetryDocuments = {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.merge_source_app)).performClick()
        compose.onNodeWithText(second.name).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(second), selected) }
        compose.onNodeWithText(context.getString(R.string.merge_app_title)).assertDoesNotExist()
    }

    @Test fun multipleSelectionKeepsCheckboxesAndDisabledRowsDoNotChangeSelection() {
        val document = pdf("Selectable PDF", Color.RED)
        val selected = mutableStateOf(emptySet<Long>())
        val enabled = mutableStateOf(true)
        var changes = 0
        var confirmations = 0
        compose.setContent {
            ScaniqoTheme {
                DocumentMergeLibrarySheet(
                    documents = listOf(document), selectedIds = selected.value, enabled = enabled.value,
                    onSelect = {
                        changes++
                        selected.value = if (it.id in selected.value) selected.value - it.id else selected.value + it.id
                    },
                    onDismiss = { confirmations++ },
                )
            }
        }
        compose.onNodeWithText(document.name).assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(setOf(document.id), selected.value); enabled.value = false }
        compose.onNodeWithText(document.name).assertIsOn().assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, changes)
            assertEquals(0, confirmations)
            enabled.value = true
        }
        compose.onNodeWithText(document.name).performClick().assertIsOff()
        compose.runOnIdle { assertEquals(2, changes); assertEquals(0, confirmations) }
    }

    @Test fun loadingErrorAndEmptyStatesKeepTheExistingSelectionCount() {
        val loading = mutableStateOf(true)
        val error = mutableStateOf(false)
        var retries = 0
        compose.setContent {
            ScaniqoTheme {
                DocumentMergeLibrarySheet(
                    documents = emptyList(), selectedIds = setOf(9L), isLoading = loading.value,
                    loadError = error.value, enabled = true, onRetry = { retries++ },
                    onSelect = {}, onDismiss = {},
                )
            }
        }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.merge_selection_library_empty)).assertDoesNotExist()
        compose.runOnIdle { loading.value = false; error.value = true }
        compose.onNodeWithText(context.getString(CoreResourcesR.string.documents_error_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(CoreResourcesR.string.documents_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries); error.value = false }
        compose.onNodeWithText(context.getString(R.string.merge_selection_library_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.merge_motion_selected, 1)).assertIsDisplayed()
    }

    @Test fun externalSelectedPdfRendersItsFirstPageAndKeepsAccessibleMoveAction() {
        val document = pdf("External PDF", Color.RED)
        val item = DocumentMergeItem(document.pdfUri, document.name)
        var movedTo = -1
        compose.setContent {
            ScaniqoTheme {
                DocumentMergeSelectionRow(
                    item = item, index = 0, count = 2, enabled = true,
                    onMove = { movedTo = it }, onRemove = {},
                )
            }
        }
        val description = context.getString(R.string.document_thumbnail_description)
        compose.waitUntil(10_000) {
            runCatching {
                val image = compose.onAllNodesWithContentDescription(description, useUnmergedTree = true)[0]
                    .captureToImage().toPixelMap()
                val pixel = image[image.width / 2, image.height / 2]
                pixel.red > .9f && pixel.blue < .1f
            }.getOrDefault(false)
        }
        compose.onNodeWithContentDescription(context.getString(CoreResourcesR.string.document_actions, bidiArgument(item.name, context.resources.configuration.locales[0])))
            .performClick()
        compose.onNodeWithText(context.getString(R.string.merge_move_up)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.merge_move_down)).performClick()
        compose.runOnIdle { assertEquals(1, movedTo) }
    }

    private fun pdf(name: String, color: Int): DocumentModel {
        val file = File.createTempFile("pdf-selection-", ".pdf", context.cacheDir).also(files::add)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(200, 280, 1).create())
            page.canvas.drawColor(color)
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
        return DocumentModel(id = 1, name = name, pdfUri = file.toURI().toString(), pageCount = 1,
            createdAt = 0, sizeBytes = file.length())
    }
}
