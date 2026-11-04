package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.core.common.R as CoreCommonR
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.signatures.R

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.PdfSignatureImage
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import com.joseleandro.scaniqo.ui.signatures.SignatureEditorScreen
import com.joseleandro.scaniqo.ui.signatures.SignatureEditorState
import com.joseleandro.scaniqo.ui.signatures.SignatureImageStep
import com.joseleandro.scaniqo.ui.signatures.SignatureInput
import com.joseleandro.scaniqo.ui.signatures.SignaturesEvent
import com.joseleandro.scaniqo.ui.signatures.SignaturesViewModel
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SignatureImportInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtures = mutableListOf<File>()
    private val models = mutableListOf<SignaturesViewModel>()

    @After
    fun cleanUp() {
        compose.runOnIdle {
            models.forEach {
                it.onEvent(SignaturesEvent.DismissEditor)
                it.viewModelScope.cancel()
            }
        }
        fixtures.forEach { it.delete() }
    }

    @Test
    fun importTabOpensPickerAndKeepsToolbarSaveDisabledUntilAProcessedImageExists() {
        val events = mutableListOf<SignaturesEvent>()
        var imports = 0
        compose.setContent {
            ScaniqoTheme {
                SignatureEditorScreen(
                    SignatureEditorState(name = "Personal", input = SignatureInput.IMAGE),
                    onEvent = events::add,
                    onPickImage = { imports++ },
                )
            }
        }
        compose.onNodeWithTag("signature_pick_image").assertIsDisplayed().performClick()
        compose.onNodeWithTag(SAVE).assertIsNotEnabled()
        compose.onNodeWithTag("signature_name").assertDoesNotExist()
        compose.onNodeWithTag("signature_tab_image").assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, imports)
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun importWaitsForCropAndProcessingThenPreviewsAllBackgroundsBeforeSaving() {
        val repository = ControlledImages(signatureFile(transparent = false), signatureFile(transparent = true))
        repository.stageGate = CompletableDeferred()
        repository.processGate = CompletableDeferred()
        val model = showFlow(repository)

        compose.onNodeWithText(text(R.string.signature_import_image)).performScrollTo().performClick()
        compose.onNodeWithTag(CONFIRM).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.signature_import_processing)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, repository.processCalls) }

        repository.stageGate?.complete(Unit)
        waitForConfirm()
        compose.onNodeWithTag("signature_crop_canvas").assertIsDisplayed()
        compose.onNodeWithTag(CONFIRM).performClick()
        compose.onNodeWithTag(CONFIRM).assertIsNotEnabled()
        compose.runOnIdle { assertTrue(repository.savedUris.isEmpty()) }

        repository.processGate?.complete(Unit)
        waitForStep(model, SignatureImageStep.PREVIEW)
        waitForSave()
        compose.onNodeWithTag(CONFIRM).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.signature_import_transparent)).assertIsSelected()
        compose.onNodeWithText(text(R.string.signature_import_white)).performClick().assertIsSelected()
        compose.onNodeWithText(text(R.string.signature_import_dark)).performClick().assertIsSelected()
        compose.onNodeWithText(text(R.string.signature_import_transparent)).performClick().assertIsSelected()
        compose.runOnIdle { assertTrue(repository.savedUris.isEmpty()) }

        compose.onNodeWithText(text(R.string.signature_import_adjust_crop)).performScrollTo().performClick()
        waitForStep(model, SignatureImageStep.CROP)
        waitForConfirm()
        compose.onNodeWithTag(CONFIRM).performClick()
        waitForStep(model, SignatureImageStep.PREVIEW)
        waitForSave()
        compose.onNodeWithTag(SAVE).performClick()
        compose.onNodeWithTag("signature_save_dialog").assertIsDisplayed()
        compose.onNodeWithTag("signature_name").assertIsDisplayed()
        compose.onNode(hasText(text(CoreResourcesR.string.runtime_save)) and hasAnyAncestor(hasTestTag("signature_save_dialog"))).assertIsEnabled().performClick()
        compose.waitUntil(10_000) { model.uiState.value.editor == null }
        compose.runOnIdle {
            assertEquals(listOf(repository.processedUri), repository.savedUris)
            assertEquals(1, model.uiState.value.signatures.size)
        }
    }

    @Test
    fun processingFailureKeepsCropForRetryAndDoesNotOfferSave() {
        val repository = ControlledImages(signatureFile(false), signatureFile(true))
        repository.processingError = IllegalArgumentException(
            "Nenhum traço foi encontrado. Ajuste o recorte ou use uma imagem mais nítida.",
        )
        val model = showFlow(repository)
        compose.onNodeWithText(text(R.string.signature_import_image)).performScrollTo().performClick()
        waitForConfirm()
        compose.onNodeWithTag(CONFIRM).performClick()
        compose.waitUntil(10_000) { model.uiState.value.editor?.error != null }
        compose.onNodeWithText(text(CoreCommonR.string.signature_import_error_no_ink)).assertIsDisplayed()
        compose.onNodeWithTag(SAVE).assertIsNotEnabled()
        compose.onNodeWithTag("signature_crop_canvas").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(SignatureImageStep.CROP, model.uiState.value.editor?.imageStep)
            assertTrue(repository.savedUris.isEmpty())
            repository.processingError = null
        }
        compose.onNodeWithTag(CONFIRM).assertIsEnabled().performClick()
        waitForStep(model, SignatureImageStep.PREVIEW)
        waitForSave()
        compose.runOnIdle { assertEquals(2, repository.processCalls) }
    }

    @Test
    fun missingSourceCannotBeConfirmedAndCanBeCancelled() {
        val events = mutableListOf<SignaturesEvent>()
        showEditor(
            SignatureEditorState(
                input = SignatureInput.IMAGE,
                imageStep = SignatureImageStep.CROP,
                imageUri = "file:///missing-signature-import-source.png",
            ),
            events,
        )
        waitForImageFailure()
        compose.onNodeWithTag(CONFIRM).assertIsNotEnabled()
        compose.onNodeWithTag(SAVE).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(CoreResourcesR.string.runtime_back)).performClick()
        compose.runOnIdle { assertEquals(listOf(SignaturesEvent.EditorBack), events) }
    }

    @Test
    fun unreadableProcessedImageCannotBeSavedAndCanReturnToCrop() {
        val events = mutableListOf<SignaturesEvent>()
        showEditor(
            SignatureEditorState(
                input = SignatureInput.IMAGE,
                imageStep = SignatureImageStep.PREVIEW,
                processedImageUri = "file:///missing-signature-import-preview.png",
            ),
            events,
        )
        waitForImageFailure()
        compose.onNodeWithTag(SAVE).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.signature_import_adjust_crop)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(SignaturesEvent.AdjustCrop), events) }
    }

    @Test
    fun replacingLoadedPreviewWithUnreadableImageRevokesSaveReadiness() {
        val valid = signatureFile(true).toURI().toString()
        val editor = mutableStateOf(SignatureEditorState(
            input = SignatureInput.IMAGE,
            imageStep = SignatureImageStep.PREVIEW,
            processedImageUri = valid,
        ))
        compose.setContent {
            ScaniqoTheme { SignatureEditorScreen(editor.value, {}, {}) }
        }
        waitForSave()
        compose.runOnIdle {
            editor.value = editor.value.copy(processedImageUri = "file:///missing-replacement-preview.png")
        }
        waitForImageFailure()
        compose.onNodeWithTag(SAVE).assertIsNotEnabled()
    }

    @Test
    fun cameraPreviewOffersRetakeWithoutManualCropAndUsesTheSameToolbarSave() {
        val events = mutableListOf<SignaturesEvent>()
        showEditor(
            SignatureEditorState(
                input = SignatureInput.SCAN,
                imageStep = SignatureImageStep.PREVIEW,
                processedImageUri = signatureFile(true).toURI().toString(),
                fromCamera = true,
            ),
            events,
        )
        waitForSave()
        compose.onNodeWithTag("signature_crop_canvas").assertDoesNotExist()
        compose.onNodeWithTag(CONFIRM).assertDoesNotExist()
        compose.onNodeWithTag("signature_image_revise").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(SignaturesEvent.OpenCamera), events) }
    }

    private fun showEditor(editor: SignatureEditorState, events: MutableList<SignaturesEvent>) {
        compose.setContent {
            ScaniqoTheme { SignatureEditorScreen(editor, events::add, {}) }
        }
    }

    private fun showFlow(repository: ControlledImages): SignaturesViewModel {
        lateinit var model: SignaturesViewModel
        compose.runOnIdle {
            model = SignaturesViewModel(repository).also(models::add)
            model.onEvent(SignaturesEvent.Create)
            model.onEvent(SignaturesEvent.InputChanged(SignatureInput.IMAGE))
        }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ScaniqoTheme {
                state.editor?.let { editor ->
                    SignatureEditorScreen(
                        editor,
                        model::onEvent,
                        onPickImage = { model.onEvent(SignaturesEvent.ImageSelected(repository.sourceUri)) },
                    )
                }
            }
        }
        return model
    }

    private fun waitForConfirm() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(CONFIRM).fetchSemanticsNodes().any {
                it.config.getOrNull(SemanticsProperties.Disabled) == null
            }
        }
        compose.onNodeWithTag(CONFIRM).assertIsEnabled()
    }

    private fun waitForSave() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(SAVE).fetchSemanticsNodes().any {
                it.config.getOrNull(SemanticsProperties.Disabled) == null
            }
        }
        compose.onNodeWithTag(SAVE).assertIsEnabled()
    }

    private fun waitForStep(model: SignaturesViewModel, step: SignatureImageStep) {
        compose.waitUntil(10_000) {
            model.uiState.value.editor?.let { it.imageStep == step && !it.isProcessing } == true
        }
    }

    private fun waitForImageFailure() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(SAVE).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText(text(CoreResourcesR.string.runtime_image_unavailable))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(CoreResourcesR.string.runtime_image_unavailable)).assertIsDisplayed()
    }

    private fun text(id: Int) = context.getString(id)

    private fun signatureFile(transparent: Boolean): File {
        val file = File.createTempFile("signature-import-", ".png", context.cacheDir).also(fixtures::add)
        val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (transparent) Color.TRANSPARENT else Color.WHITE)
        Canvas(bitmap).drawLine(70f, 140f, 530f, 60f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLUE
            strokeWidth = 3f
        })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private class ControlledImages(source: File, processed: File) : SignatureRepository {
        val sourceUri = source.toURI().toString()
        val processedUri = processed.toURI().toString()
        val items = MutableStateFlow<List<SignatureModel>>(emptyList())
        val savedUris = mutableListOf<String>()
        var stageGate: CompletableDeferred<Unit>? = null
        var processGate: CompletableDeferred<Unit>? = null
        var processingError: Exception? = null
        var processCalls = 0

        override fun observeSignatures() = items
        override suspend fun stageImage(uri: String): PdfSignatureImage {
            stageGate?.await()
            return PdfSignatureImage(sourceUri, 3f)
        }
        override suspend fun processImage(uri: String, crop: PdfSignatureCrop): PdfSignatureImage {
            processCalls++
            processGate?.await()
            processingError?.let { throw it }
            return PdfSignatureImage(processedUri, 3f)
        }
        override suspend fun saveProcessedImage(name: String, uri: String, editingId: String?): SignatureModel {
            savedUris += uri
            return SignatureModel("imported", name, uri, 3f).also { items.value += it }
        }
        override suspend fun createDrawing(name: String, strokes: List<SignatureStroke>): SignatureModel =
            error("Drawing is outside this import journey")
        override suspend fun createTyped(name: String, text: String, font: SignatureFont, colorArgb: Int): SignatureModel =
            error("Typing is outside this import journey")
        override suspend fun importImage(name: String, uri: String, removeLightBackground: Boolean): SignatureModel =
            error("The UI must use staged import")
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun delete(id: String) = Unit
    }

    private companion object {
        const val CONFIRM = "signature_import_confirm"
        const val SAVE = "signature_save"
    }
}
