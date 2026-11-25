package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultState
import com.joseleandro.scaniqo.core.security.PasswordVersionMissingException
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ReadableDocument
import com.joseleandro.scaniqo.domain.repository.DocumentReaderRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
class DocumentReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeReader()
    private val passwords = FakePasswords()
    private val model by lazy {
        DocumentReaderViewModel(repository, passwords).also { store.put("reader", it) }
    }
    private val document = DocumentModel(1, "Contrato", "saved.pdf", 2, 0, 300)
    private val protected = document.copy(protectionKeyId = "original")

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun readingUsesTheSavedPdfAndMetadataChangesDoNotReopenIt() = runTest(dispatcher) {
        model.load(document)
        runCurrent()
        assertEquals(document.pdfUri, model.uiState.value.readable?.pdfUri)
        assertEquals(listOf(document to null), repository.opened)
        assertFalse(model.uiState.value.isLoading)
        val renamed = document.copy(name = "Novo nome")
        model.load(renamed)
        runCurrent()
        assertEquals(renamed, model.uiState.value.document)
        assertEquals(1, repository.opened.size)
        assertTrue(repository.closed.isEmpty())
    }

    @Test fun returningFromEditorReopensEvenWhenTheSavedUriIsUnchanged() = runTest(dispatcher) {
        model.load(document)
        runCurrent()
        model.deactivate(document.id)
        assertNull(model.uiState.value.readable)
        runCurrent()
        model.load(document)
        runCurrent()
        assertEquals(2, repository.opened.size)
        assertEquals(1, repository.closed.size)
        assertEquals(document.pdfUri, model.uiState.value.readable?.pdfUri)
    }

    @Test fun lockedDocumentNeverReachesTheRepositoryBeforeAuthentication() = runTest(dispatcher) {
        model.load(protected)
        runCurrent()
        assertTrue(model.uiState.value.isLocked)
        assertNull(model.uiState.value.readable)
        assertTrue(repository.opened.isEmpty())

        passwords.state.value = PasswordVaultState(isConfigured = true, isUnlocked = true)
        model.retry()
        runCurrent()
        assertEquals(listOf(protected to "secret"), repository.opened)
        assertFalse(model.uiState.value.isLocked)
        assertEquals("private-1.pdf", model.uiState.value.readable?.pdfUri)
    }

    @Test fun lockingAndBackgroundingImmediatelyHideAndReleaseTheProtectedCopy() = runTest(dispatcher) {
        passwords.state.value = PasswordVaultState(isConfigured = true, isUnlocked = true)
        model.load(protected)
        runCurrent()
        val first = model.uiState.value.readable
        model.background()
        assertNull(model.uiState.value.readable)
        assertTrue(model.uiState.value.isLocked)
        assertFalse(passwords.state.value.isUnlocked)
        runCurrent()
        assertEquals(listOf(first), repository.closed)

        passwords.state.value = PasswordVaultState(isConfigured = true, isUnlocked = true)
        model.retry()
        runCurrent()
        passwords.lock()
        runCurrent()
        assertTrue(model.uiState.value.isLocked)
        assertNull(model.uiState.value.readable)
        assertEquals(2, repository.closed.size)
    }

    @Test fun missingPasswordVersionCanBeRecoveredWithoutPersistingASecretInState() = runTest(dispatcher) {
        passwords.state.value = PasswordVaultState(isConfigured = true, isUnlocked = true)
        passwords.missingOriginal = true
        model.load(protected)
        runCurrent()
        assertTrue(model.uiState.value.recoveryRequired)
        assertTrue(repository.opened.isEmpty())

        model.recover("recovered")
        runCurrent()
        assertEquals("recovered", passwords.lastKeyId)
        assertFalse(model.uiState.value.recoveryRequired)
        assertFalse(model.uiState.value.isLocked)
        assertEquals(protected, model.uiState.value.document)
        assertEquals("private-1.pdf", model.uiState.value.readable?.pdfUri)
    }

    @Test fun closingAnOldRouteDoesNotReleaseANewerRouteForTheSameDocument() = runTest(dispatcher) {
        val oldOwner = Any()
        val newOwner = Any()
        model.load(document, oldOwner)
        runCurrent()
        model.load(document, newOwner)
        model.deactivate(document.id, oldOwner)
        runCurrent()
        assertTrue(repository.closed.isEmpty())
        assertEquals(document.pdfUri, model.uiState.value.readable?.pdfUri)
        model.deactivate(document.id, newOwner)
        runCurrent()
        assertEquals(1, repository.closed.size)
    }

    @Test fun aLateCancelledResultIsClosedAndCannotReplaceTheNewDocument() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        model.load(document)
        runCurrent()
        val next = document.copy(id = 2, pdfUri = "next.pdf")
        model.load(next)
        runCurrent()
        assertNull(model.uiState.value.readable)
        repository.gate = null
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(ReadableDocument(document.pdfUri, 2)), repository.closed)
        assertEquals(next, model.uiState.value.document)
        assertEquals("next.pdf", model.uiState.value.readable?.pdfUri)
    }

    @Test fun readFailuresOfferRetryAndClearingTheViewModelReleasesTheSession() = runTest(dispatcher) {
        repository.failure = IllegalStateException("Unreadable PDF")
        model.load(document)
        runCurrent()
        assertTrue(model.uiState.value.loadError)
        assertFalse(model.uiState.value.isLoading)
        repository.failure = null
        model.retry()
        runCurrent()
        assertFalse(model.uiState.value.loadError)
        store.clear()
        runCurrent()
        assertEquals(1, repository.closed.size)
    }

    private class FakeReader : DocumentReaderRepository {
        val opened = mutableListOf<Pair<DocumentModel, String?>>()
        val closed = mutableListOf<ReadableDocument>()
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun open(document: DocumentModel, password: String?): ReadableDocument {
            opened += document to password
            failure?.let { throw it }
            gate?.let { withContext(NonCancellable) { it.await() } }
            return if (document.isProtected) ReadableDocument(
                "private-${document.id}.pdf", document.pageCount, "session-${opened.size}",
            ) else ReadableDocument(document.pdfUri, document.pageCount)
        }

        override suspend fun close(document: ReadableDocument) { closed += document }
    }

    private class FakePasswords : PasswordAccess {
        override val state = MutableStateFlow(PasswordVaultState(isConfigured = true))
        override val activeKeyId: String? = "original"
        var missingOriginal = false
        var lastKeyId: String? = null

        override suspend fun <T> withPassword(keyId: String?, block: suspend (String) -> T): T {
            if (!state.value.isUnlocked) throw PasswordVaultLockedException()
            if (missingOriginal && keyId == "original") throw PasswordVersionMissingException()
            lastKeyId = keyId
            return block("secret")
        }

        override fun lock() { state.value = state.value.copy(isUnlocked = false) }
    }
}
