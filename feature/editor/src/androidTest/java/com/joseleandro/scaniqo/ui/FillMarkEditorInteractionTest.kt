package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.feature.editor.R

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.SavedFormValue
import com.joseleandro.scaniqo.ui.documentEditor.components.DocumentFillMarkEditor
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.abs

class FillMarkEditorInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val testContexts = mutableListOf<IsolatedPreferencesContext>()

    @After fun clearOnlyTestStylePreferences() {
        testContexts.forEach { it.clear() }
    }

    @Test fun insertMarkSelectsItAndCancellingLiveSizeRestoresTheOriginal() {
        val fixture = showEditor()
        insert("mark")
        captureScreen("fill-context.png")
        compose.onNodeWithTag("fill_selection_handle_4").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(fixture.hasDraft)
            assertTrue(fixture.puts.isEmpty())
        }

        openProperty("size")
        assertProgress("fill_size_slider", .04f)
        setProgress("fill_size_slider", .12f)
        assertProgress("fill_size_slider", .12f)
        compose.runOnIdle { assertTrue(fixture.puts.isEmpty()) }
        closeProperty("SIZE", apply = false)

        openProperty("size")
        assertProgress("fill_size_slider", .04f)
        closeProperty("SIZE", apply = true)
        finish()

        compose.runOnIdle {
            val event = fixture.puts.single()
            assertEquals(PdfOverlayKind.CHECKMARK, event.overlay.kind)
            assertEquals(.04f, event.overlay.width, .001f)
            assertEquals(.03f, event.overlay.height, .001f)
            assertEquals(setOf("page-1"), event.pageIds)
            assertEquals(1, fixture.finishes)
            assertFalse(fixture.hasDraft)
        }
    }

    @Test fun cancellingAnotherPropertyKeepsThePreviouslyAppliedSize() {
        val fixture = showEditor()
        insert("mark")
        openProperty("size")
        setProgress("fill_size_slider", .14f)
        closeProperty("SIZE", apply = true)

        openProperty("color")
        compose.onNodeWithContentDescription(string(R.string.overlay_blue))
            .performScrollTo().assertIsEnabled().performClick().assertIsSelected()
        closeProperty("COLOR", apply = false)
        openProperty("size")
        assertProgress("fill_size_slider", .14f)
        closeProperty("SIZE", apply = true)
        compose.runOnIdle { assertTrue(fixture.puts.isEmpty()) }
        finish()

        compose.runOnIdle {
            val overlay = fixture.puts.single().overlay
            assertEquals(.14f, overlay.width, .001f)
            assertEquals(.105f, overlay.height, .001f)
            assertEquals(0xFF202124.toInt(), overlay.color)
        }
    }

    @Test fun duplicateAndDeleteKeepTheOriginalUntilItIsExplicitlyDeleted() {
        val fixture = showEditor()
        insert("mark")
        openMoreAction("duplicate")
        compose.onNodeWithTag("fill_context_toolbar").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, fixture.puts.size)
            assertEquals(1, fixture.pages.first().overlays.size)
        }
        openMoreAction("delete")
        compose.onNodeWithTag("fill_context_toolbar").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, fixture.pages.first().overlays.size)
            assertTrue(fixture.events.none { it is DocumentToolsEvent.RemoveOverlay })
        }

        compose.onNodeWithTag("fill_insert").performClick()
        compose.onNodeWithTag("fill_select_element").assertIsDisplayed().performClick()
        compose.onNodeWithText(string(R.string.overlay_checkmark)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(fixture.puts.single().overlay.id, fixture.state.overlayPreviewId)
        }
        openMoreAction("delete")
        compose.runOnIdle {
            val removed = fixture.events.filterIsInstance<DocumentToolsEvent.RemoveOverlay>().single()
            assertEquals(fixture.puts.single().overlay.id, removed.overlayId)
            assertEquals(setOf("page-1"), removed.pageIds)
            assertTrue(fixture.pages.all { it.overlays.isEmpty() })
        }
        finish()
        compose.runOnIdle {
            assertEquals(1, fixture.finishes)
            assertEquals(1, fixture.puts.size)
        }
    }

    @Test fun allPagesScopeSurvivesCancellingAFollowingScopeEdit() {
        val fixture = showEditor()
        insert("mark")
        openMoreAction("scope")
        scopeOption(allPages = false).assertIsSelected()
        scopeOption(allPages = true).performClick().assertIsSelected()
        closeProperty("SCOPE", apply = true)

        openMoreAction("scope")
        scopeOption(allPages = true).assertIsSelected()
        scopeOption(allPages = false).performClick()
        closeProperty("SCOPE", apply = false)
        openMoreAction("scope")
        scopeOption(allPages = true).assertIsSelected()
        closeProperty("SCOPE", apply = true)
        finish()

        compose.runOnIdle {
            val event = fixture.puts.single()
            assertEquals(setOf("page-1", "page-2", "page-3"), event.pageIds)
            assertTrue(fixture.pages.all { it.overlays.single() == event.overlay })
        }
    }

    @Test fun transformTabsPreviewChangesAndCancelRestoresTheAppliedTransform() {
        val fixture = showEditor()
        insert("mark")
        openMoreAction("transform")
        compose.onNodeWithTag("fill_transform_sheet").assertIsDisplayed()
        compose.onNodeWithTag("fill_transform_MOVE").assertIsSelected()
        val actions = compose.onNodeWithTag("fill_position_pad").performScrollTo()
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions[2].action()) }
        selectTransform("SIZE")
        setProgress("fill_size_slider", .16f)
        selectTransform("ROTATE")
        setProgress("fill_rotation_dial", 37f)
        assertProgress("fill_rotation_dial", 37f)
        compose.runOnIdle { assertTrue(fixture.puts.isEmpty()) }
        compose.onNodeWithTag("fill_transform_apply").assertIsDisplayed().performClick()

        openMoreAction("transform")
        selectTransform("ROTATE")
        assertProgress("fill_rotation_dial", 37f)
        setProgress("fill_rotation_dial", -52f)
        compose.onNodeWithTag("fill_transform_cancel").assertIsDisplayed().performClick()
        openMoreAction("transform")
        selectTransform("ROTATE")
        assertProgress("fill_rotation_dial", 37f)
        selectTransform("SIZE")
        assertProgress("fill_size_slider", .16f)
        compose.onNodeWithTag("fill_transform_apply").performClick()
        finish()

        compose.runOnIdle {
            val overlay = fixture.puts.single().overlay
            assertEquals(.16f, overlay.width, .001f)
            assertEquals(37f, overlay.rotationDegrees, .001f)
            assertTrue(overlay.isValid())
            assertTrue(overlay.x > .5f)
        }
    }

    @Test fun restoringAnOpenDialogKeepsTheDraftAndItsCancelBaseline() {
        val restoration = StateRestorationTester(compose)
        val fixture = showEditor(restoration)
        insert("mark")
        openProperty("size")
        setProgress("fill_size_slider", .15f)

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("fill_property_dialog_SIZE").assertIsDisplayed()
        assertProgress("fill_size_slider", .15f)
        closeProperty("SIZE", apply = false)
        openProperty("size")
        assertProgress("fill_size_slider", .04f)
        closeProperty("SIZE", apply = true)
        finish()

        compose.runOnIdle {
            assertEquals(.04f, fixture.puts.single().overlay.width, .001f)
            assertEquals(1, fixture.finishes)
        }
    }

    @Test fun insertedTextKeepsItsContentWhenAStyleEditIsCancelled() {
        val fixture = showEditor()
        insert("text")
        compose.onNode(hasSetTextAction()).performTextReplacement("Cliente fictício")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithTag("fill_action_edit").assertIsDisplayed()
        openMoreAction("style")
        compose.onNodeWithText(string(R.string.fill_v4_bold))
            .performScrollTo().performClick().assertIsSelected()
        closeProperty("STYLE", apply = true)
        openMoreAction("style")
        compose.onNodeWithText(string(R.string.fill_v4_italic))
            .performScrollTo().performClick()
        closeProperty("STYLE", apply = false)
        finish()

        compose.runOnIdle {
            val overlay = fixture.puts.single().overlay
            assertEquals(PdfOverlayKind.TEXT, overlay.kind)
            assertEquals("Cliente fictício", overlay.text)
            assertTrue(overlay.bold)
            assertFalse(overlay.italic)
        }
    }

    @Test fun savedValueStartsWithItsTextAndTypingAppendsWithoutLosingIt() {
        val fixture = showEditor(withSavedValue = true)
        compose.onNodeWithTag("fill_insert").performClick()
        compose.onNodeWithTag("fill_insert_saved").assertIsDisplayed().performClick()
        compose.onNodeWithText("Nome de teste").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("Cliente fictício")
            .performTextInput(" - revisão")
        compose.onNode(hasSetTextAction()).performImeAction()
        finish()

        compose.runOnIdle {
            val overlay = fixture.puts.single().overlay
            assertEquals(PdfOverlayKind.TEXT, overlay.kind)
            assertEquals("Cliente fictício - revisão", overlay.text)
            assertEquals(setOf("page-1"), fixture.puts.single().pageIds)
            assertEquals(1, fixture.finishes)
        }
    }

    private fun insert(kind: String) {
        compose.onNodeWithTag("fill_insert").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("fill_insert_sheet").assertIsDisplayed()
        compose.onNodeWithTag("fill_insert_$kind").assertIsDisplayed().performClick()
        compose.onNodeWithTag("fill_insert_sheet").assertDoesNotExist()
        if (kind != "text") compose.onNodeWithTag("fill_context_toolbar").assertIsDisplayed()
    }

    private fun openProperty(action: String) {
        compose.onNodeWithTag("fill_action_$action").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("fill_property_dialog_${action.uppercase()}").assertIsDisplayed()
    }

    private fun openMoreAction(action: String) {
        compose.onNodeWithTag("fill_action_more").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("fill_action_$action").performScrollTo().assertIsEnabled().performClick()
    }

    private fun closeProperty(property: String, apply: Boolean) {
        compose.onNode(
            hasText(string(if (apply) R.string.fill_mark_apply else R.string.tools_cancel)) and
                hasAnyAncestor(hasTestTag("fill_property_dialog_$property")),
        ).assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag("fill_property_dialog_$property").assertDoesNotExist()
    }

    private fun scopeOption(allPages: Boolean) = compose.onNode(
        hasText(string(if (allPages) R.string.fill_mark_every_page else R.string.fill_mark_this_page)) and
            hasAnyAncestor(hasTestTag("fill_property_dialog_SCOPE")),
    )

    private fun selectTransform(tab: String) {
        compose.onNodeWithTag("fill_transform_$tab").assertIsDisplayed()
            .performClick().assertIsSelected()
    }

    private fun finish() {
        compose.onNodeWithTag("fill_finish").assertIsDisplayed().assertIsEnabled().performClick()
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

    private fun string(id: Int): String = compose.activity.getString(id)

    private fun captureScreen(fileName: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), fileName)
                .outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun showEditor(restoration: StateRestorationTester? = null, withSavedValue: Boolean = false): Fixture {
        val fixture = Fixture(withSavedValue)
        val context = IsolatedPreferencesContext(compose.activity)
        testContexts += context
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalInspectionMode provides true, LocalContext provides context) {
                ScaniqoTheme {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                        DocumentFillMarkEditor(
                            state = fixture.state,
                            tool = DocumentTool.FORMS,
                            onEvent = fixture::onEvent,
                            onDraftChanged = { fixture.hasDraft = it },
                            onBack = { fixture.backs++ },
                            onFinish = { fixture.finishes++ },
                        )
                    }
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
        return fixture
    }

    /** The fixture never reads or changes the user's remembered styles. */
    private class IsolatedPreferencesContext(base: Context) : ContextWrapper(base) {
        private val namespace = "fill-editor-test-${UUID.randomUUID()}-"
        private val preferences = mutableSetOf<SharedPreferences>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(namespace + name, mode).also { preferences += it }

        fun clear() {
            preferences.forEach { it.edit().clear().commit() }
        }
    }

    private class Fixture(withSavedValue: Boolean) {
        val events = mutableListOf<DocumentToolsEvent>()
        val puts get() = events.filterIsInstance<DocumentToolsEvent.PutOverlay>()
        var hasDraft = false
        var finishes = 0
        var backs = 0
        private val initialPages = (1..3).map { PdfPageSpec("page-$it", "fixture.pdf", it - 1) }
        val pages get() = requireNotNull(state.workspace).pages
        var state by mutableStateOf(
            DocumentToolsUiState(
                document = DocumentModel(1, "Formulário de teste", "fixture.pdf", 3, 0, 1),
                workspace = PdfWorkspace(1, "fill-interaction", "fixture", initialPages),
                selectedPageId = initialPages.first().id,
                canStoreFormValues = withSavedValue,
                savedFormValues = if (withSavedValue) listOf(
                    SavedFormValue("fixture-value", "Nome de teste", "Cliente fictício"),
                ) else emptyList(),
                pagePreviews = initialPages.associate {
                    it.id to DocumentPagePreviewState(
                        preview = PdfPagePreview("fixture.pdf", it.sourcePageIndex),
                        // The inspection preview is a blank base; the real stage draws every overlay.
                        renderedOverlayIds = emptySet(),
                    )
                },
            ),
        )

        fun onEvent(event: DocumentToolsEvent) {
            events += event
            when (event) {
                is DocumentToolsEvent.PutOverlay -> updatePages { page ->
                    if (page.id in event.pageIds) page.copy(
                        overlays = page.overlays.filterNot { it.id == event.overlay.id } + event.overlay,
                    ) else page
                }
                is DocumentToolsEvent.RemoveOverlay -> updatePages { page ->
                    if (page.id in event.pageIds) page.copy(
                        overlays = page.overlays.filterNot { it.id == event.overlayId },
                    ) else page
                }
                is DocumentToolsEvent.SetOverlayPreview -> {
                    state = state.copy(
                        overlayPreviewPageId = event.pageId,
                        overlayPreviewId = event.overlayId,
                        pagePreviews = state.pagePreviews.mapValues { (_, preview) ->
                            preview.copy(renderedOverlayIds = emptySet())
                        },
                    )
                }
                is DocumentToolsEvent.SelectPage -> state = state.copy(selectedPageId = event.pageId)
                else -> Unit
            }
        }

        private fun updatePages(transform: (PdfPageSpec) -> PdfPageSpec) {
            val workspace = requireNotNull(state.workspace)
            state = state.copy(workspace = workspace.copy(pages = workspace.pages.map(transform)), hasChanges = true)
        }
    }
}
