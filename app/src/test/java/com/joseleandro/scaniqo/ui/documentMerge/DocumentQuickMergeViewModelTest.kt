package com.joseleandro.scaniqo.ui.documentMerge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultState
import com.joseleandro.scaniqo.core.security.PasswordVersionMissingException
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfMergePasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.ui.documentCommon.FakeDocumentRepository
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentQuickMergeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val savedState = SavedStateHandle()
    private val editor = FakeEditor()
    private val documents = FakeDocumentRepository()
    private val passwords = FakePasswords()
    private val viewModel by lazy {
        DocumentQuickMergeViewModel(editor, documents, passwords, savedState)
            .also { store.put("merge", it) }
    }
    private val first = DocumentMergeItem("content://first", "Primeiro.pdf")
    private val second = DocumentMergeItem("content://second", "Segundo.pdf")
    private val selection = listOf(first, second)

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun mergeUsesTheRequestedOrderAndCreatesADraftWithoutChangingOriginalDocuments() = runTest(dispatcher) {
        val original = DocumentModel(7, "Original", "file:///original.pdf", 3, 1, 1024)
        documents.documents.value = listOf(original)
        val chosen = listOf(second, original.toMergeItem(), first)
        viewModel.merge(chosen)
        viewModel.merge(selection)
        runCurrent()

        assertEquals(chosen.map { it.uri }, editor.requests.single().map { it.uri })
        assertEquals(editor.result, viewModel.uiState.value.pending)
        assertFalse(viewModel.uiState.value.isWorking)
        assertEquals(listOf(original), documents.documents.value)
        assertTrue(documents.created.isEmpty())
        assertTrue(documents.updated.isEmpty())
        assertTrue(documents.deleted.isEmpty())
        assertEquals(0, editor.saveCalls)
        assertNull(viewModel.uiState.value.pending!!.protectionKeyId)
    }

    @Test
    fun invalidSelectionsAreIgnoredAndRepeatedUrisAreMergedOnlyOnce() = runTest(dispatcher) {
        viewModel.merge(listOf(first, first.copy(name = "Duplicado")))
        runCurrent()
        assertFalse(viewModel.uiState.value.isActive)
        assertTrue(editor.requests.isEmpty())

        viewModel.merge(listOf(first, first, second))
        runCurrent()
        assertEquals(listOf(first.uri, second.uri), editor.requests.single().map { it.uri })
    }

    @Test
    fun separatePasswordsCanBeRetriedInOrderWithoutLosingEarlierValidCredentials() = runTest(dispatcher) {
        editor.merge = { sources ->
            if (sources[0].password != "first-secret") throw PdfMergePasswordRequiredException(first.uri)
            if (sources[1].password != "second-secret") throw PdfMergePasswordRequiredException(second.uri)
            editor.result
        }
        viewModel.merge(selection)
        runCurrent()
        assertEquals(first.name, viewModel.uiState.value.passwordRequest!!.name)
        assertFalse(viewModel.uiState.value.passwordRequest!!.incorrect)

        viewModel.submitPassword("wrong")
        runCurrent()
        assertEquals(first.uri, viewModel.uiState.value.passwordRequest!!.uri)
        assertTrue(viewModel.uiState.value.passwordRequest!!.incorrect)

        viewModel.submitPassword("first-secret")
        runCurrent()
        assertEquals(second.name, viewModel.uiState.value.passwordRequest!!.name)
        assertFalse(viewModel.uiState.value.passwordRequest!!.incorrect)

        viewModel.submitPassword("second-secret")
        runCurrent()
        assertEquals(listOf("first-secret", "second-secret"), editor.requests.last().map { it.password })
        assertEquals(editor.result, viewModel.uiState.value.pending)
        assertNull(viewModel.uiState.value.passwordRequest)
        assertTrue(savedState.keys().none { savedState.get<Any?>(it) in setOf("first-secret", "second-secret") })

        viewModel.completeDelivery(editor.result.pdfUri)
        viewModel.merge(selection)
        runCurrent()
        assertEquals(first.uri, viewModel.uiState.value.passwordRequest!!.uri)
        assertEquals(listOf(null, null), editor.requests.last().map { it.password })
    }

    @Test
    fun protectedLibraryDocumentsWaitForUnlockAndUseTheMatchingPasswordVersion() = runTest(dispatcher) {
        val protected = first.copy(protectionKeyId = "source-key")
        viewModel.merge(listOf(protected, second))
        runCurrent()
        assertTrue(viewModel.uiState.value.needsUnlock)
        assertTrue(editor.requests.isEmpty())

        passwords.state.value = passwords.state.value.copy(isUnlocked = true)
        viewModel.unlocked()
        runCurrent()
        assertEquals(listOf("source-key"), passwords.requestedKeys)
        assertEquals("stored-password", editor.requests.single().first().password)
        assertEquals(editor.result, viewModel.uiState.value.pending)
    }

    @Test
    fun unavailablePasswordVersionPromptsOnceAndAcceptsAManualPassword() = runTest(dispatcher) {
        passwords.state.value = passwords.state.value.copy(isUnlocked = true)
        passwords.missingVersion = true
        viewModel.merge(listOf(first.copy(protectionKeyId = "missing-key"), second))
        runCurrent()
        assertEquals(first.name, viewModel.uiState.value.passwordRequest!!.name)
        assertFalse(viewModel.uiState.value.passwordRequest!!.incorrect)
        assertTrue(editor.requests.isEmpty())

        viewModel.submitPassword("manual-password")
        runCurrent()

        assertEquals(listOf("missing-key"), passwords.requestedKeys)
        assertEquals("manual-password", editor.requests.single().first().password)
        assertEquals(editor.result, viewModel.uiState.value.pending)
    }

    @Test
    fun cancellationDiscardsALateDraftInsteadOfPublishingIt() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        editor.merge = {
            withContext(NonCancellable) { gate.await() }
            editor.result
        }
        viewModel.merge(selection)
        runCurrent()
        assertTrue(viewModel.uiState.value.isWorking)

        viewModel.cancel()
        gate.complete(Unit)
        runCurrent()

        assertFalse(viewModel.uiState.value.isActive)
        assertNull(viewModel.uiState.value.pending)
        assertEquals(listOf(editor.result), documents.discarded)
        assertTrue(savedState.keys().none { it.startsWith("merge.") })
    }

    @Test
    fun newMergeDoesNotReceiveTheCancelledOperationsDraft() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val oldDraft = editor.result.copy(pdfUri = "file:///old/merged.pdf")
        editor.merge = {
            withContext(NonCancellable) { gate.await() }
            oldDraft
        }
        viewModel.merge(selection)
        runCurrent()
        viewModel.cancel()

        editor.merge = { editor.result }
        assertTrue(viewModel.merge(selection.reversed()))
        runCurrent()
        assertTrue(viewModel.uiState.value.isWorking)
        gate.complete(Unit)
        runCurrent()

        assertEquals(editor.result, viewModel.uiState.value.pending)
        assertEquals(selection.reversed().map { it.uri }, editor.requests.last().map { it.uri })
        assertEquals(listOf(oldDraft), documents.discarded)
        assertEquals(editor.result.pdfUri, savedState.get<String>("merge.pdf"))
        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun backgroundingWipesManualPasswordsAndRetryAsksForThemAgain() = runTest(dispatcher) {
        editor.merge = { sources ->
            if (sources[0].password != "first-secret") throw PdfMergePasswordRequiredException(first.uri)
            if (sources[1].password != "second-secret") throw PdfMergePasswordRequiredException(second.uri)
            editor.result
        }
        viewModel.merge(selection)
        runCurrent()
        viewModel.submitPassword("first-secret")
        runCurrent()
        assertEquals(second.uri, viewModel.uiState.value.passwordRequest!!.uri)

        viewModel.backgrounded()
        assertNull(viewModel.uiState.value.passwordRequest)
        assertFalse(viewModel.uiState.value.isWorking)
        viewModel.retry()
        runCurrent()

        assertEquals(first.uri, viewModel.uiState.value.passwordRequest!!.uri)
        assertFalse(viewModel.uiState.value.passwordRequest!!.incorrect)
        assertNull(viewModel.uiState.value.pending)
        assertTrue(savedState.keys().isEmpty())
    }

    @Test
    fun backgroundingProtectedWorkLocksTheVaultBeforeRetry() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        passwords.state.value = passwords.state.value.copy(isUnlocked = true)
        editor.merge = { gate.await(); editor.result }
        viewModel.merge(listOf(first.copy(protectionKeyId = "source-key"), second))
        runCurrent()

        viewModel.backgrounded()
        runCurrent()
        assertEquals(1, passwords.lockCount)
        assertFalse(passwords.state.value.isUnlocked)
        viewModel.retry()
        runCurrent()

        assertTrue(viewModel.uiState.value.needsUnlock)
        assertEquals(1, editor.requests.size)
        assertNull(viewModel.uiState.value.pending)
    }

    @Test
    fun pendingDraftSurvivesRecreationUntilTheMatchingDeliveryIsAcknowledged() = runTest(dispatcher) {
        viewModel.merge(selection)
        runCurrent()
        val restoredState = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) })
        store.clear()
        runCurrent()
        val restored = DocumentQuickMergeViewModel(editor, documents, passwords, restoredState)
            .also { store.put("restored", it) }

        assertTrue(restored.uiState.value.isActive)
        assertEquals(editor.result, restored.uiState.value.pending)
        restored.completeDelivery("file:///unrelated.pdf")
        assertEquals(editor.result, restored.uiState.value.pending)

        restored.completeDelivery(editor.result.pdfUri)
        assertFalse(restored.uiState.value.isActive)
        assertNull(restored.uiState.value.pending)
        assertTrue(restoredState.keys().isEmpty())
        assertTrue(documents.discarded.isEmpty())
        assertEquals(1, editor.requests.size)
    }

    @Test
    fun cancellingAnUndeliveredDraftRemovesOnlyTheNewDraft() = runTest(dispatcher) {
        viewModel.merge(selection)
        runCurrent()
        viewModel.cancel()
        runCurrent()

        assertEquals(listOf(editor.result), documents.discarded)
        assertTrue(documents.deleted.isEmpty())
        assertNull(viewModel.uiState.value.pending)
        assertTrue(savedState.keys().isEmpty())
    }

    private class FakePasswords : PasswordAccess {
        override val state = MutableStateFlow(PasswordVaultState(isConfigured = true))
        override val activeKeyId = "active-key"
        var missingVersion = false
        var lockCount = 0
        val requestedKeys = mutableListOf<String?>()

        override suspend fun <T> withPassword(keyId: String?, block: suspend (String) -> T): T {
            if (!state.value.isUnlocked) throw PasswordVaultLockedException()
            requestedKeys += keyId
            if (missingVersion) throw PasswordVersionMissingException()
            return block("stored-password")
        }

        override fun lock() {
            lockCount++
            state.value = state.value.copy(isUnlocked = false)
        }
    }

    private class FakeEditor : PdfEditorRepository {
        val result = ScannedDocument("file:///new/merged.pdf", 5, 10, 2048, "file:///new/thumbnail.jpg")
        val requests = mutableListOf<List<PdfMergeSource>>()
        var saveCalls = 0
        var merge: suspend (List<PdfMergeSource>) -> ScannedDocument = { result }

        override suspend fun mergePdfs(sources: List<PdfMergeSource>): ScannedDocument {
            requests += sources.toList()
            return merge(sources)
        }

        override suspend fun open(document: DocumentModel, password: String?): PdfWorkspace = error("Não deve abrir um original para edição")
        override suspend fun importPdf(uri: String, password: String?, protectionKeyId: String?): ScannedDocument = error("Fora deste fluxo")
        override suspend fun appendScans(workspace: PdfWorkspace, pageUris: List<String>): PdfWorkspace = error("Fora deste fluxo")
        override suspend fun appendPdfs(workspace: PdfWorkspace, sources: List<PdfMergeSource>): PdfWorkspace = error("Não deve alterar um original")
        override suspend fun copySignature(workspace: PdfWorkspace, imageUri: String): String = error("Fora deste fluxo")
        override suspend fun preview(workspace: PdfWorkspace, retainedPreviewUri: String?): String = error("Fora deste fluxo")
        override suspend fun clearPreview(workspace: PdfWorkspace) = Unit
        override suspend fun save(workspace: PdfWorkspace, password: String?, protectionKeyId: String?, removeProtection: Boolean): DocumentModel {
            saveCalls++
            error("Não deve substituir o documento original")
        }
        override suspend fun discard(workspace: PdfWorkspace) = Unit
    }
}
