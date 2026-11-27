package com.joseleandro.scaniqo.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.core.localization.bidiArgument
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.TagModel
import com.joseleandro.scaniqo.domain.model.TagColor
import com.joseleandro.scaniqo.ui.components.labelRes
import com.joseleandro.scaniqo.feature.library.R
import com.joseleandro.scaniqo.ui.tags.TagEditorContent
import com.joseleandro.scaniqo.ui.tags.TagEditorScreen
import com.joseleandro.scaniqo.ui.tags.TagEditorUiState
import com.joseleandro.scaniqo.ui.tags.TagNameError
import com.joseleandro.scaniqo.ui.tags.TagsEvent
import com.joseleandro.scaniqo.ui.tags.TagsScreen
import com.joseleandro.scaniqo.ui.tags.TagsUiState
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.ui.trash.TrashScreen
import com.joseleandro.scaniqo.ui.trash.TrashUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

/** Isolated library activity and in-memory state; never opens or clears the installed app's data. */
class TagsTrashInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tagListOpensEditorFromCardAndOverflowWithoutConfusingCreate() {
        val tag = TagModel(id = 7, name = "Receipts", colorArgb = 0xFF2B6F91.toInt(), iconKey = "document")
        var created = 0
        val edited = mutableListOf<Long>()
        compose.setContent {
            ScaniqoTheme(dynamicColor = false) {
                TagsScreen(
                    onNavigationBack = {},
                    uiState = TagsUiState(tags = listOf(tag), isLoading = false),
                    onEvent = {},
                    onCreateTag = { created++ },
                    onEditTag = edited::add,
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.nova_tag)).performClick()
        compose.onNodeWithText(tag.name).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(tag.id), edited) }
        compose.onNodeWithContentDescription(
            compose.activity.getString(R.string.tag_actions, bidiArgument(tag.name, compose.activity.resources.configuration.locales[0])),
        ).performClick()
        compose.onNodeWithText(text(CoreResourcesR.string.editar)).performClick()
        compose.runOnIdle {
            assertEquals(1, created)
            assertEquals(listOf(tag.id, tag.id), edited)
        }
    }

    @Test
    fun openingTagEditorOnlyReportsMissingTagsAfterAnExplicitLookupResult() {
        val tagId = mutableStateOf<Long?>(null)
        val state = mutableStateOf(TagsUiState(isLoading = false))
        compose.setContent {
            ScaniqoTheme(dynamicColor = false) {
                TagEditorContent(
                    tagId = tagId.value,
                    state = state.value,
                    onBack = {},
                    onEvent = {},
                )
            }
        }

        // The list may already be loaded while the destination has not initialized its editor.
        compose.onNodeWithTag("tag_editor_loading").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tag_unavailable)).assertDoesNotExist()

        compose.runOnIdle { tagId.value = 7L }
        compose.onNodeWithTag("tag_editor_loading").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tag_unavailable)).assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(editorTagNotFound = true) }
        compose.onNodeWithText(text(R.string.tag_unavailable)).assertIsDisplayed()
        compose.onNodeWithTag("tag_editor_loading").assertDoesNotExist()

        // Creating a tag never describes the new, unsaved tag as missing.
        compose.runOnIdle { tagId.value = null }
        compose.onNodeWithText(text(R.string.tag_unavailable)).assertDoesNotExist()
        compose.onNodeWithTag("tag_editor_loading").assertIsDisplayed()

        compose.runOnIdle {
            state.value = TagsUiState(isLoading = false, editor = TagEditorUiState())
        }
        compose.onNodeWithTag("tag_editor_name").assertIsDisplayed()
        compose.onNodeWithTag("tag_editor_save").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tag_unavailable)).assertDoesNotExist()
        compose.onNodeWithTag("tag_editor_loading").assertDoesNotExist()
    }

    @Test
    fun fullScreenTagFormKeepsSaveAccessibleAfterScrollingAndResizing() {
        val editor = mutableStateOf(TagEditorUiState())
        val size = mutableStateOf(DpSize(320.dp, 640.dp))
        val events = mutableListOf<TagsEvent>()
        var backs = 0
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    ScaniqoTheme(dynamicColor = false) {
                        TagEditorScreen(
                            editor = editor.value,
                            onBack = { backs++ },
                            onEvent = {
                                events += it
                                if (it is TagsEvent.NameChanged) editor.value = editor.value.copy(name = it.name)
                            },
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("tag_editor_name").performTextReplacement("Receipts")
        compose.onNodeWithTag("tag_editor_form")
            .performScrollToNode(hasText(text(R.string.tag_color_title)))
        compose.onNodeWithTag("tag_editor_save").assertIsDisplayed().performClick()
        compose.runOnIdle { editor.value = editor.value.copy(saveError = true) }
        compose.onNodeWithText(text(R.string.tag_save_error)).assertIsDisplayed()
        compose.runOnIdle { editor.value = editor.value.copy(saveError = false, nameError = TagNameError.REQUIRED) }
        compose.onNodeWithText(text(R.string.tag_name_required)).assertIsDisplayed()
        compose.runOnIdle { size.value = DpSize(1280.dp, 800.dp) }
        compose.onNodeWithTag("tag_editor_save").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.icone_de_voltar)).performClick()
        compose.runOnIdle {
            assertEquals("Receipts", editor.value.name)
            assertEquals(1, events.count { it == TagsEvent.SaveTag })
            assertEquals(1, backs)
            editor.value = editor.value.copy(isSaving = true)
        }
        compose.onNodeWithTag("tag_editor_save").assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.icone_de_voltar)).assertIsNotEnabled()
    }

    @Test
    fun customTagColorRemainsAvailableAfterChangingColorAndRestoringTheForm() {
        val originalArgb = 0xFF2B6F91.toInt()
        val editor = mutableStateOf(
            TagEditorUiState(id = 7, name = "Receipts", color = Color(originalArgb)),
        )
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    ScaniqoTheme(dynamicColor = false) {
                        TagEditorScreen(
                            editor = editor.value,
                            onBack = {},
                            onEvent = {
                                if (it is TagsEvent.ColorChanged) editor.value = editor.value.copy(color = it.color)
                            },
                        )
                    }
                }
            }
        }
        val originalLabel = text(R.string.tag_original_color)
        compose.onNodeWithTag("tag_editor_form")
            .performScrollToNode(hasContentDescription(originalLabel))
        val original = compose.onNodeWithContentDescription(originalLabel)
        original.assertIsDisplayed().assertIsSelected()
        val originalBounds = original.fetchSemanticsNode().boundsInRoot

        compose.onNodeWithContentDescription(text(TagColor.GRAPHITE.labelRes))
            .assertIsDisplayed().performClick().assertIsSelected()
        original.assertIsDisplayed().assertIsNotSelected()
        assertEquals("Changing selection must not move the original color", originalBounds,
            original.fetchSemanticsNode().boundsInRoot)

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("tag_editor_form")
            .performScrollToNode(hasContentDescription(originalLabel))
        original.assertIsDisplayed().assertIsNotSelected().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(originalArgb, editor.value.color.toArgb()) }
    }

    @Test
    fun trashMenuGroupsActionsAndPermanentDeletionStillRequiresConfirmation() {
        val document = DocumentModel(
            id = 1, name = "Contract", pdfUri = "preview.pdf", pageCount = 2,
            createdAt = 1_790_000_000_000L, sizeBytes = 2048, deletedAt = 1_790_000_000_000L,
        )
        val state = mutableStateOf(TrashUiState(isLoading = false, documents = listOf(document)))
        var restoredAll = 0
        var deletedAll = 0
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScaniqoTheme(dynamicColor = false) {
                    TrashScreen(
                        state = state.value, onBack = {}, onRetry = {}, onRestore = {},
                        onRequestDeletion = {}, onConfirmDeletion = {}, onDismissDeletion = {},
                        onRestoreAll = { restoredAll++ },
                        onRequestClearAll = {
                            state.value = state.value.copy(pendingClearAll = true, pendingClearAllCount = 1)
                        },
                        onConfirmClearAll = { deletedAll++ },
                        onDismissClearAll = { state.value = state.value.copy(pendingClearAll = false) },
                    )
                }
            }
        }
        compose.onNodeWithText(text(R.string.trash_clear_all)).assertDoesNotExist()
        openTrashMenu()
        compose.onNodeWithText(text(R.string.trash_information)).performClick()
        compose.onNodeWithText(text(R.string.trash_description)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.trash_help_close)).performClick()
        openTrashMenu()
        compose.onNodeWithText(text(R.string.trash_restore_all)).performClick()
        compose.runOnIdle { assertEquals(1, restoredAll) }
        openTrashMenu()
        compose.onNodeWithText(text(R.string.trash_clear_all)).performClick()
        compose.onNodeWithText(text(R.string.trash_clear_all_title)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deletedAll) }
        compose.onNodeWithText(text(CoreResourcesR.string.document_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, deletedAll) }
        openTrashMenu()
        compose.onNodeWithText(text(R.string.trash_clear_all)).performClick()
        compose.onNodeWithText(text(R.string.trash_clear_all)).performClick()
        compose.runOnIdle { assertEquals(1, deletedAll) }
    }

    @Test
    fun emptyTrashDisablesBatchActionsButKeepsInformationAvailable() {
        compose.setContent {
            ScaniqoTheme(dynamicColor = false) {
                TrashScreen(
                    state = TrashUiState(isLoading = false), onBack = {}, onRetry = {}, onRestore = {},
                    onRequestDeletion = {}, onConfirmDeletion = {}, onDismissDeletion = {},
                    onRequestClearAll = {}, onConfirmClearAll = {}, onDismissClearAll = {},
                )
            }
        }
        openTrashMenu()
        compose.onNodeWithText(text(R.string.trash_clear_all)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.trash_restore_all)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.trash_information)).performClick()
        compose.onNodeWithText(text(R.string.trash_help_title)).assertIsDisplayed()
    }

    private fun openTrashMenu() {
        compose.onNodeWithContentDescription(text(R.string.trash_actions)).performClick()
    }

    private fun text(resource: Int) = compose.activity.getString(resource)
}
