package com.joseleandro.scaniqo.ui.pdfTools

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.format.Formatter
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.core.documentui.R as DocumentUiR
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfCompressionLevel
import com.joseleandro.scaniqo.domain.model.PdfImageFormat
import com.joseleandro.scaniqo.domain.model.PdfUtility
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PdfUtilitySourcePreviewInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()

    @After fun removeFixtures() { files.forEach(File::delete) }

    @Test fun compressionUsesTheSelectedPdfThumbnailAndDisablesChangeWhileBusy() =
        verifySourceChanges(PdfUtility.COMPRESS_PDF)

    @Test fun extractionUsesTheSelectedPdfThumbnailAndDisablesChangeWhileBusy() =
        verifySourceChanges(PdfUtility.EXTRACT_PAGES)

    @Test fun imageExportUsesTheSelectedPdfThumbnailAndDisablesChangeWhileBusy() =
        verifySourceChanges(PdfUtility.PDF_TO_IMAGES)

    private fun verifySourceChanges(tool: PdfUtility) {
        val first = pdf("First PDF", Color.RED)
        val second = pdf("Second PDF", Color.BLUE)
        val document = mutableStateOf(first)
        val busy = mutableStateOf(false)
        var chooseCount = 0
        compose.setContent {
            ScaniqoTheme {
                PdfUtilityWorkspace(
                    tool = tool, state = PdfUtilityUiState(loading = false, busy = busy.value),
                    document = document.value, pageSelection = PdfUtilityPageSelectionState(),
                    password = "", format = PdfImageFormat.JPEG, compression = PdfCompressionLevel.BALANCED,
                    externalPicker = false, onBack = {}, onChooseDocument = { chooseCount++ },
                    onPageSelection = {}, onPassword = {}, onFormat = {}, onCompression = {},
                    onAction = {}, onRetry = {}, onOpenDocument = {},
                )
            }
        }
        waitForThumbnail(red = true)
        compose.onNodeWithText(first.name).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, chooseCount)
            document.value = second
        }
        waitForThumbnail(red = false)
        compose.onNodeWithText(first.name).assertDoesNotExist()
        compose.onNodeWithText(second.name).assertIsDisplayed()
        val pages = context.resources.getQuantityString(
            com.joseleandro.scaniqo.core.resources.R.plurals.document_page_count, 1, 1,
        )
        compose.onNodeWithText(pages + " · " + Formatter.formatShortFileSize(context, second.sizeBytes)).assertIsDisplayed()
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithText(second.name).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, chooseCount) }
    }

    private fun waitForThumbnail(red: Boolean) {
        val description = context.getString(DocumentUiR.string.document_thumbnail_description)
        compose.waitUntil(10_000) {
            runCatching {
                val image = compose.onAllNodesWithContentDescription(description, useUnmergedTree = true)[0]
                    .captureToImage().toPixelMap()
                val pixel = image[image.width / 2, image.height / 2]
                if (red) pixel.red > .9f && pixel.blue < .1f else pixel.blue > .9f && pixel.red < .1f
            }.getOrDefault(false)
        }
        assertTrue(compose.onAllNodesWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty())
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
        return DocumentModel(name = name, pdfUri = file.toURI().toString(), pageCount = 1,
            createdAt = 0, sizeBytes = file.length())
    }
}
