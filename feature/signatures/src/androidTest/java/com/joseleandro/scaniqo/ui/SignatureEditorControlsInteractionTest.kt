package com.joseleandro.scaniqo.ui

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignaturePoint
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import com.joseleandro.scaniqo.ui.signatures.SignatureEditorScreen
import com.joseleandro.scaniqo.ui.signatures.SignatureEditorState
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

class SignatureEditorControlsInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val models = mutableListOf<SignaturesViewModel>()

    @After fun cleanUp() {
        compose.runOnIdle { models.forEach { it.viewModelScope.cancel() } }
    }

    @Test fun editorStartsOnDrawAndExposesFourTabsWithASingleDisabledToolbarSave() {
        val events = mutableListOf<SignaturesEvent>()
        compose.setContent {
            ScaniqoTheme { SignatureEditorScreen(SignatureEditorState(), events::add, {}) }
        }
        compose.onNodeWithTag("signature_tab_draw").assertIsSelected()
        compose.onAllNodesWithTag("signature_save").assertCountEquals(1)
        compose.onNodeWithTag("signature_save").assertIsNotEnabled()
        compose.onNodeWithTag("signature_name").assertDoesNotExist()
        compose.onNodeWithTag("signature_save_dialog").assertDoesNotExist()
        SignatureInput.entries.forEach { input ->
            compose.onNodeWithTag("signature_tab_${input.name.lowercase()}")
                .assertIsDisplayed().performClick()
        }
        compose.runOnIdle {
            assertEquals(SignatureInput.entries.map(SignaturesEvent::InputChanged), events)
        }
    }

    @Test fun nameAppearsOnlyAfterToolbarSaveAndBlankNamesCannotBeConfirmed() {
        val repository = TypedSignatures()
        val model = showTypedEditor(repository)
        compose.onNodeWithTag("signature_name").assertDoesNotExist()
        compose.onAllNodesWithTag("signature_save").assertCountEquals(1)
        compose.onNodeWithTag("signature_save").assertIsEnabled().performClick()
        compose.onNodeWithTag("signature_save_dialog").assertIsDisplayed()
        compose.onNodeWithTag("signature_name").performTextReplacement("   ")
        compose.onNode(hasText(text(CoreResourcesR.string.runtime_save)) and hasAnyAncestor(hasTestTag("signature_save_dialog"))).assertIsNotEnabled()
        compose.runOnIdle { assertTrue(repository.savedNames.isEmpty()) }
        compose.onNodeWithTag("signature_name").performTextReplacement("Personal")
        compose.onNode(hasText(text(CoreResourcesR.string.runtime_save)) and hasAnyAncestor(hasTestTag("signature_save_dialog"))).assertIsEnabled()
        compose.onNodeWithText(text(CoreResourcesR.string.runtime_cancel)).performClick()
        compose.onNodeWithTag("signature_save_dialog").assertDoesNotExist()
        compose.onNodeWithTag("signature_name").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(repository.savedNames.isEmpty())
            assertEquals("Ana Silva", model.uiState.value.editor?.text)
        }
        compose.onNodeWithTag("signature_save").performClick()
        compose.onNode(hasText(text(CoreResourcesR.string.runtime_save)) and hasAnyAncestor(hasTestTag("signature_save_dialog"))).performClick()
        compose.waitUntil(10_000) { model.uiState.value.editor == null }
        compose.runOnIdle { assertEquals(listOf("Personal"), repository.savedNames) }
    }

    @Test fun toolbarSaveRequiresContentAndIsDisabledThroughoutProcessingOrSaving() {
        val state = mutableStateOf(SignatureEditorState())
        compose.setContent { ScaniqoTheme { SignatureEditorScreen(state.value, {}, {}) } }
        compose.onNodeWithTag("signature_save").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(input = SignatureInput.TYPE, text = "   ") }
        compose.onNodeWithTag("signature_save").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(text = "Ana") }
        compose.onNodeWithTag("signature_save").assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(isProcessing = true) }
        compose.onNodeWithTag("signature_save").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(isProcessing = false, isSaving = true) }
        compose.onNodeWithTag("signature_save").assertIsNotEnabled()
        compose.runOnIdle {
            state.value = SignatureEditorState(strokes = listOf(SignatureStroke(
                listOf(SignaturePoint(.1f, .2f), SignaturePoint(.8f, .7f)),
                colorArgb = 0xFF18212B.toInt(), width = .004f,
            )))
        }
        compose.onNodeWithTag("signature_save").assertIsEnabled()
    }

    @Test fun savingDialogBlocksRepeatedConfirmationUntilPersistenceCompletes() {
        val repository = TypedSignatures().apply { saveGate = CompletableDeferred() }
        val model = showTypedEditor(repository)
        compose.onNodeWithTag("signature_save").performClick()
        compose.onNode(hasText(text(CoreResourcesR.string.runtime_save)) and hasAnyAncestor(hasTestTag("signature_save_dialog"))).performClick()
        compose.waitUntil(10_000) { model.uiState.value.editor?.isSaving == true }
        compose.onNodeWithTag("signature_name").assertIsNotEnabled()
        compose.onNodeWithText(text(CoreResourcesR.string.runtime_cancel)).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, repository.saveCalls) }
        repository.saveGate?.complete(Unit)
        compose.waitUntil(10_000) { model.uiState.value.editor == null }
        compose.runOnIdle { assertEquals(1, repository.saveCalls) }
    }

    private fun showTypedEditor(repository: TypedSignatures): SignaturesViewModel {
        lateinit var model: SignaturesViewModel
        compose.runOnIdle {
            model = SignaturesViewModel(repository).also(models::add)
            model.onEvent(SignaturesEvent.Create)
            model.onEvent(SignaturesEvent.InputChanged(SignatureInput.TYPE))
            model.onEvent(SignaturesEvent.TextChanged("Ana Silva"))
        }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ScaniqoTheme {
                state.editor?.let { SignatureEditorScreen(it, model::onEvent, {}) }
            }
        }
        return model
    }

    private fun text(id: Int) = context.getString(id)

    private class TypedSignatures : SignatureRepository {
        val signatures = MutableStateFlow<List<SignatureModel>>(emptyList())
        val savedNames = mutableListOf<String>()
        var saveGate: CompletableDeferred<Unit>? = null
        var saveCalls = 0
        override fun observeSignatures() = signatures
        override suspend fun createTyped(name: String, text: String, font: SignatureFont, colorArgb: Int): SignatureModel {
            saveCalls++
            saveGate?.await()
            savedNames += name
            return SignatureModel("typed", name, "file:///typed.png", 3f).also { signatures.value += it }
        }
        override suspend fun createDrawing(name: String, strokes: List<SignatureStroke>): SignatureModel = error("unused")
        override suspend fun importImage(name: String, uri: String, removeLightBackground: Boolean): SignatureModel = error("unused")
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun delete(id: String) = Unit
    }
}
