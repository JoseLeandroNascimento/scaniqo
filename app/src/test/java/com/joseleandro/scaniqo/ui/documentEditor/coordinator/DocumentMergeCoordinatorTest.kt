package com.joseleandro.scaniqo.ui.documentEditor.coordinator

import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultState
import com.joseleandro.scaniqo.core.security.PasswordVersionMissingException
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfMergePasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentCommon.model.toMergeItem
import com.joseleandro.scaniqo.ui.documentEditor.effect.DocumentToolsEffect
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentToolsMessage
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentMergeUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentMergeCoordinatorTest {
    @Test fun cancellationDropsLateResultWithoutUpdatingHistoryOrFinishingAnotherOperation() = runTest {
        val fixture = Fixture(this)
        val initial = fixture.workspace
        val gate = CompletableDeferred<Unit>()
        fixture.append = { workspace, sources ->
            withContext(NonCancellable) { gate.await() }
            appended(workspace, sources)
        }

        fixture.coordinator.mergeDocuments(listOf(source()), targetIndex = 1)
        runCurrent()
        assertEquals(DocumentMergeUiState.Merging, fixture.phase)
        fixture.coordinator.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(DocumentMergeUiState.Idle, fixture.phase)
        assertEquals(initial, fixture.workspace)
        assertTrue(fixture.history.isEmpty())
        assertEquals(0, fixture.finished)
        assertTrue(fixture.messages.isEmpty())
    }

    @Test fun aNewSessionRejectsSuccessfulAndPasswordResultsFromOldSession() = runTest {
        for (passwordRequired in listOf(false, true)) {
            val fixture = Fixture(this)
            val initial = fixture.workspace
            val gate = CompletableDeferred<Unit>()
            fixture.append = { workspace, sources ->
                gate.await()
                if (passwordRequired) throw PdfMergePasswordRequiredException(sources.first().uri)
                appended(workspace, sources)
            }
            fixture.coordinator.mergeDocuments(listOf(source()), targetIndex = 0)
            runCurrent()
            fixture.session++
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(initial, fixture.workspace)
            assertTrue(fixture.history.isEmpty())
            assertFalse(fixture.phase is DocumentMergeUiState.PasswordRequired)
            assertEquals(0, fixture.finished)
            fixture.coordinator.clear()
        }
    }

    @Test fun missingPasswordVersionUsesNamedManualPromptAndCommitsOneUndoSnapshot() = runTest {
        val fixture = Fixture(this)
        val initial = fixture.workspace
        val protectedSource = source().copy(name = "Contrato antigo", protectionKeyId = "missing")
        fixture.passwords.unlocked = true
        fixture.passwords.missingVersion = true
        fixture.append = { workspace, sources ->
            if (sources.single().password != "correct") {
                throw PdfMergePasswordRequiredException(sources.single().uri)
            }
            appended(workspace, sources)
        }

        fixture.coordinator.mergeDocuments(listOf(protectedSource), targetIndex = 1)
        advanceUntilIdle()
        assertEquals(
            DocumentMergeUiState.PasswordRequired(protectedSource.pdfUri, "Contrato antigo"),
            fixture.phase,
        )
        assertEquals(0, fixture.appendCalls)
        assertTrue(fixture.history.isEmpty())

        fixture.coordinator.submitPassword("wrong")
        advanceUntilIdle()
        assertEquals(
            DocumentMergeUiState.PasswordRequired(protectedSource.pdfUri, "Contrato antigo", incorrect = true),
            fixture.phase,
        )
        assertTrue(fixture.history.isEmpty())

        fixture.coordinator.submitPassword("correct")
        advanceUntilIdle()
        assertEquals(DocumentMergeUiState.Idle, fixture.phase)
        assertEquals(listOf(initial), fixture.history)
        assertEquals(listOf("first", "added-0", "second"), fixture.workspace.pages.map { it.id })
        assertEquals("added-0", fixture.selectedPageId)
        assertEquals(listOf("missing"), fixture.passwords.requestedKeys)
        assertFalse(fixture.coordinator.hasSensitiveData)
    }

    @Test fun lockingWorkspaceReacquiresVersionedPasswordBeforeResumingSelection() = runTest {
        val fixture = Fixture(this)
        fixture.protectedDestination = true
        fixture.passwords.unlocked = true
        fixture.passwords.secret = "before"
        val gate = CompletableDeferred<Unit>()
        val receivedPasswords = mutableListOf<String?>()
        fixture.append = { workspace, sources ->
            receivedPasswords += sources.single().password
            gate.await()
            appended(workspace, sources)
        }

        fixture.coordinator.mergeDocuments(listOf(source().copy(protectionKeyId = "source-key")), targetIndex = 2)
        runCurrent()
        fixture.locked = true
        fixture.passwords.unlocked = false
        fixture.coordinator.onWorkspaceLocked()
        runCurrent()
        assertTrue(fixture.history.isEmpty())

        fixture.passwords.secret = "after"
        fixture.passwords.unlocked = true
        fixture.locked = false
        gate.complete(Unit)
        assertTrue(fixture.coordinator.resumeAfterUnlock())
        advanceUntilIdle()

        assertEquals(listOf("before", "after"), receivedPasswords)
        assertEquals(listOf("source-key", "source-key"), fixture.passwords.requestedKeys)
        assertEquals(1, fixture.history.size)
        assertEquals(listOf("first", "second", "added-0"), fixture.workspace.pages.map { it.id })
        assertEquals(DocumentMergeUiState.Idle, fixture.phase)
    }

    @Test fun sharedMutexProtectsPasswordResolutionAndAppending() = runTest {
        val fixture = Fixture(this)
        fixture.passwords.unlocked = true
        fixture.mutex.lock()
        fixture.coordinator.mergeDocuments(listOf(source().copy(protectionKeyId = "source-key")), targetIndex = 0)
        runCurrent()

        assertEquals(DocumentMergeUiState.Merging, fixture.phase)
        assertTrue(fixture.passwords.requestedKeys.isEmpty())
        assertEquals(0, fixture.appendCalls)
        fixture.mutex.unlock()
        advanceUntilIdle()

        assertEquals(listOf("source-key"), fixture.passwords.requestedKeys)
        assertEquals(1, fixture.appendCalls)
        assertEquals(1, fixture.history.size)
    }

    @Test fun mixedSourcesPreserveDragOrderAndInsertTogetherAfterTheChosenPage() = runTest {
        val fixture = Fixture(this)
        fixture.passwords.unlocked = true
        val protected = source().copy(protectionKeyId = "source-key").toMergeItem()
        val firstExternal = DocumentMergeItem("content://first", "Primeiro.pdf")
        val lastExternal = DocumentMergeItem("content://last", "Último.pdf")
        val received = mutableListOf<PdfMergeSource>()
        fixture.append = { workspace, sources ->
            received += sources
            appended(workspace, sources)
        }
        val initial = fixture.workspace

        fixture.coordinator.mergeItems(listOf(firstExternal, protected, lastExternal), targetIndex = 1)
        advanceUntilIdle()

        assertEquals(listOf(firstExternal.uri, protected.uri, lastExternal.uri), received.map { it.uri })
        assertEquals(listOf(null, "stored", null), received.map { it.password })
        assertEquals(listOf("first", "added-0", "added-1", "added-2", "second"), fixture.workspace.pages.map { it.id })
        assertEquals(listOf(initial), fixture.history)
        assertEquals("added-0", fixture.selectedPageId)
    }

    @Test fun duplicateDeviceSelectionKeepsLibraryCredentialsAndExcludesTheOpenDocument() = runTest {
        val fixture = Fixture(this)
        fixture.passwords.unlocked = true
        val library = source().copy(protectionKeyId = "source-key").toMergeItem()
        val received = mutableListOf<PdfMergeSource>()
        fixture.append = { workspace, sources ->
            received += sources
            appended(workspace, sources)
        }

        fixture.coordinator.mergeItems(
            listOf(
                DocumentMergeItem(library.uri, "Arquivo externo"),
                fixture.document.toMergeItem(),
                library,
                DocumentMergeItem(fixture.document.pdfUri, "O próprio documento"),
            ),
            targetIndex = 0,
        )
        advanceUntilIdle()

        assertEquals(listOf(PdfMergeSource(library.uri, "stored")), received)
        assertEquals(listOf("source-key"), fixture.passwords.requestedKeys)
        assertEquals(listOf("added-0", "first", "second"), fixture.workspace.pages.map { it.id })
    }


    @Test fun orderedSelectionPreservesCurrentRecipesBetweenMixedSourcesWithoutReimportingIt() = runTest {
        for (currentIndex in 0..2) {
            val fixture = Fixture(this)
            val current = fixture.document.toMergeItem().copy(isCurrentDocument = true)
            val source = source().copy(protectionKeyId = "source-key").toMergeItem()
            val external = DocumentMergeItem("content://external", "Anexo")
            fixture.passwords.unlocked = true
            val received = mutableListOf<PdfMergeSource>()
            var receivedIndex = -1
            fixture.appendOrdered = { workspace, sources, index ->
                received += sources
                receivedIndex = index
                val blocks = sources.map { item ->
                    (0..1).map { page -> PdfPageSpec("added-${item.uri}-$page", item.uri, page) }
                }
                workspace.copy(pages = blocks.take(index).flatten() + workspace.pages + blocks.drop(index).flatten())
            }
            val before = fixture.workspace
            val items = listOf(source, external).toMutableList().apply { add(currentIndex, current) }

            fixture.coordinator.mergeOrderedItems(items)
            advanceUntilIdle()

            assertEquals(currentIndex, receivedIndex)
            assertEquals(listOf(source.uri, external.uri), received.map { it.uri })
            assertEquals(listOf("stored", null), received.map { it.password })
            assertEquals(1, fixture.appendCalls)
            assertEquals(listOf(before), fixture.history)
            assertEquals(before.pages, fixture.workspace.pages.drop(currentIndex * 2).take(before.pages.size))
            assertEquals(before, fixture.workspace.copy(pages = before.pages))
            assertEquals(6, fixture.workspace.pages.size)
            assertEquals(6, fixture.workspace.pages.map { it.id }.distinct().size)
            assertEquals("added-${source.uri}-0", fixture.selectedPageId)
        }
    }

    @Test fun orderedSelectionRequiresTheCurrentBlockAndAtLeastOneOtherPdf() = runTest {
        val fixture = Fixture(this)
        val current = fixture.document.toMergeItem().copy(isCurrentDocument = true)
        for (items in listOf(listOf(current), listOf(source().toMergeItem()), emptyList())) {
            fixture.coordinator.mergeOrderedItems(items)
            advanceUntilIdle()
        }

        assertEquals(0, fixture.appendCalls)
        assertTrue(fixture.history.isEmpty())
        assertEquals(List(3) { DocumentToolsMessage.MERGE_ERROR }, fixture.messages)
        assertEquals(DocumentMergeUiState.Idle, fixture.phase)
    }

    @Test fun orderedSelectionRetainsBlockPositionAcrossPasswordPromptsAndCreatesOneUndo() = runTest {
        val fixture = Fixture(this)
        val before = DocumentMergeItem("content://before", "Antes")
        val after = DocumentMergeItem("content://after", "Depois protegido")
        val current = fixture.document.toMergeItem().copy(isCurrentDocument = true)
        val positions = mutableListOf<Int>()
        fixture.appendOrdered = { workspace, sources, index ->
            positions += index
            if (sources.last().password != "correct") throw PdfMergePasswordRequiredException(after.uri)
            ordered(workspace, sources, index)
        }
        val original = fixture.workspace

        fixture.coordinator.mergeOrderedItems(listOf(before, current, after))
        advanceUntilIdle()
        assertEquals(DocumentMergeUiState.PasswordRequired(after.uri, after.name), fixture.phase)
        assertTrue(fixture.history.isEmpty())
        fixture.coordinator.submitPassword("correct")
        advanceUntilIdle()

        assertEquals(listOf(1, 1), positions)
        assertEquals(listOf(original), fixture.history)
        assertEquals(listOf("added-${before.uri}", "first", "second", "added-${after.uri}"), fixture.workspace.pages.map { it.id })
        assertFalse(fixture.coordinator.hasSensitiveData)
    }

    @Test fun cancellingOrderedMergeRejectsLateResultsAndLeavesTheWholeCurrentBlockUntouched() = runTest {
        val fixture = Fixture(this)
        val initial = fixture.workspace
        val gate = CompletableDeferred<Unit>()
        fixture.appendOrdered = { workspace, sources, index ->
            withContext(NonCancellable) { gate.await() }
            ordered(workspace, sources, index)
        }

        fixture.coordinator.mergeOrderedItems(listOf(source().toMergeItem(), fixture.document.toMergeItem()))
        runCurrent()
        assertEquals(DocumentMergeUiState.Merging, fixture.phase)
        fixture.coordinator.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(initial, fixture.workspace)
        assertTrue(fixture.history.isEmpty())
        assertEquals(DocumentMergeUiState.Idle, fixture.phase)
        assertEquals(0, fixture.finished)
    }

    @Test fun orderedMergeDeduplicatesCurrentFileAndWaitsForUnlockBeforeImportingOtherSources() = runTest {
        val fixture = Fixture(this)
        fixture.locked = true
        fixture.protectedDestination = true
        val other = source().toMergeItem().copy(protectionKeyId = "source-key")
        val current = fixture.document.toMergeItem().copy(isCurrentDocument = true)
        fixture.coordinator.mergeOrderedItems(listOf(
            other, current, DocumentMergeItem(current.uri, "O mesmo PDF"),
        ))
        assertEquals(DocumentMergeUiState.AwaitingUnlock, fixture.phase)
        assertEquals(0, fixture.appendCalls)
        fixture.passwords.unlocked = true
        fixture.locked = false
        assertTrue(fixture.coordinator.resumeAfterUnlock())
        advanceUntilIdle()

        assertEquals(1, fixture.appendCalls)
        assertEquals(listOf("source-key"), fixture.passwords.requestedKeys)
        assertEquals(listOf("added-${other.uri}", "first", "second"), fixture.workspace.pages.map { it.id })
        assertEquals(1, fixture.history.size)
    }

    private class Fixture(scope: CoroutineScope) {
        val document = DocumentModel(
            id = 1, name = "Destino", pdfUri = "file:///destination.pdf", pageCount = 2,
            createdAt = 1, sizeBytes = 200,
        )
        var workspace = PdfWorkspace(
            documentId = document.id, workspaceId = "session", directoryUri = "file:///session",
            pages = listOf(
                PdfPageSpec("first", document.pdfUri, 0),
                PdfPageSpec("second", document.pdfUri, 1),
            ),
        )
        var session = 1L
        var locked = false
        var protectedDestination = false
        var phase: DocumentMergeUiState = DocumentMergeUiState.Idle
        var selectedPageId: String? = null
        var appendCalls = 0
        var finished = 0
        val history = mutableListOf<PdfWorkspace>()
        val messages = mutableListOf<DocumentToolsMessage>()
        val effects = mutableListOf<DocumentToolsEffect>()
        val passwords = FakePasswords()
        val mutex = Mutex()
        var append: suspend (PdfWorkspace, List<PdfMergeSource>) -> PdfWorkspace = ::appended
        var appendOrdered: suspend (PdfWorkspace, List<PdfMergeSource>, Int) -> PdfWorkspace = ::ordered

        val coordinator = DocumentMergeCoordinator(
            scope = scope,
            passwords = passwords,
            ioMutex = mutex,
            context = {
                DocumentMergeContext(
                    document = document, workspace = workspace, session = session,
                    isBusy = phase != DocumentMergeUiState.Idle,
                    isLocked = locked, hasSignatureDraft = false,
                    requiresAuthentication = protectedDestination,
                )
            },
            append = { workspace, sources ->
                appendCalls++
                append(workspace, sources)
            },
            appendOrdered = { workspace, sources, index ->
                appendCalls++
                appendOrdered(workspace, sources, index)
            },
            onStateChanged = { phase = it },
            onEffect = { effects += it },
            onMessage = { messages += it },
            onWorkStarted = {},
            onMerged = { previous, updated, firstAdded ->
                history += previous
                workspace = updated
                selectedPageId = firstAdded
            },
            onWorkFinished = { finished++ },
        )
    }

    private class FakePasswords : PasswordAccess {
        override val state = MutableStateFlow(PasswordVaultState(isConfigured = true))
        override val activeKeyId = "destination-key"
        var unlocked: Boolean
            get() = state.value.isUnlocked
            set(value) { state.value = state.value.copy(isUnlocked = value) }
        var missingVersion = false
        var secret = "stored"
        val requestedKeys = mutableListOf<String?>()

        override suspend fun <T> withPassword(keyId: String?, block: suspend (String) -> T): T {
            if (!unlocked) throw PasswordVaultLockedException()
            requestedKeys += keyId
            if (missingVersion) throw PasswordVersionMissingException()
            return block(secret)
        }

        override fun lock() { unlocked = false }
    }

    private companion object {
        fun source() = DocumentModel(
            id = 2, name = "Origem", pdfUri = "file:///source.pdf", pageCount = 1,
            createdAt = 1, sizeBytes = 100,
        )

        fun ordered(workspace: PdfWorkspace, sources: List<PdfMergeSource>, currentIndex: Int): PdfWorkspace {
            val added = sources.map { source -> PdfPageSpec("added-${source.uri}", source.uri, 0) }
            return workspace.copy(pages = added.take(currentIndex) + workspace.pages + added.drop(currentIndex))
        }

        fun appended(workspace: PdfWorkspace, sources: List<PdfMergeSource>): PdfWorkspace =
            workspace.copy(pages = workspace.pages + sources.mapIndexed { index, source ->
                PdfPageSpec("added-$index", source.uri, 0)
            })
    }
}
