package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultLockedException
import com.joseleandro.scaniqo.core.security.PasswordVaultState
import com.joseleandro.scaniqo.core.security.PasswordVersionMissingException
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfMergeLimitException
import com.joseleandro.scaniqo.domain.model.PdfMergePasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfMergeSource
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.PdfSignatureImage
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.ScannedDocument
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentMergeItem
import com.joseleandro.scaniqo.ui.documentEditor.effect.DocumentToolsEffect
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentMergeUiState
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentToolsMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentToolsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakePdfEditorRepository()
    private val signatures = FakeSignatureRepository()
    private val passwords = FakePasswords()
    private val document = DocumentModel(
        id = 7, name = "Contrato", pdfUri = "file:///saved.pdf", pageCount = 3,
        createdAt = 1L, sizeBytes = 300L,
    )
    private val viewModel by lazy {
        DocumentToolsViewModel(repository, signatures, passwords).also { store.put("tools", it) }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun layoutOnlyChangesSelectedPageUntilExplicitSave() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.TOP, PageScaleMode.FILL, false))
        advanceUntilIdle()

        val pages = viewModel.uiState.value.workspace!!.pages
        assertEquals(PageAlignment.CENTER, pages[0].layout.alignment)
        assertEquals(PageAlignment.TOP, pages[1].layout.alignment)
        assertEquals(PageScaleMode.FILL, pages[1].layout.scaleMode)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canExport)
        assertEquals(0, repository.saves.size)

        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertEquals(1, repository.saves.size)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertTrue(viewModel.uiState.value.canExport)
    }

    @Test fun rapidLayoutChangesRenderOnlyLatestRecipeAndCanReturnToBaseline() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val previousCount = repository.previews.size
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.LEFT, PageScaleMode.FILL, true))
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.RIGHT, PageScaleMode.FIT, true))
        advanceUntilIdle()
        assertEquals(previousCount + 1, repository.previews.size)
        assertTrue(repository.previews.last().pages.all { it.layout.alignment == PageAlignment.RIGHT })

        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.CENTER, PageScaleMode.FIT, true))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun rotationResizeAndFilterUseIdenticalRecipeForSelectedPreviewAndSave() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        val expected = PdfPageLayout(
            alignment = PageAlignment.RIGHT,
            scaleMode = PageScaleMode.FIT,
            rotationQuarterTurns = 1,
            contentScale = 0.75f,
            imageFilter = PdfImageFilter.AUTO,
        )
        viewModel.onEvent(
            DocumentToolsEvent.ApplyLayout(
                expected.alignment, expected.scaleMode, false,
                expected.rotationQuarterTurns, expected.contentScale,
            ),
        )
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(expected.imageFilter, allPages = false))
        advanceUntilIdle()

        val preview = repository.previews.last()
        assertEquals(expected, preview.pages[1].layout)
        assertEquals(PdfPageLayout(), preview.pages[0].layout)
        assertEquals(PdfPageLayout(), preview.pages[2].layout)
        assertEquals("file:///source.pdf", preview.pages[1].sourceUri)
        assertEquals(1, preview.pages[1].sourcePageIndex)

        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertEquals(preview, repository.saves.single())
    }

    @Test fun resettingGeometryPreservesFiltersUntilOriginalFilterIsApplied() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(
            DocumentToolsEvent.ApplyLayout(
                PageAlignment.BOTTOM, PageScaleMode.FILL, true,
                rotationQuarterTurns = 3, contentScale = 1.5f,
            ),
        )
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.BLACK_WHITE, allPages = true))
        advanceUntilIdle()
        assertTrue(repository.previews.last().pages.all {
            it.layout.rotationQuarterTurns == 3 &&
                it.layout.contentScale == 1.5f &&
                it.layout.imageFilter == PdfImageFilter.BLACK_WHITE
        })

        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.CENTER, PageScaleMode.FIT, true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(repository.previews.last().pages.all {
            it.layout == PdfPageLayout(imageFilter = PdfImageFilter.BLACK_WHITE)
        })

        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ORIGINAL, allPages = true))
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertEquals(original, repository.previews.last())
    }

    @Test fun layoutNormalizesRotationAndBoundsScaleWithoutAcceptingNonFiniteValues() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(
            DocumentToolsEvent.ApplyLayout(
                PageAlignment.CENTER, PageScaleMode.FIT, false,
                rotationQuarterTurns = -1, contentScale = 7f,
            ),
        )
        advanceUntilIdle()
        var layout = viewModel.uiState.value.workspace!!.pages.first().layout
        assertEquals(3, layout.rotationQuarterTurns)
        assertEquals(2f, layout.contentScale, 0.0001f)

        viewModel.onEvent(
            DocumentToolsEvent.ApplyLayout(
                PageAlignment.TOP, PageScaleMode.FIT, false,
                rotationQuarterTurns = 5, contentScale = Float.NaN,
            ),
        )
        advanceUntilIdle()
        layout = viewModel.uiState.value.workspace!!.pages.first().layout
        assertEquals(1, layout.rotationQuarterTurns)
        assertEquals(2f, layout.contentScale, 0.0001f)
    }

    @Test fun failedAdjustmentPreviewCanRetrySameRecipeWithoutLosingEdits() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.GRAYSCALE, allPages = false))
        advanceUntilIdle()
        val adjustment = DocumentToolsEvent.ApplyLayout(
            PageAlignment.LEFT, PageScaleMode.FIT, false,
            contentScale = 0.5f,
        )
        val previousUri = viewModel.uiState.value.previewUri
        repository.failPreview = true
        viewModel.onEvent(adjustment)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.previewError)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertEquals(previousUri, viewModel.uiState.value.previewUri)

        repository.failPreview = false
        viewModel.onEvent(adjustment)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.previewError)
        assertNotNull(viewModel.uiState.value.previewUri)
        assertEquals(PdfImageFilter.GRAYSCALE, repository.previews.last().pages.first().layout.imageFilter)
        assertEquals(0.5f, repository.previews.last().pages.first().layout.contentScale, 0.0001f)
    }

    @Test fun applyingAndRemovingFilterOnAllPagesPreservesEachPagesGeometry() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(
            DocumentToolsEvent.ApplyLayout(
                PageAlignment.TOP, PageScaleMode.FILL, false,
                rotationQuarterTurns = 1, contentScale = 0.5f,
            ),
        )
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        viewModel.onEvent(
            DocumentToolsEvent.ApplyLayout(
                PageAlignment.RIGHT, PageScaleMode.FIT, false,
                rotationQuarterTurns = 2, contentScale = 1.25f,
            ),
        )
        advanceUntilIdle()
        val beforeFilter = viewModel.uiState.value.workspace!!

        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.GRAYSCALE, allPages = true))
        advanceUntilIdle()
        val filtered = viewModel.uiState.value.workspace!!
        beforeFilter.pages.zip(filtered.pages).forEach { (before, after) ->
            assertEquals(before.copy(layout = before.layout.copy(imageFilter = PdfImageFilter.GRAYSCALE)), after)
        }

        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ORIGINAL, allPages = true))
        advanceUntilIdle()
        assertEquals(beforeFilter, viewModel.uiState.value.workspace)
        assertEquals(beforeFilter, repository.previews.last())
        assertTrue(viewModel.uiState.value.hasChanges)
    }

    @Test fun allPageGeometryAndResetPreserveDifferentFiltersOnEachPage() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, allPages = false))
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.BLACK_WHITE, allPages = false))
        advanceUntilIdle()
        val filters = listOf(PdfImageFilter.ENHANCE, PdfImageFilter.BLACK_WHITE, PdfImageFilter.ORIGINAL)

        viewModel.onEvent(
            DocumentToolsEvent.ApplyLayout(
                PageAlignment.LEFT, PageScaleMode.FIT, true,
                rotationQuarterTurns = 3, contentScale = 0.75f,
            ),
        )
        advanceUntilIdle()
        val adjusted = viewModel.uiState.value.workspace!!.pages
        assertEquals(filters, adjusted.map { it.layout.imageFilter })
        assertTrue(adjusted.all {
            it.layout.rotationQuarterTurns == 3 &&
                it.layout.contentScale == 0.75f &&
                it.layout.alignment == PageAlignment.LEFT
        })

        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.CENTER, PageScaleMode.FIT, true))
        advanceUntilIdle()
        assertEquals(filters.map { PdfPageLayout(imageFilter = it) }, repository.previews.last().pages.map { it.layout })
    }

    @Test fun failedFilterPreviewCanRetrySameFilterWithoutLosingGeometry() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(
            DocumentToolsEvent.ApplyLayout(
                PageAlignment.BOTTOM, PageScaleMode.FIT, false,
                rotationQuarterTurns = 1, contentScale = 0.75f,
            ),
        )
        advanceUntilIdle()
        val geometry = viewModel.uiState.value.workspace!!.pages.first().layout
        val filter = DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, allPages = false)

        val previousUri = viewModel.uiState.value.previewUri
        repository.failPreview = true
        viewModel.onEvent(filter)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.previewError)
        assertEquals(previousUri, viewModel.uiState.value.previewUri)

        repository.failPreview = false
        viewModel.onEvent(filter)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.previewError)
        assertNotNull(viewModel.uiState.value.previewUri)
        assertEquals(geometry.copy(imageFilter = PdfImageFilter.ENHANCE), repository.previews.last().pages.first().layout)
        assertEquals(PdfPageLayout(), repository.previews.last().pages[1].layout)
    }

    @Test fun lockedProtectedDocumentCannotApplyFilterOrGeometry() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key-1"))
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        val previewsBefore = repository.previews.size

        viewModel.onEvent(DocumentToolsEvent.ApplyFilter(PdfImageFilter.ENHANCE, allPages = true))
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(PageAlignment.LEFT, PageScaleMode.FILL, true))
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertEquals(previewsBefore, repository.previews.size)
        assertNull(viewModel.uiState.value.previewUri)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun reorderRetainsSelectionAndLastPageCannotBeDeleted() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-2"))
        viewModel.onEvent(DocumentToolsEvent.MovePage("page-2", -1))
        assertEquals(listOf("page-2", "page-1", "page-3"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertEquals("page-2", viewModel.uiState.value.selectedPageId)
        listOf("page-1", "page-3").forEach {
            viewModel.onEvent(DocumentToolsEvent.RequestDeletePage(it))
            viewModel.onEvent(DocumentToolsEvent.ConfirmDeletePage)
        }
        viewModel.onEvent(DocumentToolsEvent.RequestDeletePage("page-2"))
        viewModel.onEvent(DocumentToolsEvent.ConfirmDeletePage)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.workspace!!.pages.size)
        assertNull(viewModel.uiState.value.pendingDeletePageId)
    }

    @Test fun failedSavePreservesUnsavedRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        repository.failSave = true
        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(viewModel.uiState.value.workspace!!.showPageNumbers)
        assertEquals(DocumentToolsMessage.SAVE_ERROR, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test fun navigatingToLibraryAndLoadingSameDocumentDoesNotLoseEdits() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        viewModel.load(document.copy(name = "Outro nome"))
        advanceUntilIdle()
        assertEquals(1, repository.openCount)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(viewModel.uiState.value.workspace!!.showPageNumbers)
        assertEquals("Outro nome", viewModel.uiState.value.document!!.name)
    }

    @Test fun backWithChangesRequiresExplicitDiscard() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        viewModel.onEvent(DocumentToolsEvent.RequestBack)
        assertTrue(viewModel.uiState.value.confirmDiscard)
        assertEquals(0, repository.discards)
        val navigation = async { viewModel.effects.first() }
        viewModel.onEvent(DocumentToolsEvent.ConfirmDiscard)
        advanceUntilIdle()
        assertEquals(DocumentToolsEffect.NavigateBack, navigation.await())
        assertEquals(1, repository.discards)
        assertNull(viewModel.uiState.value.workspace)
    }

    @Test fun protectedDocumentNeverOpensOrPreviewsBeforeAuthentication() = runTest(dispatcher) {
        val protected = document.copy(protectionKeyId = "key-1")
        viewModel.load(protected)
        runCurrent()
        assertTrue(viewModel.uiState.value.isLocked)
        assertEquals(0, repository.openCount)
        assertTrue(repository.previews.isEmpty())

        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLocked)
        assertEquals("secret", repository.lastOpenPassword)
        assertNotNull(viewModel.uiState.value.previewUri)
    }

    @Test fun backgroundClearsProtectedPreviewButPreservesRecipe() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key-1"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        advanceUntilIdle()
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)
        assertNull(viewModel.uiState.value.previewUri)
        assertTrue(viewModel.uiState.value.workspace!!.showPageNumbers)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(repository.clearedPreviews > 0)

        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.previewUri)
        assertTrue(viewModel.uiState.value.workspace!!.showPageNumbers)
    }

    @Test fun protectedScannerResultWaitsForUnlockBeforeAppending() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key-1"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.AddPages)
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        viewModel.onEvent(DocumentToolsEvent.ScanCompleted(listOf("content://new-page")))
        advanceUntilIdle()
        assertEquals(0, repository.appendCount)
        assertNull(viewModel.uiState.value.previewUri)

        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()
        assertEquals(1, repository.appendCount)
        assertEquals(4, viewModel.uiState.value.workspace!!.pages.size)
        assertTrue(viewModel.uiState.value.hasChanges)
    }

    @Test fun signatureDraftPreservesAspectRatioAndIsNotBakedTwice() = runTest(dispatcher) {
        signatures.values.value = listOf(SignatureModel("signature", "Minha assinatura", "file:///signature.png", 3f))
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.InsertSignature("signature"))
        advanceUntilIdle()
        val initial = viewModel.uiState.value.signatureDraft!!
        assertEquals("file:///workspace/copied-signature.png", initial.imageUri)
        assertTrue(initial.width >= .5f)
        assertEquals(.5f, initial.x + initial.width / 2f, .0001f)
        assertEquals(3f, initial.width * 595f / (initial.height * 842f), .0001f)
        assertTrue(repository.previews.last().pages.all { it.signatures.isEmpty() })
        assertFalse(viewModel.uiState.value.hasChanges)

        viewModel.onEvent(DocumentToolsEvent.ResizeSignature(0.6f))
        viewModel.onEvent(DocumentToolsEvent.MoveSignature(2f, 2f))
        val moved = viewModel.uiState.value.signatureDraft!!
        assertEquals(initial.height / initial.width, moved.height / moved.width, 0.0001f)
        assertEquals(1f, moved.x + moved.width, 0.0001f)
        assertEquals(1f, moved.y + moved.height, 0.0001f)

        viewModel.onEvent(DocumentToolsEvent.ConfirmSignature)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.workspace!!.pages.first().signatures.size)
        assertEquals(1, repository.previews.last().pages.first().signatures.size)

        viewModel.onEvent(DocumentToolsEvent.EditSignature("page-1", moved.id))
        advanceUntilIdle()
        assertTrue(repository.previews.last().pages.first().signatures.isEmpty())
        viewModel.onEvent(DocumentToolsEvent.CancelSignature)
        advanceUntilIdle()
        assertEquals(1, repository.previews.last().pages.first().signatures.size)
    }

    @Test fun savingProtectionUsesVersionedPasswordAccess() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetProtection(true))
        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertEquals("key-1", repository.lastSaveKey)
        assertEquals("secret", repository.lastSavePassword)
        assertTrue(passwords.requestedKeys.contains("key-1"))
    }

    @Test fun missingPasswordVersionOffersManualRecoveryWithoutOpeningPdf() = runTest(dispatcher) {
        passwords.unlock()
        passwords.missingVersion = true
        viewModel.load(document.copy(protectionKeyId = "old-key"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.recoveryRequired)
        assertTrue(viewModel.uiState.value.isLocked)
        assertEquals(0, repository.openCount)
        assertNull(viewModel.uiState.value.previewUri)

        passwords.missingVersion = false
        viewModel.onEvent(DocumentToolsEvent.RecoverWithPassword("original-password", "recovered-key"))
        advanceUntilIdle()
        assertEquals("original-password", repository.lastOpenPassword)
        assertFalse(viewModel.uiState.value.isLocked)
        assertEquals("recovered-key", viewModel.uiState.value.workspace!!.protectionKeyId)
        assertTrue(viewModel.uiState.value.hasChanges)
    }

    @Test fun confirmingUnchangedExistingSignatureRestoresItInPreview() = runTest(dispatcher) {
        signatures.values.value = listOf(SignatureModel("signature", "Minha assinatura", "file:///signature.png", 3f))
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.InsertSignature("signature"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ConfirmSignature)
        advanceUntilIdle()
        val placement = viewModel.uiState.value.workspace!!.pages.first().signatures.single()
        viewModel.onEvent(DocumentToolsEvent.EditSignature("page-1", placement.id))
        advanceUntilIdle()
        assertTrue(repository.previews.last().pages.first().signatures.isEmpty())
        viewModel.onEvent(DocumentToolsEvent.ConfirmSignature)
        advanceUntilIdle()
        assertEquals(1, repository.previews.last().pages.first().signatures.size)
    }

    @Test fun removingProtectionAuthenticatesOriginalVersionAndUsesExplicitFlag() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "original-key"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetProtection(false))
        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.lastRemoveProtection)
        assertEquals("secret", repository.lastSavePassword)
        assertEquals("original-key", passwords.requestedKeys.last())
        assertNull(viewModel.uiState.value.document!!.protectionKeyId)
    }

    @Test fun distantMoveAndDragCommitPreserveSelectionWithoutRerenderingUnchangedPage() = runTest(dispatcher) {
        viewModel.load(document.copy(pageCount = 100))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SelectPage("page-1"))
        val before = repository.previews.size

        viewModel.onEvent(DocumentToolsEvent.MovePageTo("page-1", 99))
        advanceUntilIdle()
        assertEquals("page-1", viewModel.uiState.value.workspace!!.pages.last().id)
        assertEquals("page-1", viewModel.uiState.value.selectedPageId)
        assertEquals(before, repository.previews.size)

        val reverseOrder = viewModel.uiState.value.workspace!!.pages.map { it.id }.reversed()
        viewModel.onEvent(DocumentToolsEvent.ReorderPages(reverseOrder))
        advanceUntilIdle()
        assertEquals(reverseOrder, viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertEquals("page-1", viewModel.uiState.value.selectedPageId)
        assertEquals(before, repository.previews.size)
    }

    @Test fun invalidMovesAndIncompleteOrDuplicateReordersLeaveRecipeUntouched() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val before = repository.previews.size

        viewModel.onEvent(DocumentToolsEvent.MovePageTo("unknown", 1))
        viewModel.onEvent(DocumentToolsEvent.MovePageTo("page-1", -1))
        viewModel.onEvent(DocumentToolsEvent.MovePageTo("page-1", 3))
        viewModel.onEvent(DocumentToolsEvent.ReorderPages(listOf("page-1", "page-2")))
        viewModel.onEvent(DocumentToolsEvent.ReorderPages(listOf("page-1", "page-1", "page-3")))
        viewModel.onEvent(DocumentToolsEvent.ReorderPages(listOf("page-1", "page-2", "unknown")))
        advanceUntilIdle()

        assertEquals(original, viewModel.uiState.value.workspace)
        assertEquals(before, repository.previews.size)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun numberingRemainsEnabledAfterMovingPagesAndSaving() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.PAGES))
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        viewModel.onEvent(DocumentToolsEvent.MovePageTo("page-3", 0))
        advanceUntilIdle()
        assertEquals(DocumentTool.PAGES, viewModel.uiState.value.tool)
        assertTrue(repository.previews.last().showPageNumbers)
        viewModel.onEvent(DocumentToolsEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.saves.single().showPageNumbers)
        assertEquals("page-3", repository.saves.single().pages.first().id)
    }

    @Test fun mergeAppendsInPickerOrderWithoutSavingAndReopensPages() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        runCurrent()
        assertEquals(DocumentToolsEffect.LaunchPdfMergePicker, effect.await())
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Picking)

        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://second", "content://first")))
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(listOf("content://second", "content://first"), state.workspace!!.pages.takeLast(2).map { it.sourceUri })
        assertEquals("merged-0", state.selectedPageId)
        assertNull(state.tool)
        assertEquals(DocumentToolsMessage.MERGED, state.message)
        assertTrue(state.hasChanges)
        assertFalse(state.isBusy)
        assertEquals(3, state.document!!.pageCount)
        assertTrue(repository.saves.isEmpty())
    }

    @Test fun mergePickerCancellationEmptyResultAndFailureNeverChangePages() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsCancelled)
        assertFalse(viewModel.uiState.value.isBusy)
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(emptyList()))
        assertFalse(viewModel.uiState.value.isBusy)
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsFailed)
        advanceUntilIdle()

        assertEquals(original, viewModel.uiState.value.workspace)
        assertEquals(0, repository.mergeCount)
        assertEquals(DocumentToolsMessage.MERGE_ERROR, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun failedMergePreservesExistingChangesAndAllowsRetry() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.failMerge = true
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://broken")))
        advanceUntilIdle()

        assertEquals(original, viewModel.uiState.value.workspace)
        assertEquals(DocumentToolsMessage.MERGE_ERROR, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isBusy)
        repository.failMerge = false
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://good")))
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.workspace!!.pages.size)
        assertTrue(viewModel.uiState.value.workspace!!.showPageNumbers)
    }

    @Test fun mergeLimitErrorPreservesRecipeAndExplainsWhyMergeFailed() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.mergeFailure = PdfMergeLimitException(PdfMergeLimitException.Reason.PAGE_COUNT)
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://too-large")))
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertEquals(DocumentToolsMessage.MERGE_LIMIT_ERROR, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun mergePromptsForEachPasswordAndWrongPasswordDoesNotPartiallyAppend() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.requiredMergePasswords = mapOf("content://one" to "first", "content://two" to "second")
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://one", "content://two")))
        advanceUntilIdle()
        assertEquals("content://one", (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertFalse((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.incorrect == true)
        assertFalse(viewModel.uiState.value.canEdit)

        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("wrong"))
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.incorrect == true)
        assertEquals(original, viewModel.uiState.value.workspace)
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("first"))
        advanceUntilIdle()
        assertEquals("content://two", (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertFalse((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.incorrect == true)
        assertEquals(original, viewModel.uiState.value.workspace)
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("second"))
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.workspace!!.pages.size)
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.workspace!!.isProtected)
    }

    @Test fun cancellingMergePasswordClearsPendingFilesAndIgnoresLateSubmission() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.requiredMergePasswords = mapOf("content://locked" to "password")
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://locked")))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.CancelMergePassword)
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("password"))
        advanceUntilIdle()
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertEquals(1, repository.mergeCount)
    }

    @Test fun protectedMergePickerResultWaitsForUnlock() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key-1"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://new")))
        advanceUntilIdle()
        assertEquals(0, repository.mergeCount)
        assertTrue(viewModel.uiState.value.isLocked)
        assertNull(viewModel.uiState.value.previewUri)
        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()
        assertEquals(1, repository.mergeCount)
        assertEquals(4, viewModel.uiState.value.workspace!!.pages.size)
        assertEquals("key-1", viewModel.uiState.value.workspace!!.protectionKeyId)
        assertTrue(viewModel.uiState.value.workspace!!.isProtected)
    }

    @Test fun lockedMergeRequestOpensPickerOnlyAfterAuthentication() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key-1"))
        advanceUntilIdle()
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        val unlockEffect = async { viewModel.effects.first() }
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        runCurrent()
        assertEquals(DocumentToolsEffect.NeedUnlock, unlockEffect.await())
        assertFalse(viewModel.uiState.value.merge is DocumentMergeUiState.Picking)
        passwords.unlock()
        val pickerEffect = async { viewModel.effects.first() }
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        runCurrent()
        assertEquals(DocumentToolsEffect.LaunchPdfMergePicker, pickerEffect.await())
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Picking)
        viewModel.onEvent(DocumentToolsEvent.MergePdfsCancelled)
        advanceUntilIdle()
    }

    @Test fun mergingBlocksEditsAndCancellationLeavesOriginalRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val gate = CompletableDeferred<Unit>()
        repository.mergeGate = gate
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://slow")))
        runCurrent()
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Merging)
        viewModel.onEvent(DocumentToolsEvent.MovePageTo("page-1", 2))
        viewModel.onEvent(DocumentToolsEvent.ReorderPages(listOf("page-3", "page-2", "page-1")))
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        viewModel.onEvent(DocumentToolsEvent.Save)
        viewModel.onEvent(DocumentToolsEvent.CancelPdfMerge)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertTrue(repository.saves.isEmpty())
    }

    @Test fun switchingDocumentsCancelsMergeAndDropsLatePickerOrPasswordResults() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        repository.mergeGate = CompletableDeferred()
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://slow")))
        runCurrent()
        viewModel.load(document.copy(id = 8, pageCount = 2))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://late")))
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("late"))
        advanceUntilIdle()
        assertEquals(8L, viewModel.uiState.value.workspace!!.documentId)
        assertEquals(2, viewModel.uiState.value.workspace!!.pages.size)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun backgroundDuringPasswordPromptDropsEarlierPasswordsAndRetainsUris() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key-1"))
        advanceUntilIdle()
        repository.requiredMergePasswords = mapOf("content://one" to "first", "content://two" to "second")
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://one", "content://two")))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("first"))
        advanceUntilIdle()
        assertEquals("content://two", (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()
        assertEquals("content://one", (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertFalse((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.incorrect == true)
        assertEquals(3, viewModel.uiState.value.workspace!!.pages.size)
    }

    @Test fun unprotectedBackgroundCancelsPasswordPromptAndDropsSubmittedCredentials() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.requiredMergePasswords = mapOf("content://one" to "first", "content://two" to "second")
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://one", "content://two")))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("first"))
        advanceUntilIdle()
        assertEquals("content://two", (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)

        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("second"))
        advanceUntilIdle()
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.isLocked)
        assertEquals(original, viewModel.uiState.value.workspace)

        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://one", "content://two")))
        advanceUntilIdle()
        assertEquals("content://one", (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
    }

    @Test fun unprotectedBackgroundCancelsRunningMergeThatContainsPassword() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.requiredMergePasswords = mapOf("content://one" to "first")
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://one")))
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.mergeGate = gate
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("first"))
        runCurrent()
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Merging)

        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun unprotectedBackgroundKeepsPickerWithoutCredentialsActive() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Picking)
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://normal")))
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.workspace!!.pages.size)
        assertEquals(DocumentToolsMessage.MERGED, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test fun mergeInsertsWholeOrderedBlockAtBeginningMiddleAndEndWithOnePreview() = runTest(dispatcher) {
        repository.mergePagesPerSource = 2
        for (insertionIndex in listOf(0, 1, 3)) {
            viewModel.load(document.copy(id = 20L + insertionIndex))
            advanceUntilIdle()
            val existing = viewModel.uiState.value.workspace!!.pages
            val previewsBefore = repository.previews.size
            viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.MERGE))
            assertNull(viewModel.uiState.value.tool)
            assertEquals(DocumentToolsEffect.NavigateMergeEditor(20L + insertionIndex), viewModel.effects.first())

            viewModel.onEvent(DocumentToolsEvent.MergeItems(
                listOf(
                    DocumentMergeItem("content://first", "Primeiro.pdf"),
                    DocumentMergeItem("content://second", "Segundo.pdf"),
                ),
                insertionIndex,
            ))
            advanceUntilIdle()
            val state = viewModel.uiState.value
            val pages = state.workspace!!.pages
            val inserted = pages.subList(insertionIndex, insertionIndex + 4)
            assertEquals(listOf("merged-0", "merged-1", "merged-2", "merged-3"), inserted.map { it.id })
            assertEquals(listOf("content://first", "content://first", "content://second", "content://second"), inserted.map { it.sourceUri })
            assertEquals(listOf(0, 1, 0, 1), inserted.map { it.sourcePageIndex })
            assertEquals(existing.take(insertionIndex) + existing.drop(insertionIndex), pages.filterNot { it.id.startsWith("merged-") })
            assertEquals(existing.take(insertionIndex).map { it.id } + inserted.map { it.id } + existing.drop(insertionIndex).map { it.id }, pages.map { it.id })
            assertEquals("merged-0", state.selectedPageId)
            assertEquals(insertionIndex, state.selectedPageIndex)
            assertEquals(previewsBefore + 1, repository.previews.size)
            assertNull(state.tool)
            assertTrue(state.hasChanges)
            assertEquals(DocumentToolsEffect.MergeCompleted(20L + insertionIndex), viewModel.effects.first())
        }
        assertTrue(repository.saves.isEmpty())
    }

    @Test fun invalidMergeInsertionDoesNotLaunchPickerOrModifyPages() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        for (index in listOf(-1, 4, Int.MAX_VALUE)) {
            viewModel.onEvent(DocumentToolsEvent.MergePdfs(index))
            viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://ignored")))
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.merge is DocumentMergeUiState.Picking)
            assertFalse(viewModel.uiState.value.isBusy)
            assertEquals(original, viewModel.uiState.value.workspace)
            assertEquals(DocumentToolsMessage.MERGE_ERROR, viewModel.uiState.value.message)
        }
        assertEquals(0, repository.mergeCount)
    }

    @Test fun secondMergeRequestCannotChangePositionWhilePickerIsOpen() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 1))
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 0))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://new")))
        advanceUntilIdle()
        assertEquals(listOf("page-1", "merged-0", "page-2", "page-3"), viewModel.uiState.value.workspace!!.pages.map { it.id })
    }

    @Test fun cancellingMergeDropsOldPositionBeforeStartingAgain() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 0))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsCancelled)
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://new")))
        advanceUntilIdle()
        assertEquals(listOf("page-1", "page-2", "page-3", "merged-0"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertEquals(3, viewModel.uiState.value.selectedPageIndex)
    }

    @Test fun changingDocumentWhilePickerIsOpenInvalidatesItsInsertionPosition() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 3))
        viewModel.load(document.copy(id = 30, pageCount = 1))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://late")))
        advanceUntilIdle()
        assertEquals(0, repository.mergeCount)
        assertEquals(1, viewModel.uiState.value.workspace!!.pages.size)

        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 0))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://current")))
        advanceUntilIdle()
        assertEquals(listOf("merged-0", "page-1"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertEquals(30L, viewModel.uiState.value.workspace!!.documentId)
    }

    @Test fun insertionSurvivesWrongPasswordsAndReauthenticationDuringBatch() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key-1"))
        advanceUntilIdle()
        repository.requiredMergePasswords = mapOf("content://one" to "first", "content://two" to "second")
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 1))
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://one", "content://two")))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("wrong"))
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.incorrect == true)
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("first"))
        advanceUntilIdle()
        assertEquals("content://two", (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)

        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()
        assertEquals("content://one", (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("first"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("second"))
        advanceUntilIdle()
        assertEquals(listOf("page-1", "merged-0", "merged-1", "page-2", "page-3"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertEquals(1, viewModel.uiState.value.selectedPageIndex)
        assertEquals("key-1", viewModel.uiState.value.workspace!!.protectionKeyId)
    }

    @Test fun insertionRequestedWhileLockedIsRetainedUntilPickerReturns() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document.copy(protectionKeyId = "key-1"))
        advanceUntilIdle()
        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 2))
        assertFalse(viewModel.uiState.value.merge is DocumentMergeUiState.Picking)
        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Picking)
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://new")))
        advanceUntilIdle()
        assertEquals(listOf("page-1", "page-2", "merged-0", "page-3"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertEquals(2, viewModel.uiState.value.selectedPageIndex)
    }

    @Test fun appDocumentsMergeKeepsSelectionOrderAndInsertionAndCreatesOneUndoStep() = runTest(dispatcher) {
        repository.mergePagesPerSource = 2
        val first = document.copy(id = 40, pdfUri = "file:///first.pdf")
        val second = document.copy(id = 41, pdfUri = "file:///second.pdf")
        for (insertionIndex in listOf(0, 1, 3)) {
            viewModel.load(document.copy(id = 20L + insertionIndex))
            advanceUntilIdle()
            val original = viewModel.uiState.value.workspace
            val previewsBefore = repository.previews.size

            viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(listOf(second, first), insertionIndex))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val inserted = state.workspace!!.pages.subList(insertionIndex, insertionIndex + 4)
            assertEquals(listOf(second.pdfUri, second.pdfUri, first.pdfUri, first.pdfUri), inserted.map { it.sourceUri })
            assertEquals(listOf(0, 1, 0, 1), inserted.map { it.sourcePageIndex })
            assertEquals(original!!.pages.take(insertionIndex) + inserted + original.pages.drop(insertionIndex), state.workspace.pages)
            assertEquals(inserted.first().id, state.selectedPageId)
            assertEquals(insertionIndex, state.selectedPageIndex)
            assertNull(state.tool)
            assertEquals(DocumentToolsMessage.MERGED, state.message)
            assertEquals(previewsBefore + 1, repository.previews.size)
            assertTrue(state.canUndo)
            assertTrue(state.hasChanges)
            assertFalse(state.isBusy)
            assertFalse(state.merge is DocumentMergeUiState.Picking)
            assertEquals(3, state.document!!.pageCount)

            viewModel.onEvent(DocumentToolsEvent.Undo)
            advanceUntilIdle()
            assertEquals(original, viewModel.uiState.value.workspace)
            assertFalse(viewModel.uiState.value.canUndo)
            assertFalse(viewModel.uiState.value.hasChanges)
        }
        assertTrue(repository.saves.isEmpty())
    }

    @Test fun appDocumentsMergeExcludesCurrentBlankAndDuplicateDocumentsByIdOrUri() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val first = document.copy(id = 10, pdfUri = "file:///first.pdf")
        val second = document.copy(id = 12, pdfUri = "file:///second.pdf")

        viewModel.onEvent(
            DocumentToolsEvent.MergeAppDocuments(
                listOf(
                    document,
                    document.copy(pdfUri = "file:///same-id.pdf"),
                    document.copy(id = 8),
                    document.copy(id = 9, pdfUri = " "),
                    first,
                    first.copy(pdfUri = "file:///duplicate-id.pdf"),
                    first.copy(id = 11),
                    second,
                ),
                insertionIndex = 3,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, repository.mergeCount)
        assertEquals(listOf(first.pdfUri, second.pdfUri), viewModel.uiState.value.workspace!!.pages.takeLast(2).map { it.sourceUri })
        assertTrue(passwords.requestedKeys.isEmpty())
    }

    @Test fun appDocumentsMergeEmptyOrExcludedSelectionDoesNotChangeRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        for (selection in listOf(emptyList(), listOf(document), listOf(document.copy(id = 8, pdfUri = "")))) {
            viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(selection, insertionIndex = 0))
            advanceUntilIdle()
            assertEquals(original, viewModel.uiState.value.workspace)
            assertFalse(viewModel.uiState.value.isBusy)
            assertFalse(viewModel.uiState.value.hasChanges)
            assertFalse(viewModel.uiState.value.canUndo)
        }
        assertEquals(0, repository.mergeCount)
    }

    @Test fun appDocumentsMergeRejectsInvalidInsertionWithoutChangingRecipe() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val source = document.copy(id = 8, pdfUri = "file:///other.pdf")
        for (insertionIndex in listOf(-1, 4, Int.MAX_VALUE)) {
            viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(listOf(source), insertionIndex))
            advanceUntilIdle()
            assertEquals(original, viewModel.uiState.value.workspace)
            assertEquals(DocumentToolsMessage.MERGE_ERROR, viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.isBusy)
        }
        assertEquals(0, repository.mergeCount)
    }

    @Test fun appDocumentsRequestDoesNotReplaceActiveDevicePickerOrItsInsertion() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 1))
        viewModel.onEvent(
            DocumentToolsEvent.MergeAppDocuments(
                listOf(document.copy(id = 8, pdfUri = "file:///ignored.pdf")), insertionIndex = 0,
            ),
        )
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Picking)
        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://chosen")))
        advanceUntilIdle()

        assertEquals(1, repository.mergeCount)
        assertEquals(listOf("page-1", "merged-0", "page-2", "page-3"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertEquals("content://chosen", viewModel.uiState.value.workspace!!.pages[1].sourceUri)
    }

    @Test fun cancellingAppMergeDropsLateResultAndIgnoresRequestsWhileBusy() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val gate = CompletableDeferred<Unit>()
        repository.mergeGate = gate
        repository.mergeIgnoresCancellation = true
        viewModel.onEvent(
            DocumentToolsEvent.MergeAppDocuments(
                listOf(document.copy(id = 8, pdfUri = "file:///slow.pdf")), insertionIndex = 1,
            ),
        )
        runCurrent()
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Merging)
        viewModel.onEvent(
            DocumentToolsEvent.MergeAppDocuments(
                listOf(document.copy(id = 9, pdfUri = "file:///ignored.pdf")), insertionIndex = 0,
            ),
        )
        viewModel.onEvent(DocumentToolsEvent.CancelPdfMerge)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.mergeCount)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun protectedAppDocumentsUseTheirOwnPasswordVersionsWithoutProtectingDestination() = runTest(dispatcher) {
        passwords.unlock()
        passwords.passwordsByKey = mapOf("old-key" to "old-secret", "new-key" to "new-secret")
        viewModel.load(document)
        advanceUntilIdle()
        val old = document.copy(id = 8, pdfUri = "file:///old.pdf", protectionKeyId = "old-key")
        val normal = document.copy(id = 9, pdfUri = "file:///normal.pdf")
        val newest = document.copy(id = 10, pdfUri = "file:///new.pdf", protectionKeyId = "new-key")
        repository.requiredMergePasswords = mapOf(old.pdfUri to "old-secret", newest.pdfUri to "new-secret")

        viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(listOf(old, normal, newest), insertionIndex = 3))
        advanceUntilIdle()

        assertEquals(listOf("old-key", "new-key"), passwords.requestedKeys)
        assertEquals(listOf(old.pdfUri, normal.pdfUri, newest.pdfUri), viewModel.uiState.value.workspace!!.pages.takeLast(3).map { it.sourceUri })
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertNull(viewModel.uiState.value.workspace!!.protectionKeyId)
        assertFalse(viewModel.uiState.value.workspace!!.isProtected)
        assertEquals(DocumentToolsMessage.MERGED, viewModel.uiState.value.message)
    }

    @Test fun missingAppPasswordVersionOffersNamedManualPromptAndResumesMerge() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document)
        advanceUntilIdle()
        passwords.missingVersion = true
        val source = document.copy(id = 8, name = "Arquivo antigo", pdfUri = "file:///old.pdf", protectionKeyId = "missing-key")
        repository.requiredMergePasswords = mapOf(source.pdfUri to "manual-secret")

        viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(listOf(source), insertionIndex = 1))
        advanceUntilIdle()
        assertEquals(0, repository.mergeCount)
        assertEquals(source.pdfUri, (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertEquals(source.name, (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.name)
        assertFalse(viewModel.uiState.value.recoveryRequired)

        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("manual-secret"))
        advanceUntilIdle()
        assertEquals(1, repository.mergeCount)
        assertEquals(source.pdfUri, viewModel.uiState.value.workspace!!.pages[1].sourceUri)
        assertEquals(DocumentToolsMessage.MERGED, viewModel.uiState.value.message)
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.name)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test fun rejectedAppVaultPasswordCanBeReplacedManuallyWithoutReusingStoredPassword() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document)
        advanceUntilIdle()
        val source = document.copy(id = 8, name = "Contrato protegido", pdfUri = "file:///protected.pdf", protectionKeyId = "source-key")
        repository.requiredMergePasswords = mapOf(source.pdfUri to "manual-secret")

        viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(listOf(source), insertionIndex = 2))
        advanceUntilIdle()
        assertEquals(1, repository.mergeCount)
        assertEquals(source.pdfUri, (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertEquals(source.name, (viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.name)

        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("manual-secret"))
        advanceUntilIdle()
        assertEquals(2, repository.mergeCount)
        assertEquals(listOf("source-key"), passwords.requestedKeys)
        assertEquals(source.pdfUri, viewModel.uiState.value.workspace!!.pages[2].sourceUri)
        assertEquals(DocumentToolsMessage.MERGED, viewModel.uiState.value.message)
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test fun protectedAppSelectionWaitsForVaultUnlockThenResumesAtRequestedInsertion() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val source = document.copy(id = 8, pdfUri = "file:///protected.pdf", protectionKeyId = "source-key")
        repository.requiredMergePasswords = mapOf(source.pdfUri to "secret")
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(listOf(source), insertionIndex = 1))
        runCurrent()
        assertEquals(DocumentToolsEffect.NeedUnlock, effect.await())
        assertEquals(0, repository.mergeCount)
        assertTrue(passwords.requestedKeys.isEmpty())
        assertFalse(viewModel.uiState.value.merge is DocumentMergeUiState.Picking)
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.AwaitingUnlock)
        assertTrue(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.canEdit)
        viewModel.onEvent(
            DocumentToolsEvent.MergeAppDocuments(
                listOf(document.copy(id = 9, pdfUri = "file:///ignored.pdf")), insertionIndex = 0,
            ),
        )

        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()
        assertEquals(1, repository.mergeCount)
        assertEquals(listOf("source-key"), passwords.requestedKeys)
        assertEquals(listOf("page-1", "merged-0", "page-2", "page-3"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertEquals(1, viewModel.uiState.value.selectedPageIndex)
        assertFalse(viewModel.uiState.value.merge is DocumentMergeUiState.AwaitingUnlock)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test fun dismissingAppSelectionAuthenticationDiscardsSelectionBeforeLaterUnlock() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val source = document.copy(id = 8, pdfUri = "file:///protected.pdf", protectionKeyId = "source-key")
        viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(listOf(source), insertionIndex = 1))
        runCurrent()

        viewModel.onEvent(DocumentToolsEvent.AuthenticationDismissed)
        assertFalse(viewModel.uiState.value.merge is DocumentMergeUiState.AwaitingUnlock)
        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()

        assertEquals(0, repository.mergeCount)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertTrue(passwords.requestedKeys.isEmpty())
    }

    @Test fun backgroundCancelsProtectedAppSourceForUnprotectedDestinationAndDropsLateResult() = runTest(dispatcher) {
        passwords.unlock()
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val source = document.copy(id = 8, pdfUri = "file:///protected.pdf", protectionKeyId = "source-key")
        val gate = CompletableDeferred<Unit>()
        repository.mergeGate = gate
        repository.mergeIgnoresCancellation = true
        viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(listOf(source), insertionIndex = 1))
        runCurrent()
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Merging)

        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        gate.complete(Unit)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()

        assertEquals(1, repository.mergeCount)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.isLocked)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun protectedDestinationRetriesAppMergeAfterBackgroundWithFreshVersionedPassword() = runTest(dispatcher) {
        passwords.unlock()
        passwords.passwordsByKey = mapOf("source-key" to "before-background")
        viewModel.load(document.copy(protectionKeyId = "key-1"))
        advanceUntilIdle()
        val source = document.copy(id = 8, pdfUri = "file:///protected.pdf", protectionKeyId = "source-key")
        val gate = CompletableDeferred<Unit>()
        repository.mergeGate = gate
        viewModel.onEvent(DocumentToolsEvent.MergeAppDocuments(listOf(source), insertionIndex = 2))
        runCurrent()
        assertTrue(viewModel.uiState.value.merge is DocumentMergeUiState.Merging)

        passwords.lock()
        viewModel.onEvent(DocumentToolsEvent.Backgrounded)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)
        assertNull(viewModel.uiState.value.previewUri)
        assertEquals(3, viewModel.uiState.value.workspace!!.pages.size)

        passwords.passwordsByKey = mapOf("source-key" to "after-background")
        repository.requiredMergePasswords = mapOf(source.pdfUri to "after-background")
        gate.complete(Unit)
        passwords.unlock()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()

        assertEquals(2, repository.mergeCount)
        assertEquals(2, passwords.requestedKeys.count { it == "source-key" })
        assertEquals(listOf("page-1", "page-2", "merged-0", "page-3"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertEquals("key-1", viewModel.uiState.value.workspace!!.protectionKeyId)
        assertEquals(DocumentToolsMessage.MERGED, viewModel.uiState.value.message)
        assertNull((viewModel.uiState.value.merge as? DocumentMergeUiState.PasswordRequired)?.uri)
        assertFalse(viewModel.uiState.value.isLocked)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test fun switchingDocumentsDropsLateAppMergeResultAndPendingSelection() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.mergeGate = gate
        repository.mergeIgnoresCancellation = true
        viewModel.onEvent(
            DocumentToolsEvent.MergeAppDocuments(
                listOf(document.copy(id = 8, pdfUri = "file:///slow.pdf")), insertionIndex = 1,
            ),
        )
        runCurrent()

        viewModel.load(document.copy(id = 9, pageCount = 2))
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.AuthenticationSucceeded)
        advanceUntilIdle()

        assertEquals(1, repository.mergeCount)
        assertEquals(9L, viewModel.uiState.value.workspace!!.documentId)
        assertEquals(listOf("page-1", "page-2"), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test fun mergePhasesAreExclusiveAndCancellationRejectsLatePasswordResult() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        repository.requiredMergePasswords = mapOf("content://protected" to "secret")
        val firstAttempt = CompletableDeferred<Unit>()
        repository.mergeGate = firstAttempt

        viewModel.onEvent(DocumentToolsEvent.MergePdfs(insertionIndex = 1))
        assertEquals(DocumentMergeUiState.Picking, viewModel.uiState.value.merge)
        assertFalse(viewModel.uiState.value.canEdit)

        viewModel.onEvent(DocumentToolsEvent.MergePdfsSelected(listOf("content://protected")))
        runCurrent()
        assertEquals(DocumentMergeUiState.Merging, viewModel.uiState.value.merge)
        assertTrue(viewModel.uiState.value.isBusy)

        firstAttempt.complete(Unit)
        advanceUntilIdle()
        assertEquals(
            DocumentMergeUiState.PasswordRequired(uri = "content://protected", name = null),
            viewModel.uiState.value.merge,
        )
        assertFalse(viewModel.uiState.value.canEdit)

        val passwordAttempt = CompletableDeferred<Unit>()
        repository.mergeGate = passwordAttempt
        repository.mergeIgnoresCancellation = true
        viewModel.onEvent(DocumentToolsEvent.SubmitMergePassword("secret"))
        runCurrent()
        assertEquals(DocumentMergeUiState.Merging, viewModel.uiState.value.merge)

        viewModel.onEvent(DocumentToolsEvent.CancelPdfMerge)
        assertEquals(DocumentMergeUiState.Idle, viewModel.uiState.value.merge)
        assertTrue(viewModel.uiState.value.canEdit)
        passwordAttempt.complete(Unit)
        advanceUntilIdle()

        assertEquals(DocumentMergeUiState.Idle, viewModel.uiState.value.merge)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
        assertFalse(viewModel.uiState.value.canUndo)
    }


    @Test fun signatureToolOpensDedicatedScreenWithoutChangingTheWorkspace() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.OpenTool(DocumentTool.SIGNATURES))

        assertEquals(DocumentToolsEffect.NavigateSignatureEditor(document.id), viewModel.effects.first())
        assertNull(viewModel.uiState.value.tool)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun signatureTransformCommitsInOneUndoStepWithoutSavingTheDocument() = runTest(dispatcher) {
        signatures.values.value = listOf(SignatureModel("signature", "Minha assinatura", "file:///signature.png", 3f))
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.InsertSignature("signature"))
        advanceUntilIdle()
        val draft = viewModel.uiState.value.signatureDraft!!

        viewModel.onEvent(DocumentToolsEvent.TransformSignature(.3f, .4f, .5f, 45f))
        val transformed = viewModel.uiState.value.signatureDraft!!
        assertEquals(45f, transformed.rotationDegrees, .0001f)
        assertEquals(draft.height / draft.width, transformed.height / transformed.width, .0001f)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.canUndo)

        viewModel.onEvent(DocumentToolsEvent.ConfirmSignature)
        advanceUntilIdle()
        assertEquals(transformed, viewModel.uiState.value.workspace!!.pages.first().signatures.single())
        assertTrue(viewModel.uiState.value.canUndo)
        assertTrue(repository.saves.isEmpty())

        viewModel.onEvent(DocumentToolsEvent.EditSignature("page-1", transformed.id))
        assertEquals(DocumentToolsEffect.NavigateSignatureEditor(document.id), viewModel.effects.first())
        viewModel.onEvent(DocumentToolsEvent.CancelSignature)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun independentSignatureDimensionsSurviveMovesProportionalResizeAndCommit() = runTest(dispatcher) {
        signatures.values.value = listOf(SignatureModel("signature", "Minha assinatura", "file:///signature.png", 3f))
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.InsertSignature("signature"))
        advanceUntilIdle()

        viewModel.onEvent(DocumentToolsEvent.TransformSignature(
            .2f, .3f, .55f, 25f, height = .22f, stretchToBounds = true,
        ))
        val resized = viewModel.uiState.value.signatureDraft!!
        assertEquals(.55f, resized.width, .0001f)
        assertEquals(.22f, resized.height, .0001f)
        assertEquals(25f, resized.rotationDegrees, .0001f)
        assertTrue(resized.stretchToBounds)
        assertEquals(original, viewModel.uiState.value.workspace)

        viewModel.onEvent(DocumentToolsEvent.MoveSignature(.1f, .4f))
        viewModel.onEvent(DocumentToolsEvent.ResizeSignature(.6f))
        val moved = viewModel.uiState.value.signatureDraft!!
        assertEquals(resized.height / resized.width, moved.height / moved.width, .0001f)
        assertTrue(moved.stretchToBounds)
        viewModel.onEvent(DocumentToolsEvent.ConfirmSignature)
        advanceUntilIdle()
        assertEquals(moved, viewModel.uiState.value.workspace!!.pages.first().signatures.single())

        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
    }

    @Test fun tallAndWideSignaturesStartInsideThePageWithoutChangingTheirAspect() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        for (aspect in listOf(.2f, 12f)) {
            signatures.values.value = listOf(SignatureModel("signature", "Assinatura", "file:///signature.png", aspect))
            advanceUntilIdle()
            viewModel.onEvent(DocumentToolsEvent.InsertSignature("signature"))
            advanceUntilIdle()
            val draft = viewModel.uiState.value.signatureDraft!!
            assertEquals(aspect, draft.width * 595f / (draft.height * 842f), .0001f)
            assertEquals(.5f, draft.x + draft.width / 2f, .0001f)
            assertTrue(draft.x >= 0f && draft.y >= 0f)
            assertTrue(draft.x + draft.width <= 1f && draft.y + draft.height <= 1f)
            viewModel.onEvent(DocumentToolsEvent.CancelSignature)
            advanceUntilIdle()
        }
    }

    @Test fun croppingCreatesOnlyADraftAndRetainsTheLibraryAndUndoSource() = runTest(dispatcher) {
        val library = SignatureModel("signature", "Minha assinatura", "file:///signature.png", 3f)
        signatures.values.value = listOf(library)
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.InsertSignature("signature"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.ConfirmSignature)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace!!
        val placed = original.pages.first().signatures.single()
        viewModel.onEvent(DocumentToolsEvent.EditSignature("page-1", placed.id))
        viewModel.onEvent(DocumentToolsEvent.TransformSignature(
            placed.x, placed.y, placed.width, 30f, height = .25f, stretchToBounds = true,
        ))
        val beforeCrop = viewModel.uiState.value.signatureDraft!!
        viewModel.onEvent(DocumentToolsEvent.CropSignature(.25f, 0f, .75f, 1f))
        assertTrue(viewModel.uiState.value.isCopyingSignature)
        advanceUntilIdle()

        val cropped = viewModel.uiState.value.signatureDraft!!
        assertEquals("file:///workspace/cropped-signature.png", cropped.imageUri)
        assertEquals(30f, cropped.rotationDegrees, .0001f)
        assertEquals(beforeCrop.height / beforeCrop.width * 2f, cropped.height / cropped.width, .0001f)
        assertTrue(cropped.stretchToBounds)
        assertEquals(PdfSignatureCrop(.25f, 0f, .75f, 1f), repository.lastCrop)
        assertEquals(placed.imageUri, repository.lastCropImageUri)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertEquals(listOf(library), signatures.values.value)

        viewModel.onEvent(DocumentToolsEvent.ConfirmSignature)
        advanceUntilIdle()
        assertEquals(cropped, viewModel.uiState.value.workspace!!.pages.first().signatures.single())
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
    }

    @Test fun croppingLegacySignatureUsesTheCroppedImageAspect() = runTest(dispatcher) {
        signatures.values.value = listOf(SignatureModel("signature", "Assinatura", "file:///signature.png", 3f))
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.InsertSignature("signature"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.CropSignature(.25f, 0f, .75f, 1f))
        advanceUntilIdle()
        val cropped = viewModel.uiState.value.signatureDraft!!
        assertFalse(cropped.stretchToBounds)
        assertEquals(repository.cropAspectRatio, cropped.width * 595f / (cropped.height * 842f), .0001f)
    }

    @Test fun identityCropDoesNotUndoIndependentSignatureDimensions() = runTest(dispatcher) {
        signatures.values.value = listOf(SignatureModel("signature", "Assinatura", "file:///signature.png", 3f))
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.InsertSignature("signature"))
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.TransformSignature(
            .2f, .3f, .5f, 0f, height = .2f, stretchToBounds = true,
        ))
        val beforeCrop = viewModel.uiState.value.signatureDraft!!
        viewModel.onEvent(DocumentToolsEvent.CropSignature(0f, 0f, 1f, 1f))
        advanceUntilIdle()
        val cropped = viewModel.uiState.value.signatureDraft!!
        assertEquals(beforeCrop.width, cropped.width, .0001f)
        assertEquals(beforeCrop.height, cropped.height, .0001f)
        assertEquals(beforeCrop.x, cropped.x, .0001f)
        assertEquals(beforeCrop.y, cropped.y, .0001f)
        assertTrue(cropped.stretchToBounds)
    }

    @Test fun canceledCropCannotRestoreItsDraftAfterTheFileReturns() = runTest(dispatcher) {
        signatures.values.value = listOf(SignatureModel("signature", "Minha assinatura", "file:///signature.png", 3f))
        viewModel.load(document)
        advanceUntilIdle()
        viewModel.onEvent(DocumentToolsEvent.InsertSignature("signature"))
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        val gate = CompletableDeferred<Unit>()
        repository.cropGate = gate

        viewModel.onEvent(DocumentToolsEvent.CropSignature(.25f, 0f, .75f, 1f))
        runCurrent()
        viewModel.onEvent(DocumentToolsEvent.CancelSignature)
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.signatureDraft)
        assertFalse(viewModel.uiState.value.isCopyingSignature)
        assertEquals(original, viewModel.uiState.value.workspace)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test fun dedicatedToolsNavigateButRestoringTheScreenDoesNotNavigateAgain() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        for (tool in listOf(DocumentTool.LAYOUT, DocumentTool.CORRECTIONS, DocumentTool.PAGES)) {
            viewModel.onEvent(DocumentToolsEvent.OpenTool(tool))
            assertEquals(DocumentToolsEffect.NavigateEditorTool(document.id, tool), viewModel.effects.first())
            assertEquals(tool, viewModel.uiState.value.tool)
            viewModel.onEvent(DocumentToolsEvent.DismissTool)

            val unexpected = async { viewModel.effects.first() }
            viewModel.onEvent(DocumentToolsEvent.ActivateEditorTool(tool))
            runCurrent()
            assertEquals(tool, viewModel.uiState.value.tool)
            assertFalse(unexpected.isCompleted)
            unexpected.cancel()
            viewModel.onEvent(DocumentToolsEvent.DismissTool)
        }
    }

    @Test fun layoutApplyClosesOnlyForValidTargetsAndKeepsUndoInTheSharedWorkspace() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace
        viewModel.onEvent(DocumentToolsEvent.ActivateEditorTool(DocumentTool.LAYOUT))
        val completion = async { viewModel.effects.first() }
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(
            PageAlignment.LEFT, PageScaleMode.FIT, false, pageIds = emptySet(),
        ))
        runCurrent()
        assertFalse(completion.isCompleted)
        assertEquals(DocumentTool.LAYOUT, viewModel.uiState.value.tool)
        assertEquals(original, viewModel.uiState.value.workspace)

        // Confirmar o layout original também encerra a etapa sem criar histórico artificial.
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(
            PageAlignment.CENTER, PageScaleMode.FIT, false, pageIds = setOf("page-1"),
        ))
        assertEquals(DocumentToolsEffect.EditorToolApplied(document.id, DocumentTool.LAYOUT), completion.await())
        assertNull(viewModel.uiState.value.tool)
        assertFalse(viewModel.uiState.value.canUndo)

        viewModel.onEvent(DocumentToolsEvent.ActivateEditorTool(DocumentTool.LAYOUT))
        viewModel.onEvent(DocumentToolsEvent.ApplyLayout(
            PageAlignment.LEFT, PageScaleMode.FIT, false, pageIds = setOf("page-1"),
        ))
        assertEquals(DocumentToolsEffect.EditorToolApplied(document.id, DocumentTool.LAYOUT), viewModel.effects.first())
        assertTrue(viewModel.uiState.value.canUndo)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(repository.saves.isEmpty())
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
    }

    @Test fun leavingThePageScreenPreservesImmediateEditsAndTheirUndoHistory() = runTest(dispatcher) {
        viewModel.load(document)
        advanceUntilIdle()
        val original = viewModel.uiState.value.workspace!!
        viewModel.onEvent(DocumentToolsEvent.ActivateEditorTool(DocumentTool.PAGES))
        viewModel.onEvent(DocumentToolsEvent.ReorderPages(original.pages.map { it.id }.reversed()))
        viewModel.onEvent(DocumentToolsEvent.SetNumbering(true))
        viewModel.onEvent(DocumentToolsEvent.DismissTool)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.tool)
        assertEquals(original.pages.map { it.id }.reversed(), viewModel.uiState.value.workspace!!.pages.map { it.id })
        assertTrue(viewModel.uiState.value.workspace!!.showPageNumbers)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(repository.saves.isEmpty())
        viewModel.onEvent(DocumentToolsEvent.Undo)
        viewModel.onEvent(DocumentToolsEvent.Undo)
        advanceUntilIdle()
        assertEquals(original, viewModel.uiState.value.workspace)
    }

    private class FakePasswords : PasswordAccess {
        override val state = MutableStateFlow(PasswordVaultState(isConfigured = true))
        override val activeKeyId = "key-1"
        val requestedKeys = mutableListOf<String?>()
        var missingVersion = false
        var passwordsByKey = emptyMap<String, String>()
        fun unlock() { state.value = state.value.copy(isUnlocked = true) }
        override fun lock() { state.value = state.value.copy(isUnlocked = false) }
        override suspend fun <T> withPassword(keyId: String?, block: suspend (String) -> T): T {
            if (!state.value.isUnlocked) throw PasswordVaultLockedException()
            if (missingVersion) throw PasswordVersionMissingException()
            requestedKeys += keyId
            return block(passwordsByKey[keyId] ?: "secret")
        }
    }

    private class FakeSignatureRepository : SignatureRepository {
        val values = MutableStateFlow(emptyList<SignatureModel>())
        override fun observeSignatures() = values
        override suspend fun createDrawing(name: String, strokes: List<SignatureStroke>): SignatureModel = error("Unused")
        override suspend fun createTyped(name: String, text: String, font: SignatureFont, colorArgb: Int): SignatureModel = error("Unused")
        override suspend fun importImage(name: String, uri: String, removeLightBackground: Boolean): SignatureModel = error("Unused")
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun delete(id: String) = Unit
    }

    private class FakePdfEditorRepository : PdfEditorRepository {
        var cropGate: CompletableDeferred<Unit>? = null
        var cropAspectRatio = 1.5f
        var lastCrop: PdfSignatureCrop? = null
        var lastCropImageUri: String? = null
        var openCount = 0
        var appendCount = 0
        var mergeCount = 0
        var mergePagesPerSource = 1
        var failMerge = false
        var mergeFailure: Exception? = null
        var mergeGate: CompletableDeferred<Unit>? = null
        var mergeIgnoresCancellation = false
        var requiredMergePasswords = emptyMap<String, String>()
        var discards = 0
        var clearedPreviews = 0
        var failSave = false
        var failPreview = false
        var lastOpenPassword: String? = null
        var lastSavePassword: String? = null
        var lastSaveKey: String? = null
        var lastRemoveProtection = false
        val saves = mutableListOf<PdfWorkspace>()
        val previews = mutableListOf<PdfWorkspace>()
        private lateinit var document: DocumentModel

        override suspend fun open(document: DocumentModel, password: String?): PdfWorkspace {
            openCount++
            this.document = document
            lastOpenPassword = password
            return PdfWorkspace(
                documentId = document.id,
                workspaceId = "workspace",
                directoryUri = "file:///workspace",
                pages = (1..document.pageCount).map { PdfPageSpec("page-$it", "file:///source.pdf", it - 1) },
                isProtected = document.protectionKeyId != null,
                protectionKeyId = document.protectionKeyId,
            )
        }
        override suspend fun importPdf(uri: String, password: String?, protectionKeyId: String?): ScannedDocument = error("Unused")
        override suspend fun appendScans(workspace: PdfWorkspace, pageUris: List<String>): PdfWorkspace {
            appendCount++
            return workspace.copy(pages = workspace.pages + pageUris.mapIndexed { index, uri -> PdfPageSpec("new-$index", uri, 0) })
        }
        override suspend fun appendPdfs(workspace: PdfWorkspace, sources: List<PdfMergeSource>): PdfWorkspace {
            mergeCount++
            mergeGate?.let { gate ->
                if (mergeIgnoresCancellation) withContext(NonCancellable) { gate.await() } else gate.await()
            }
            if (failMerge) error("Arquivo indisponível")
            mergeFailure?.let { throw it }
            sources.forEach { source ->
                val expected = requiredMergePasswords[source.uri]
                if (expected != null && expected != source.password) throw PdfMergePasswordRequiredException(source.uri)
            }
            return workspace.copy(pages = workspace.pages + sources.flatMapIndexed { index, source ->
                (0 until mergePagesPerSource).map { page ->
                    PdfPageSpec("merged-${index * mergePagesPerSource + page}", source.uri, page)
                }
            })
        }
        override suspend fun copySignature(workspace: PdfWorkspace, imageUri: String): String =
            "file:///workspace/copied-signature.png"
        override suspend fun cropSignature(workspace: PdfWorkspace, imageUri: String, crop: PdfSignatureCrop): PdfSignatureImage {
            lastCropImageUri = imageUri
            lastCrop = crop
            cropGate?.await()
            return PdfSignatureImage("file:///workspace/cropped-signature.png", cropAspectRatio)
        }
        override suspend fun preview(workspace: PdfWorkspace, retainedPreviewUri: String?): String {
            if (failPreview) error("Unable to render preview")
            previews += workspace
            return "file:///preview-${previews.size}.pdf"
        }
        override suspend fun clearPreview(workspace: PdfWorkspace) { clearedPreviews++ }
        override suspend fun save(workspace: PdfWorkspace, password: String?, protectionKeyId: String?, removeProtection: Boolean): DocumentModel {
            if (failSave) error("Storage unavailable")
            saves += workspace
            lastSavePassword = password
            lastSaveKey = protectionKeyId
            lastRemoveProtection = removeProtection
            return document.copy(pdfUri = "file:///saved-${saves.size}.pdf", pageCount = workspace.pages.size, protectionKeyId = protectionKeyId)
        }
        override suspend fun discard(workspace: PdfWorkspace) { discards++ }
    }
}
