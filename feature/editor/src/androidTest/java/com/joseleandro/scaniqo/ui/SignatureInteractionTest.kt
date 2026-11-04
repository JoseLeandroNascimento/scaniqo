package com.joseleandro.scaniqo.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentSignature.DocumentSignatureControls
import com.joseleandro.scaniqo.ui.documentSignature.DocumentSignatureScreen
import com.joseleandro.scaniqo.ui.documentSignature.SignatureAdjustmentMode
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class SignatureInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun applyChangesAndFinishInvokeSeparateCallbacksOnce() {
        val fixture = showEditor()
        captureScreen("signature-toolbar.png")
        compose.onNodeWithTag("signature_finish_tool").assertDoesNotExist()
        compose.onNodeWithTag("signature_apply_adjustments").assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals(1, fixture.applies)
            assertEquals(0, fixture.finishes)
            assertEquals(0, fixture.backs)
            assertEquals("signature-1", fixture.lastAppliedDraft?.id)
        }
        compose.onNodeWithTag("signature_apply_adjustments").assertDoesNotExist()
        compose.onNodeWithTag("signature_finish_tool").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, fixture.applies)
            assertEquals(1, fixture.finishes)
            assertEquals(0, fixture.backs)
        }
    }

    @Test fun pickerCreatesOrInsertsASignatureThenWaitsForPlacement() {
        val fixture = showEditor(withDraft = false)
        openPicker()
        compose.onNodeWithTag("signature_picker_create").assertIsDisplayed().performClick()
        compose.onNodeWithTag("signature_picker_sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(false), fixture.creates) }

        openPicker()
        compose.onNodeWithTag("signature_choice_signature-2").performScrollTo().performClick()
        compose.onNodeWithTag("signature_picker_sheet").assertDoesNotExist()
        compose.onNodeWithTag("signature_cancel_placement").assertIsDisplayed()
        compose.onNodeWithTag("signature_place_center").assertIsDisplayed().performClick()
        compose.onNodeWithTag("signature_open_adjustments").assertIsDisplayed()
        compose.onNodeWithTag("signature_finish_tool").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("signature-2", fixture.events.filterIsInstance<DocumentToolsEvent.InsertSignature>().single().signatureId)
            assertEquals(DocumentToolsEvent.PlaceSignature(.5f, .5f), fixture.events.filterIsInstance<DocumentToolsEvent.PlaceSignature>().single())
            assertFalse(fixture.state.signatureAwaitingPlacement)
            assertEquals(0, fixture.applies)
            assertEquals(0, fixture.finishes)
        }
    }

    @Test fun replacementUsesTheSamePickerAndKeepsTheCurrentPlacement() {
        val fixture = showEditor()
        val original = requireNotNull(fixture.state.signatureDraft)
        performContextAction("replace")
        compose.onNodeWithTag("signature_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("signature_choice_signature-1").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("signature_choice_signature-2").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("signature-2", fixture.events.filterIsInstance<DocumentToolsEvent.ReplaceSignature>().single().signatureId)
            assertEquals(
                original.copy(imageUri = "session-signature-2.png", sourceSignatureId = "signature-2"),
                fixture.state.signatureDraft,
            )
            assertEquals(0, fixture.applies)
        }

        performContextAction("replace")
        compose.onNodeWithTag("signature_choice_signature-2").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("signature_picker_create").performClick()
        compose.onNodeWithTag("signature_picker_sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(true), fixture.creates) }
    }

    @Test fun contextualDuplicateDeleteAndCancelDispatchDistinctActions() {
        val fixture = showEditor()
        performContextAction("duplicate")
        compose.runOnIdle {
            assertEquals(1, fixture.events.count { it is DocumentToolsEvent.DuplicateSignatureDraft })
            assertEquals(0, fixture.applies)
        }
        performContextAction("delete")
        compose.onNodeWithTag("signature_context_toolbar").assertDoesNotExist()
        compose.onNodeWithTag("signature_finish_tool").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, fixture.events.count { it is DocumentToolsEvent.DeleteSignatureDraft })
            assertEquals(0, fixture.events.count { it is DocumentToolsEvent.CancelSignature })
        }

        openPicker()
        compose.onNodeWithTag("signature_choice_signature-1").performScrollTo().performClick()
        compose.onNodeWithTag("signature_place_center").performClick()
        performContextAction("cancel")
        compose.onNodeWithTag("signature_context_toolbar").assertDoesNotExist()
        compose.onNodeWithTag("signature_finish_tool").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, fixture.events.count { it is DocumentToolsEvent.CancelSignature })
            assertEquals(0, fixture.applies)
            assertEquals(0, fixture.finishes)
        }
    }

    @Test fun adjustmentTabsUpdateTheDraftAndClosingTheSheetKeepsChanges() {
        val fixture = showEditor()
        openAdjustments()
        compose.onNodeWithTag("signature_adjustment_MOVE").assertIsSelected()
        val actions = compose.onNodeWithTag("signature_position_pad")
            .performScrollTo().assertIsEnabled().fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.first().action()) }
        compose.runOnIdle {
            val moved = fixture.events.filterIsInstance<DocumentToolsEvent.TransformSignature>().last()
            assertEquals(0f, moved.x, .001f)
            assertEquals(.5f, moved.y, .001f)
        }

        selectAdjustment("SIZE")
        setProgress("signature_size_slider", .55f)
        selectAdjustment("ROTATE")
        setProgress("signature_rotation_dial", 37f)
        compose.runOnIdle {
            val changed = fixture.events.filterIsInstance<DocumentToolsEvent.TransformSignature>().last()
            assertEquals(.55f, changed.width, .001f)
            assertEquals(37f, changed.rotationDegrees, .001f)
            assertEquals(0, fixture.applies)
            assertTrue(fixture.events.none { it is DocumentToolsEvent.ConfirmSignature })
        }

        captureScreen("signature-adjustments.png")
        compose.onNodeWithTag("signature_close_adjustments").assertIsDisplayed().performClick()
        compose.onNodeWithTag("signature_adjustment_sheet").assertDoesNotExist()
        openAdjustments()
        compose.onNodeWithTag("signature_adjustment_ROTATE").assertIsSelected()
        assertProgress("signature_rotation_dial", 37f)
        selectAdjustment("SIZE")
        assertProgress("signature_size_slider", .55f)
        applyFromSheet()
        compose.onNodeWithTag("signature_adjustment_sheet").assertDoesNotExist()
        compose.onNodeWithTag("signature_finish_tool").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, fixture.applies)
            assertEquals(.55f, requireNotNull(fixture.lastAppliedDraft).width, .001f)
            assertEquals(37f, requireNotNull(fixture.lastAppliedDraft).rotationDegrees, .001f)
            assertEquals(0, fixture.finishes)
        }
    }

    @Test fun largeFontsKeepEveryAdjustmentAndSheetActionReachable() {
        val fixture = showEditor(fontScale = 2f)
        openAdjustments()
        compose.onNodeWithTag("signature_close_adjustments").assertIsDisplayed()
        listOf("MOVE", "SIZE", "ROTATE").forEach { selectAdjustment(it) }
        captureScreen("signature-adjustments-large-font.png")
        compose.onNodeWithTag("signature_cancel_edit").assertIsDisplayed().assertIsEnabled()
        setProgress("signature_rotation_dial", -23f)
        applyFromSheet()
        compose.onNodeWithTag("signature_finish_tool").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, fixture.applies)
            assertEquals(-23f, requireNotNull(fixture.lastAppliedDraft).rotationDegrees, .001f)
        }
    }

    @Test fun cropOpensFromTheContextToolbarAndCancellationKeepsTheDraft() {
        val fixture = showEditor()
        val original = fixture.state.signatureDraft
        performContextAction("crop")
        compose.onNodeWithTag("signature_crop_sheet").assertIsDisplayed()
        compose.onNodeWithTag("signature_crop_cancel").assertIsDisplayed().performClick()
        compose.onNodeWithTag("signature_crop_sheet").assertDoesNotExist()
        compose.onNodeWithTag("signature_context_toolbar").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(original, fixture.state.signatureDraft)
            assertTrue(fixture.events.none { it is DocumentToolsEvent.CropSignature })
            assertEquals(0, fixture.applies)
        }
    }

    @Test fun shortSheetStaysAtTheWindowBottomAndScrollingKeepsTabsAndFooterVisible() {
        val fixture = Fixture(withDraft = true)
        var visible by mutableStateOf(true)
        var mode by mutableStateOf(SignatureAdjustmentMode.ROTATE)
        var adjustmentActive by mutableStateOf(false)
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme {
                    Button(
                        onClick = { visible = true },
                        modifier = Modifier.testTag("test_open_signature_sheet"),
                    ) { Text("Open") }
                    if (visible) {
                        DocumentSignatureControls(
                            draft = requireNotNull(fixture.state.signatureDraft),
                            mode = mode,
                            enabled = true,
                            applying = false,
                            adjustmentActive = adjustmentActive,
                            maxHeight = 280.dp,
                            onModeChange = { mode = it },
                            onEvent = fixture::onEvent,
                            onDismiss = { visible = false },
                            onCancel = { visible = false },
                            onApply = {
                                fixture.applies++
                                fixture.lastAppliedDraft = fixture.state.signatureDraft
                                visible = false
                            },
                            onHeightChange = {},
                            onAdjustmentActiveChange = { adjustmentActive = it },
                        )
                    }
                }
            }
        }

        captureScreen("signature-sheet-short.png")
        assertSheetAtWindowBottom()
        val initialTabs = compose.onNodeWithTag("signature_adjustment_tabs")
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val initialFooter = compose.onNodeWithTag("signature_adjustment_footer")
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val initialScroll = compose.onNodeWithTag("signature_adjustment_body")
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag("signature_rotation_reset").performScrollTo().assertIsDisplayed()
        val scrolled = compose.onNodeWithTag("signature_adjustment_body")
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("The constrained body must scroll to reach its last control", scrolled > initialScroll)
        assertSheetAtWindowBottom()
        val scrolledTabs = compose.onNodeWithTag("signature_adjustment_tabs")
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val scrolledFooter = compose.onNodeWithTag("signature_adjustment_footer")
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertEquals(initialTabs.top, scrolledTabs.top, 1f)
        assertEquals(initialTabs.bottom, scrolledTabs.bottom, 1f)
        assertEquals(initialFooter.top, scrolledFooter.top, 1f)
        assertEquals(initialFooter.bottom, scrolledFooter.bottom, 1f)

        selectAdjustment("SIZE")
        setProgress("signature_size_slider", .52f)
        selectAdjustment("MOVE")
        compose.onNodeWithTag("signature_position_center").performScrollTo().assertIsDisplayed()
        selectAdjustment("ROTATE")
        setProgress("signature_rotation_dial", 21f)
        assertSheetAtWindowBottom()
        compose.onNodeWithTag("signature_close_adjustments").assertIsDisplayed().performClick()
        compose.onNodeWithTag("signature_adjustment_modal").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, fixture.applies)
            assertEquals(.52f, requireNotNull(fixture.state.signatureDraft).width, .001f)
            assertEquals(21f, requireNotNull(fixture.state.signatureDraft).rotationDegrees, .001f)
        }

        compose.onNodeWithTag("test_open_signature_sheet").assertIsDisplayed().performClick()
        compose.onNodeWithTag("signature_adjustment_ROTATE").assertIsSelected()
        assertProgress("signature_rotation_dial", 21f)
        assertSheetAtWindowBottom()
        applyFromSheet()
        compose.runOnIdle { assertEquals(1, fixture.applies) }
        compose.onNodeWithTag("signature_adjustment_modal").assertDoesNotExist()
    }

    private fun assertSheetAtWindowBottom() {
        val sheet = compose.onNodeWithTag("signature_adjustment_sheet")
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val (windowBottom, navigationInset) = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            decor.height.toFloat() to (
                ViewCompat.getRootWindowInsets(decor)
                    ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            ).toFloat()
        }
        assertEquals(
            "Sheet content must reach the navigation inset: sheet=$sheet, window=$windowBottom, navigation=$navigationInset",
            windowBottom - navigationInset,
            sheet.bottom,
            1f,
        )
        val tabs = compose.onNodeWithTag("signature_adjustment_tabs")
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val footer = compose.onNodeWithTag("signature_adjustment_footer")
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue(tabs.top >= sheet.top - 1f)
        assertTrue(tabs.bottom <= footer.top + 1f)
        assertTrue(footer.bottom <= sheet.bottom + 1f)
        val close = compose.onNodeWithTag("signature_close_adjustments")
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue(close.top >= 0f)
        assertTrue(close.bottom <= sheet.top + 1f)
        compose.onNodeWithTag("signature_cancel_edit").assertIsDisplayed().assertIsEnabled()
        listOf("MOVE", "SIZE", "ROTATE").forEach { control ->
            compose.onNodeWithTag("signature_adjustment_$control").assertIsDisplayed()
        }
    }

    private fun captureScreen(fileName: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
            File(directory, fileName).outputStream().use { output ->
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun openPicker() {
        compose.onNodeWithTag("signature_open_picker").assertIsEnabled().performClick()
        compose.onNodeWithTag("signature_picker_sheet").assertIsDisplayed()
    }

    private fun performContextAction(action: String) {
        compose.onNodeWithTag("signature_context_toolbar").assertIsDisplayed()
        compose.onNodeWithTag("signature_action_$action").assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun openAdjustments() {
        performContextAction("adjust")
        compose.onNodeWithTag("signature_adjustment_sheet").assertIsDisplayed()
    }

    private fun selectAdjustment(mode: String) {
        compose.onNodeWithTag("signature_adjustment_$mode")
            .assertIsDisplayed().performClick().assertIsSelected()
    }

    private fun applyFromSheet() {
        compose.onNode(
            hasTestTag("signature_apply_adjustments") and
                hasAnyAncestor(hasTestTag("signature_adjustment_sheet")),
        ).assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun setProgress(tag: String, value: Float) {
        compose.onNodeWithTag(tag).performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(value)) }
    }

    private fun assertProgress(tag: String, value: Float) {
        compose.onNodeWithTag(tag).performScrollTo().assert(
            SemanticsMatcher("Progress is $value") {
                val current = it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current
                current != null && abs(current - value) < .001f
            },
        )
    }

    private fun showEditor(withDraft: Boolean = true, fontScale: Float = 1f): Fixture {
        val fixture = Fixture(withDraft)
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalDensity provides Density(density, fontScale),
            ) {
                ScaniqoTheme {
                    DocumentSignatureScreen(
                        documentId = 1,
                        state = fixture.state,
                        documentName = "Contrato",
                        isLoadingDocument = false,
                        documentError = false,
                        onEvent = fixture::onEvent,
                        onBack = { fixture.backs++ },
                        onCreateSignature = { replacing -> fixture.creates += replacing },
                        onRetry = {},
                        onApply = {
                            fixture.applies++
                            fixture.lastAppliedDraft = fixture.state.signatureDraft
                            fixture.clearDraft()
                        },
                        onDone = { fixture.finishes++ },
                    )
                }
            }
        }
        return fixture
    }

    private class Fixture(withDraft: Boolean) {
        val events = mutableListOf<DocumentToolsEvent>()
        val creates = mutableListOf<Boolean>()
        var applies = 0
        var finishes = 0
        var backs = 0
        var lastAppliedDraft: PdfSignaturePlacement? = null
        private val signatures = listOf(
            SignatureModel("signature-1", "Assinatura pessoal", "signature-one.png", 3f),
            SignatureModel("signature-2", "Rubrica", "signature-two.png", 3f),
        )
        private val page = PdfPageSpec("page", "preview.pdf", 0)
        private val initialDraft = PdfSignaturePlacement(
            id = "signature-1",
            imageUri = "session-copy.png",
            sourceSignatureId = "signature-1",
            x = .2f,
            y = .5f,
            width = .4f,
            height = .1f,
        )
        var state by mutableStateOf(
            DocumentToolsUiState(
                document = DocumentModel(1, "Contrato", "preview.pdf", 1, 0, 1),
                workspace = PdfWorkspace(1, "interaction", "preview", listOf(page)),
                signatures = signatures,
                selectedPageId = page.id,
                signatureDraft = initialDraft.takeIf { withDraft },
                signaturePageId = page.id.takeIf { withDraft },
                pagePreviews = mapOf(
                    page.id to DocumentPagePreviewState(preview = PdfPagePreview("preview.pdf", 0)),
                ),
            ),
        )

        fun clearDraft() {
            state = state.copy(
                signatureDraft = null,
                signaturePageId = null,
                signatureAwaitingPlacement = false,
            )
        }

        fun onEvent(event: DocumentToolsEvent) {
            events += event
            val draft = state.signatureDraft
            when (event) {
                is DocumentToolsEvent.InsertSignature -> {
                    val signature = signatures.single { it.id == event.signatureId }
                    state = state.copy(
                        signatureDraft = initialDraft.copy(
                            id = signature.id,
                            imageUri = "session-${signature.id}.png",
                            sourceSignatureId = signature.id,
                        ),
                        signaturePageId = page.id,
                        signatureAwaitingPlacement = true,
                    )
                }
                is DocumentToolsEvent.PlaceSignature -> if (draft != null) {
                    state = state.copy(
                        signatureDraft = draft.copy(
                            x = event.x - draft.width / 2f,
                            y = event.y - draft.height / 2f,
                        ),
                        signatureAwaitingPlacement = false,
                    )
                }
                is DocumentToolsEvent.ReplaceSignature -> if (draft != null) {
                    state = state.copy(
                        signatureDraft = draft.copy(
                            imageUri = "session-${event.signatureId}.png",
                            sourceSignatureId = event.signatureId,
                        ),
                    )
                }
                is DocumentToolsEvent.TransformSignature -> if (draft != null) {
                    state = state.copy(
                        signatureDraft = draft.copy(
                            x = event.x,
                            y = event.y,
                            width = event.width,
                            height = event.height ?: draft.height,
                            rotationDegrees = event.rotationDegrees,
                            stretchToBounds = event.stretchToBounds ?: draft.stretchToBounds,
                        ),
                    )
                }
                DocumentToolsEvent.DuplicateSignatureDraft -> if (draft != null) {
                    state = state.copy(signatureDraft = draft.copy(id = "${draft.id}-copy"))
                }
                DocumentToolsEvent.DeleteSignatureDraft, DocumentToolsEvent.CancelSignature -> clearDraft()
                else -> Unit
            }
        }
    }
}
