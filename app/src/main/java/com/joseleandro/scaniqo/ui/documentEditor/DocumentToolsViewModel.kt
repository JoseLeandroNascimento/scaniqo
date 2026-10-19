package com.joseleandro.scaniqo.ui.documentEditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.scaniqo.core.security.PasswordAccess
import com.joseleandro.scaniqo.core.security.PasswordVaultRecoveryException
import com.joseleandro.scaniqo.core.security.PasswordVersionMissingException
import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPasswordRequiredException
import com.joseleandro.scaniqo.domain.model.PdfSignatureCrop
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.PdfOverlay
import com.joseleandro.scaniqo.domain.model.PdfOverlayKind
import com.joseleandro.scaniqo.ui.documentEditor.model.hasEditorScreen
import com.joseleandro.scaniqo.domain.model.isValidBackgroundMask
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import com.joseleandro.scaniqo.domain.repository.FormValueRepository
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import com.joseleandro.scaniqo.ui.documentEditor.coordinator.DocumentMergeContext
import com.joseleandro.scaniqo.ui.documentEditor.coordinator.DocumentMergeCoordinator
import com.joseleandro.scaniqo.ui.documentEditor.effect.DocumentToolsEffect
import com.joseleandro.scaniqo.ui.documentEditor.event.DocumentToolsEvent
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentToolsMessage
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentCaptureSourceController
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentLivePreviewController
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentFilterThumbnailController
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentPagePreviewController
import com.joseleandro.scaniqo.ui.documentEditor.preview.DocumentToolPreviewController
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentMergeUiState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentPagePreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolPreviewState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import com.joseleandro.scaniqo.ui.documentEditor.util.isValidSelection
import com.joseleandro.scaniqo.ui.documentEditor.util.transformSignaturePlacement
import com.joseleandro.scaniqo.ui.documentEditor.util.withoutImageEnhancements
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Mantém a receita editável separada do PDF salvo; somente Salvar substitui o documento. */
class DocumentToolsViewModel(
    private val repository: PdfEditorRepository,
    private val signatureRepository: SignatureRepository,
    private val passwords: PasswordAccess,
    private val formValues: FormValueRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DocumentToolsUiState())
    val uiState = mutableState.asStateFlow()
    private val effectChannel = Channel<DocumentToolsEffect>(Channel.UNLIMITED)
    val effects = effectChannel.receiveAsFlow()
    private val ioMutex = Mutex()
    private var openJob: Job? = null
    private val visiblePages = mutableMapOf<String, Int>()
    private val pagePreviews = DocumentPagePreviewController(viewModelScope, ::renderPage) { previews ->
        mutableState.update { it.withPagePreviews(previews) }
    }
    private val layoutPreviews = DocumentToolPreviewController(
        viewModelScope, ::renderInteractivePixels, ::renderPage,
    ) { preview ->
        mutableState.update { state ->
            state.copy(layoutPreview = preview.copy(pageId = state.layoutPreview.pageId))
        }
    }
    private val correctionsPreviews = DocumentToolPreviewController(
        viewModelScope, ::renderInteractivePixels, ::renderPage,
    ) { preview ->
        mutableState.update { state ->
            state.copy(correctionsPreview = preview.copy(pageId = state.correctionsPreview.pageId))
        }
    }
    private var correctionCaptureOnly = false
    private val correctionSources = DocumentCaptureSourceController(
        scope = viewModelScope,
        load = { workspace, pageId ->
            ioMutex.withLock { repository.renderCaptureSource(workspace, pageId) }
        },
        onChanged = { source -> mutableState.update { it.copy(correctionSource = source) } },
    )
    private val captureCorrections = DocumentLivePreviewController(
        viewModelScope,
        { workspace, pageId, maxSide ->
            ioMutex.withLock { repository.renderCaptureResult(workspace, pageId, maxSide) }
        },
    ) { preview ->
        mutableState.update { it.copy(correctionCapturePreview = preview) }
    }
    private val filterPreviews = DocumentPagePreviewController(viewModelScope, ::renderPage) { previews ->
        mutableState.update { state ->
            val preview = previews[state.filterPreview.pageId]
            state.copy(
                filterPreview = DocumentToolPreviewState(
                    pageId = state.filterPreview.pageId,
                    preview = preview?.preview,
                    isRendering = preview?.isRendering == true,
                    error = preview?.error == true,
                    showingOriginal = preview?.showingOriginal == true,
                ),
            )
        }
    }
    private val filterThumbnails = DocumentFilterThumbnailController(
        scope = viewModelScope,
        load = { workspace, pageId, rasterize, force ->
            ioMutex.withLock { repository.filterThumbnails(workspace, pageId, rasterize, force) }
        },
        onChanged = { thumbnails ->
            mutableState.update { it.copy(filterThumbnails = thumbnails) }
        },
    )
    private var baseline: PdfWorkspace? = null
    private val undoHistory = ArrayDeque<PdfWorkspace>()
    private var analysisJob: Job? = null
    private var analysisRevision = 0L
    private var sourceProtectionKeyId: String? = null
    private var session = 0L
    private var pendingProtection: Boolean? = null
    private var pendingScans: List<String>? = null
    private val mergeCoordinator = DocumentMergeCoordinator(
        scope = viewModelScope,
        passwords = passwords,
        ioMutex = ioMutex,
        context = {
            val state = uiState.value
            DocumentMergeContext(
                document = state.document,
                workspace = state.workspace,
                session = session,
                isBusy = state.isBusy,
                isLocked = state.isLocked,
                hasSignatureDraft = state.signatureDraft != null,
                requiresAuthentication = requiresAuthentication(),
            )
        },
        append = repository::appendPdfs,
        appendOrdered = repository::appendPdfsOrdered,
        onStateChanged = { merge ->
            mutableState.update {
                if (merge == DocumentMergeUiState.Idle) it.copy(merge = merge)
                else it.copy(merge = merge, tool = null, message = null)
            }
        },
        onEffect = { effectChannel.trySend(it) },
        onMessage = { message -> mutableState.update { it.copy(message = message) } },
        onWorkStarted = {
            pausePreviews()
            mutableState.update { it.copy(isRendering = false) }
        },
        onMerged = { previous, updated, firstAddedPageId ->
            rememberUndo(previous)
            mutableState.update {
                it.copy(
                    workspace = updated,
                    canUndo = true,
                    isComparingOriginal = false,
                    qualityReport = null,
                    selectedPageId = firstAddedPageId,
                    hasChanges = updated != baseline,
                    message = DocumentToolsMessage.MERGED,
                    tool = null,
                )
            }
            effectChannel.trySend(DocumentToolsEffect.MergeCompleted(updated.documentId))
        },
        onWorkFinished = { renderPreview() },
    )

    init {
        loadFormValues()
        viewModelScope.launch {
            try {
                signatureRepository.observeSignatures().collect { signatures ->
                    mutableState.update { it.copy(signatures = signatures) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(message = DocumentToolsMessage.SIGNATURES_ERROR) }
            }
        }
        viewModelScope.launch {
            passwords.state.collect { state ->
                if (!state.isUnlocked && requiresAuthentication() && !uiState.value.isLocked) {
                    lockWorkspace()
                } else if (!state.isUnlocked && mergeCoordinator.hasProtectedSources && uiState.value.merge == DocumentMergeUiState.Merging) {
                    mergeCoordinator.clear()
                    renderPreview()
                }
            }
        }
    }

    fun load(document: DocumentModel) {
        val current = uiState.value
        if (current.document?.id == document.id) {
            // Voltar das assinaturas/configurações preserva a receita ainda não salva.
            mutableState.update { it.copy(document = document) }
            return
        }
        val previous = current.workspace
        session++
        openJob?.cancel()
        pausePreviews()
        baseline = null
        pagePreviews.clear()
        clearToolPreviews()
        visiblePages.clear()
        undoHistory.clear()
        cancelAnalysis(resumePreview = false)
        sourceProtectionKeyId = document.protectionKeyId
        pendingProtection = null
        pendingScans = null
        mergeCoordinator.clear()
        mutableState.value = DocumentToolsUiState(
            document = document,
            signatures = current.signatures,
            savedFormValues = current.savedFormValues,
            canStoreFormValues = formValues != null,
            isLocked = document.protectionKeyId != null && !passwords.state.value.isUnlocked,
            recoveryRequired = document.protectionKeyId != null &&
                (!passwords.state.value.isConfigured || passwords.state.value.recoveryRequired),
        )
        viewModelScope.launch {
            if (previous != null) runCatching { ioMutex.withLock { repository.discard(previous) } }
        }
        if (uiState.value.recoveryRequired) {
            mutableState.update { it.copy(isLocked = true) }
        } else if (uiState.value.isLocked) {
            effectChannel.trySend(DocumentToolsEffect.NeedUnlock)
        } else {
            open(document)
        }
    }

    fun onEvent(event: DocumentToolsEvent) {
        when (event) {
            is DocumentToolsEvent.SelectPage -> if (canChange() && uiState.value.signatureDraft == null) {
                if (uiState.value.selectedPageId != event.pageId &&
                    uiState.value.workspace?.pages?.any { it.id == event.pageId } == true) {
                    mutableState.update { it.copy(selectedPageId = event.pageId, isComparingOriginal = false,
                        overlayPreviewPageId = null, overlayPreviewId = null) }
                    renderPreview()
                }
            }
            is DocumentToolsEvent.PageVisibility -> {
                val count = visiblePages.getOrDefault(event.pageId, 0) + if (event.visible) 1 else -1
                if (count > 0) visiblePages[event.pageId] = count else visiblePages.remove(event.pageId)
                renderPreview()
            }
            is DocumentToolsEvent.RetryPagePreview -> {
                if (canChange()) {
                    if (uiState.value.workspace?.pages?.any { it.id == event.pageId } == true) {
                        renderPreview(force = true)
                    }
                }
            }
            is DocumentToolsEvent.SetOverlayPreview -> {
                val state = uiState.value
                if (!state.canEdit || state.tool !in setOf(DocumentTool.FORMS, DocumentTool.WATERMARK)) return
                val page = state.workspace?.pages?.firstOrNull { it.id == event.pageId } ?: return
                if (event.overlayId != null && page.overlays.none { it.id == event.overlayId }) return
                if (state.overlayPreviewPageId == event.pageId && state.overlayPreviewId == event.overlayId) return
                mutableState.update { it.copy(overlayPreviewPageId = event.pageId, overlayPreviewId = event.overlayId) }
                renderPreview()
            }
            is DocumentToolsEvent.PreviewFilter -> previewFilter(event)
            DocumentToolsEvent.ClearFilterPreview -> clearFilterPreview()
            is DocumentToolsEvent.LoadFilterThumbnails -> {
                val state = uiState.value
                val workspace = state.workspace ?: return
                if (state.canEdit && state.tool == DocumentTool.FILTERS) {
                    filterThumbnails.request(workspace, event.pageId, event.rasterizeText, event.force)
                }
            }
            is DocumentToolsEvent.PreviewLayout -> previewLayout(event)
            DocumentToolsEvent.ClearLayoutPreview -> clearLayoutPreview()
            is DocumentToolsEvent.PreviewCorrections -> previewCorrections(event)
            DocumentToolsEvent.ClearCorrectionsPreview -> clearCorrectionsPreview()
            is DocumentToolsEvent.LoadCorrectionSource -> {
                val state = uiState.value
                if (state.canEdit && state.tool == DocumentTool.CORRECTIONS) {
                    state.workspace?.let { correctionSources.request(it, event.pageId, event.force) }
                }
            }
            DocumentToolsEvent.ClearCorrectionSource -> correctionSources.clear()
            is DocumentToolsEvent.ActivateEditorTool -> activateEditorTool(event.tool)
            is DocumentToolsEvent.OpenTool -> if (canChange() && uiState.value.signatureDraft == null) {
                if (event.tool.hasEditorScreen) {
                    activateEditorTool(event.tool)
                    uiState.value.document?.id?.let { effectChannel.trySend(DocumentToolsEffect.NavigateEditorTool(it, event.tool)) }
                    return
                }
                clearToolPreviews()
                if (event.tool == DocumentTool.MERGE) {
                    mutableState.update { it.copy(tool = null) }
                    uiState.value.document?.id?.let { effectChannel.trySend(DocumentToolsEffect.NavigateMergeEditor(it)) }
                    renderPreview()
                    return
                }
                if (event.tool == DocumentTool.SIGNATURES) {
                    mutableState.update { it.copy(tool = null) }
                    uiState.value.document?.id?.let { effectChannel.trySend(DocumentToolsEffect.NavigateSignatureEditor(it)) }
                    renderPreview()
                    return
                }
                val closeFilters = event.tool == DocumentTool.FILTERS && uiState.value.tool == DocumentTool.FILTERS
                if (event.tool == DocumentTool.LAYOUT || event.tool == DocumentTool.CORRECTIONS) pagePreviews.pause()
                mutableState.update { it.copy(tool = event.tool.takeUnless { closeFilters }) }
                // A faixa de filtros usa a página do visualizador como prévia, sem suspender sua atualização.
                renderPreview()
            }
            DocumentToolsEvent.DismissTool -> if (!uiState.value.isBusy) {
                clearToolPreviews()
                mutableState.update { it.copy(tool = null) }
                renderPreview()
            }
            is DocumentToolsEvent.SelectGlobalFilter -> selectGlobalFilter(event.imageFilter)
            is DocumentToolsEvent.ApplyLayout -> applyEditorTool(DocumentTool.LAYOUT) { applyLayout(event) }
            is DocumentToolsEvent.ApplyFilter -> applyPageLayouts(event.allPages, event.pageIds) {
                it.copy(
                    imageFilter = event.imageFilter,
                    filterIntensity = (event.intensity.takeIf(Float::isFinite) ?: it.filterIntensity).coerceIn(0f, 1f),
                    rasterizeText = event.rasterizeText,
                )
            }
            is DocumentToolsEvent.ApplyCorrections -> applyEditorTool(DocumentTool.CORRECTIONS) { applyCorrections(event) }
            is DocumentToolsEvent.AutoImprove -> analyzeQuality(event.allPages)
            DocumentToolsEvent.AnalyzeQuality -> analyzeQuality()
            is DocumentToolsEvent.AnalyzeCorrectionPage -> analyzeQuality(pageId = event.pageId, requestId = event.requestId)
            DocumentToolsEvent.CancelAnalysis -> cancelAnalysis()
            is DocumentToolsEvent.SetExportOptions -> if (canChange() && uiState.value.signatureDraft == null) {
                change { it.copy(exportQuality = event.quality, ocrEnabled = event.ocrEnabled) }
                mutableState.update { it.copy(tool = null) }
            }
            is DocumentToolsEvent.SetCompareOriginal -> if (canChange() && uiState.value.signatureDraft == null) {
                mutableState.update { it.copy(isComparingOriginal = event.enabled && it.canCompare) }
                renderPreview()
            }
            is DocumentToolsEvent.SaveFormValue -> updateFormValues { it.save(event.label, event.value) }
            is DocumentToolsEvent.DeleteFormValue -> updateFormValues { it.delete(event.id) }
            is DocumentToolsEvent.AddImageMark -> addImageMark(event)
            is DocumentToolsEvent.PutOverlay -> if (event.overlay.isValid()) {
                change { workspace ->
                    workspace.copy(pages = workspace.pages.map { page ->
                        if (page.id in event.pageIds && (page.overlays.size < 100 || page.overlays.any { it.id == event.overlay.id })) {
                            page.copy(overlays = page.overlays.filterNot { it.id == event.overlay.id } + event.overlay)
                        } else page
                    })
                }
            }
            is DocumentToolsEvent.RemoveOverlay -> change { workspace ->
                workspace.copy(pages = workspace.pages.map { page ->
                    if (page.id in event.pageIds) page.copy(overlays = page.overlays.filterNot { it.id == event.overlayId }) else page
                })
            }
            is DocumentToolsEvent.SaveRedactedCopy -> saveRedactedCopy(event)
            DocumentToolsEvent.Undo -> undo()
            is DocumentToolsEvent.SetNumbering -> change { it.copy(showPageNumbers = event.enabled) }
            is DocumentToolsEvent.MovePage -> movePage(event.pageId, event.offset)
            is DocumentToolsEvent.MovePageTo -> movePageTo(event.pageId, event.targetIndex)
            is DocumentToolsEvent.ReorderPages -> reorderPages(event.pageIds)
            is DocumentToolsEvent.RequestDeletePage -> if (canChange() && (uiState.value.workspace?.pages?.size ?: 0) > 1) {
                mutableState.update { it.copy(pendingDeletePageId = event.pageId) }
            }
            DocumentToolsEvent.ConfirmDeletePage -> deletePage()
            DocumentToolsEvent.DismissPageDeletion -> mutableState.update { it.copy(pendingDeletePageId = null) }
            DocumentToolsEvent.AddPages -> if (canChange() && uiState.value.signatureDraft == null) {
                mutableState.update { it.copy(isScanning = true, tool = null, message = null) }
                effectChannel.trySend(DocumentToolsEffect.LaunchScanner)
            }
            is DocumentToolsEvent.ScanCompleted -> {
                if (uiState.value.isScanning) {
                    mutableState.update { it.copy(isScanning = false) }
                    if (uiState.value.isLocked) {
                        pendingScans = event.pageUris
                        effectChannel.trySend(DocumentToolsEffect.NeedUnlock)
                    } else appendScans(event.pageUris)
                }
            }
            DocumentToolsEvent.ScanCancelled -> mutableState.update { it.copy(isScanning = false) }
            DocumentToolsEvent.ScanFailed -> mutableState.update {
                it.copy(isScanning = false, message = DocumentToolsMessage.SCAN_ERROR)
            }
            is DocumentToolsEvent.MergePdfs -> mergeCoordinator.launchPicker(event.insertionIndex)
            is DocumentToolsEvent.MergeAppDocuments -> mergeCoordinator.mergeDocuments(event.documents, event.insertionIndex)
            is DocumentToolsEvent.MergeItems -> mergeCoordinator.mergeItems(event.items, event.insertionIndex)
            is DocumentToolsEvent.MergeOrderedItems -> mergeCoordinator.mergeOrderedItems(event.items)
            is DocumentToolsEvent.MergePdfsSelected -> mergeCoordinator.acceptSelection(event.uris)
            DocumentToolsEvent.MergePdfsCancelled -> if (uiState.value.merge == DocumentMergeUiState.Picking) mergeCoordinator.clear()
            DocumentToolsEvent.MergePdfsFailed -> if (uiState.value.merge == DocumentMergeUiState.Picking) {
                mergeCoordinator.clear()
                mutableState.update { it.copy(message = DocumentToolsMessage.MERGE_ERROR) }
            }
            DocumentToolsEvent.CancelPdfMerge, DocumentToolsEvent.CancelMergePassword -> {
                mergeCoordinator.clear()
                if (!uiState.value.isLocked) renderPreview()
            }
            is DocumentToolsEvent.SubmitMergePassword -> mergeCoordinator.submitPassword(event.password)
            is DocumentToolsEvent.SetProtection -> setProtection(event.enabled)
            DocumentToolsEvent.Unlock -> effectChannel.trySend(DocumentToolsEffect.NeedUnlock)
            DocumentToolsEvent.AuthenticationSucceeded -> unlock()
            DocumentToolsEvent.AuthenticationDismissed -> if (uiState.value.merge == DocumentMergeUiState.AwaitingUnlock) {
                mergeCoordinator.clear()
                if (!uiState.value.isLocked) renderPreview()
            }
            DocumentToolsEvent.RequestPasswordRecovery -> effectChannel.trySend(DocumentToolsEffect.NeedDocumentPassword)
            is DocumentToolsEvent.RecoverWithPassword -> {
                val document = uiState.value.document ?: return
                if (event.protectionKeyId == null) {
                    effectChannel.trySend(DocumentToolsEffect.NavigatePasswordSettings)
                } else if (!passwords.state.value.isUnlocked) {
                    effectChannel.trySend(DocumentToolsEffect.NeedUnlock)
                } else {
                    mutableState.update { it.copy(isLocked = false, recoveryRequired = false) }
                    open(document, event.password, event.protectionKeyId)
                }
            }
            DocumentToolsEvent.ManageSignatures -> if (canChange()) {
                mutableState.update { it.copy(tool = null) }
                effectChannel.trySend(DocumentToolsEffect.NavigateSignatures)
            }
            DocumentToolsEvent.PasswordSettings -> {
                mutableState.update { it.copy(tool = null) }
                effectChannel.trySend(DocumentToolsEffect.NavigatePasswordSettings)
            }
            is DocumentToolsEvent.InsertSignature -> insertSignature(event.signatureId)
            is DocumentToolsEvent.EditSignature -> editSignature(event.pageId, event.placementId)
            is DocumentToolsEvent.MoveSignature -> updateSignature { draft ->
                transformSignaturePlacement(draft, x = event.x, y = event.y)
            }
            is DocumentToolsEvent.ResizeSignature -> updateSignature { draft ->
                transformSignaturePlacement(draft, width = event.width)
            }
            is DocumentToolsEvent.TransformSignature -> updateSignature { draft ->
                transformSignaturePlacement(
                    draft, event.x, event.y, event.width, event.rotationDegrees,
                    height = event.height,
                    stretchToBounds = event.stretchToBounds ?: draft.stretchToBounds,
                )
            }
            is DocumentToolsEvent.CropSignature -> cropSignature(event)
            DocumentToolsEvent.ConfirmSignature -> commitSignature()
            DocumentToolsEvent.CancelSignature -> {
                mutableState.update { it.copy(signatureDraft = null, signaturePageId = null) }
                renderPreview()
            }
            is DocumentToolsEvent.RemoveSignature -> change { workspace ->
                workspace.copy(pages = workspace.pages.map { page ->
                    if (page.id == event.pageId) page.copy(signatures = page.signatures.filterNot { it.id == event.placementId }) else page
                })
            }
            DocumentToolsEvent.Save -> save()
            DocumentToolsEvent.Retry -> {
                val document = uiState.value.document ?: return
                if (uiState.value.workspace == null) open(document) else renderPreview(force = true)
            }
            DocumentToolsEvent.DismissMessage -> mutableState.update { it.copy(message = null) }
            DocumentToolsEvent.RequestBack -> requestBack()
            DocumentToolsEvent.ConfirmDiscard -> close()
            DocumentToolsEvent.CancelDiscard -> mutableState.update { it.copy(confirmDiscard = false) }
            DocumentToolsEvent.Backgrounded -> when {
                requiresAuthentication() -> lockWorkspace()
                mergeCoordinator.hasSensitiveData -> {
                    // Sem cofre para retomar com autenticação, descarte toda credencial da mesclagem.
                    mergeCoordinator.clear()
                    renderPreview()
                }
            }
        }
    }

    private fun open(document: DocumentModel, passwordOverride: String? = null, recoveryKeyId: String? = null) {
        if (uiState.value.isLoading) return
        if (document.protectionKeyId != null && !passwords.state.value.isUnlocked) {
            mutableState.update { it.copy(isLocked = true) }
            effectChannel.trySend(DocumentToolsEffect.NeedUnlock)
            return
        }
        val openingSession = session
        mutableState.update { it.copy(isLoading = true, loadError = false) }
        openJob = viewModelScope.launch {
            var opened: PdfWorkspace? = null
            try {
                opened = ioMutex.withLock {
                    if (passwordOverride != null) {
                        repository.open(document, passwordOverride)
                    } else if (document.protectionKeyId != null) {
                        passwords.withPassword(document.protectionKeyId) { repository.open(document, it) }
                    } else repository.open(document)
                }
                if (openingSession != session) {
                    withContext(NonCancellable) { ioMutex.withLock { repository.discard(opened) } }
                    return@launch
                }
                baseline = opened
                if (recoveryKeyId != null) sourceProtectionKeyId = recoveryKeyId
                val ready = if (recoveryKeyId != null) opened.copy(protectionKeyId = recoveryKeyId, isProtected = true) else opened
                mutableState.update {
                    it.copy(
                        workspace = ready,
                        selectedPageId = ready.pages.firstOrNull()?.id,
                        isLoading = false,
                        hasChanges = passwordOverride != null || ready != baseline,
                        recoveryRequired = false,
                    )
                }
                if (!uiState.value.isLocked) renderPreview()
            } catch (cancelled: CancellationException) {
                opened?.let { withContext(NonCancellable) { ioMutex.withLock { repository.discard(it) } } }
                throw cancelled
            } catch (failure: Exception) {
                if (openingSession == session) {
                    val recovery = failure is PasswordVersionMissingException ||
                        failure is PasswordVaultRecoveryException || failure is PdfPasswordRequiredException
                    mutableState.update {
                        it.copy(isLoading = false, loadError = true, recoveryRequired = recovery, isLocked = recovery || it.isLocked)
                    }
                }
            }
        }
    }

    private fun canChange(): Boolean = uiState.value.canEdit

    /** Uma seleção altera todas as folhas em uma única etapa do histórico e mantém a faixa aberta. */
    private fun selectGlobalFilter(filter: PdfImageFilter) {
        val state = uiState.value
        if (!state.canEdit || state.tool != DocumentTool.FILTERS || state.signatureDraft != null) return
        // Voltar ao mesmo filtro também encerra uma comparação temporária, sem criar outra edição.
        if (state.isComparingOriginal) mutableState.update { it.copy(isComparingOriginal = false) }
        change { workspace ->
            workspace.copy(pages = workspace.pages.map { page ->
                val layout = page.layout
                val hasCorrections = layout.straightenDegrees != 0f || layout.perspective != null ||
                layout.curvature != 0f || layout.backgroundMask != null
                page.copy(layout = layout.copy(
                    imageFilter = filter,
                    filterIntensity = 1f,
                    // Filtros precisam dos pixels. Original recupera texto e vetores quando não há correções.
                    rasterizeText = filter != PdfImageFilter.ORIGINAL || (layout.rasterizeText && hasCorrections),
                ))
            })
        }
        // As miniaturas independem do filtro escolhido; conservá-las evita refazer a faixa a cada toque.
        renderPreview()
    }

    private fun activateEditorTool(tool: DocumentTool) {
        if (!tool.hasEditorScreen) return
        if (!canChange() || uiState.value.signatureDraft != null) return
        if (uiState.value.tool == tool) return
        clearToolPreviews()
        if (tool == DocumentTool.LAYOUT || tool == DocumentTool.CORRECTIONS) pagePreviews.pause()
        mutableState.update { it.copy(tool = tool) }
        renderPreview()
    }

    private fun applyEditorTool(tool: DocumentTool, apply: () -> Boolean) {
        val state = uiState.value
        if (apply() && state.tool == tool) {
            state.document?.id?.let { effectChannel.trySend(DocumentToolsEffect.EditorToolApplied(it, tool)) }
        }
    }

    private fun applyLayout(event: DocumentToolsEvent.ApplyLayout): Boolean {
        // Ajustes geométricos preservam o filtro próprio de cada página, inclusive ao redefinir.
        return applyPageLayouts(event.allPages, event.pageIds) { current ->
            current.copy(
                alignment = event.alignment,
                scaleMode = event.scaleMode,
                rotationQuarterTurns = ((event.rotationQuarterTurns % 4) + 4) % 4,
                contentScale = (event.contentScale.takeIf { it.isFinite() } ?: current.contentScale)
                    .coerceIn(0.25f, 2f),
            )
        }
    }

    private fun applyCorrections(event: DocumentToolsEvent.ApplyCorrections): Boolean {
        if (event.crop?.isValid() == false || event.perspective?.isValidSelection() == false ||
            event.backgroundMask?.isValidBackgroundMask() == false
        ) return false
        return applyPageLayouts(event.allPages, event.pageIds) { current ->
            current.copy(
                straightenDegrees = (event.straightenDegrees.takeIf(Float::isFinite) ?: current.straightenDegrees)
                    .coerceIn(-15f, 15f),
                perspective = event.perspective.takeIf { event.crop == null },
            crop = event.crop?.takeUnless { it.isIdentity() },
            backgroundMask = event.backgroundMask,
                curvature = (event.curvature.takeIf(Float::isFinite) ?: current.curvature).coerceIn(-1f, 1f),
                // Corrigir a geometria não pode desligar a conversão exigida pelo filtro já aplicado.
                rasterizeText = event.rasterizeText ||
                    (current.rasterizeText && current.imageFilter != PdfImageFilter.ORIGINAL && current.filterIntensity > 0f),
            )
        }
    }

    /** A análise é cancelável e usa as fontes privadas; nenhum alerta apaga páginas sozinho. */
    private fun analyzeQuality(improveAllPages: Boolean? = null, pageId: String? = null, requestId: String? = null) {
        val state = uiState.value
        val workspace = state.workspace ?: return
        if (!state.canEdit || state.signatureDraft != null) return
        val operationSession = session
        val revision = ++analysisRevision
        pausePreviews()
        mutableState.update {
            it.copy(isAnalyzing = true, analysisError = false, isRendering = false, correctionAnalysisRequestId = null)
        }
        analysisJob = viewModelScope.launch {
            try {
                // Melhorar uma folha não precisa analisar todas as outras capturas.
                val targetPageId = pageId ?: state.selectedPageId.takeIf { improveAllPages == false }
                val analysisWorkspace = if (targetPageId != null) {
                    workspace.copy(pages = workspace.pages.filter { it.id == targetPageId })
                } else workspace
                require(analysisWorkspace.pages.isNotEmpty())
                val report = ioMutex.withLock { repository.analyze(analysisWorkspace) }
                if (revision != analysisRevision || operationSession != session || uiState.value.isLocked) return@launch
                mutableState.update {
                    it.copy(qualityReport = report, isAnalyzing = false, correctionAnalysisRequestId = requestId)
                }
                if (improveAllPages != null) {
                    val pages = report.pages.associateBy { it.pageId }
                    change { current ->
                        current.copy(pages = current.pages.map { page ->
                            if (improveAllPages || page.id == state.selectedPageId) {
                                val angle = pages[page.id]?.suggestedStraightenDegrees
                                    ?.takeIf(Float::isFinite)?.coerceIn(-15f, 15f) ?: page.layout.straightenDegrees
                                page.copy(layout = page.layout.copy(
                                    imageFilter = PdfImageFilter.ENHANCE,
                                    filterIntensity = 1f,
                                    straightenDegrees = angle,
                                ))
                            } else page
                        })
                    }
                    mutableState.update { it.copy(tool = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == analysisRevision && operationSession == session) {
                    mutableState.update { it.copy(analysisError = true, correctionAnalysisRequestId = requestId) }
                }
            } finally {
                if (revision == analysisRevision && operationSession == session) {
                    analysisJob = null
                    mutableState.update { it.copy(isAnalyzing = false) }
                    if (!uiState.value.isLocked) renderPreview()
                }
            }
        }
    }

    private fun cancelAnalysis(resumePreview: Boolean = true) {
        analysisRevision++
        analysisJob?.cancel()
        analysisJob = null
        mutableState.update { it.copy(isAnalyzing = false, correctionAnalysisRequestId = null) }
        if (resumePreview && !uiState.value.isLocked) renderPreview()
    }

    private fun rememberUndo(workspace: PdfWorkspace) {
        if (undoHistory.size == 20) undoHistory.removeFirst()
        undoHistory.addLast(workspace)
    }

    private fun undo() {
        if (!canChange() || uiState.value.signatureDraft != null) return
        val previous = undoHistory.removeLastOrNull() ?: return
        mutableState.update { state ->
            state.copy(
                workspace = previous,
                hasChanges = previous != baseline,
                canUndo = undoHistory.isNotEmpty(),
                isComparingOriginal = false,
                qualityReport = null,
                selectedPageId = state.selectedPageId?.takeIf { id -> previous.pages.any { it.id == id } }
                    ?: previous.pages.firstOrNull()?.id,
                tool = state.tool.takeIf { it == DocumentTool.FILTERS },
            )
        }
        renderPreview()
    }

    private fun applyPageLayouts(allPages: Boolean, pageIds: Set<String>? = null, transform: (PdfPageLayout) -> PdfPageLayout): Boolean {
        val state = uiState.value
        val workspace = state.workspace ?: return false
        if (!state.canEdit || state.signatureDraft != null) return false
        val targets = workspace.pages.filter {
            pageIds?.contains(it.id) ?: (allPages || it.id == state.selectedPageId)
        }.mapTo(mutableSetOf()) { it.id }
        if (targets.isEmpty()) return false
        clearToolPreviews()
        // A mesma receita alimenta a prévia e o salvamento, sem transformar o PDF original.
        change { value ->
            value.copy(pages = value.pages.map { page ->
                if (page.id in targets) {
                    page.copy(layout = transform(page.layout))
                } else page
            })
        }
        mutableState.update { it.copy(tool = null) }
        renderPreview()
        return true
    }

    private fun change(transform: (PdfWorkspace) -> PdfWorkspace) {
        if (!canChange() || uiState.value.signatureDraft != null) return
        val workspace = uiState.value.workspace ?: return
        val wasComparing = uiState.value.isComparingOriginal
        val updated = transform(workspace)
        if (updated == workspace) return
        rememberUndo(workspace)
        mutableState.update { state ->
            state.copy(
                workspace = updated,
                canUndo = true,
                isComparingOriginal = false,
                qualityReport = if (workspace.pages.map { it.id } == updated.pages.map { it.id }) state.qualityReport else null,
                hasChanges = updated != baseline,
                selectedPageId = state.selectedPageId?.takeIf { id -> updated.pages.any { it.id == id } } ?: updated.pages.firstOrNull()?.id,
                message = null,
            )
        }
        // Opções de saída não mudam a imagem da prévia e serão processadas apenas ao salvar.
        if (wasComparing || workspace.pages != updated.pages || workspace.showPageNumbers != updated.showPageNumbers ||
            uiState.value.previewUri == null || workspace.isProtected != updated.isProtected
        ) renderPreview()
    }

    private fun movePage(pageId: String, offset: Int) {
        if (offset != -1 && offset != 1) return
        change { workspace ->
            val pages = workspace.pages.toMutableList()
            val from = pages.indexOfFirst { it.id == pageId }
            val to = from + offset
            if (from !in pages.indices || to !in pages.indices) workspace else {
                pages.add(to, pages.removeAt(from))
                workspace.copy(pages = pages)
            }
        }
    }

    private fun movePageTo(pageId: String, targetIndex: Int) {
        change { workspace ->
            val from = workspace.pages.indexOfFirst { it.id == pageId }
            if (from < 0 || targetIndex !in workspace.pages.indices || from == targetIndex) {
                workspace
            } else {
                val pages = workspace.pages.toMutableList()
                pages.add(targetIndex, pages.removeAt(from))
                workspace.copy(pages = pages)
            }
        }
    }

    private fun reorderPages(pageIds: List<String>) {
        change { workspace ->
            val pagesById = workspace.pages.associateBy { it.id }
            // Um gesto confirma uma única permutação completa; IDs repetidos ou antigos são ignorados.
            if (pageIds.size != workspace.pages.size || pageIds.toSet() != pagesById.keys) {
                workspace
            } else workspace.copy(pages = pageIds.map(pagesById::getValue))
        }
    }

    private fun deletePage() {
        val id = uiState.value.pendingDeletePageId ?: return
        change { workspace ->
            if (workspace.pages.size <= 1) workspace else workspace.copy(pages = workspace.pages.filterNot { it.id == id })
        }
        mutableState.update { it.copy(pendingDeletePageId = null) }
    }

    private fun appendScans(uris: List<String>) {
        val workspace = uiState.value.workspace ?: return
        if (!canChange()) return
        if (uris.isEmpty()) {
            mutableState.update { it.copy(message = DocumentToolsMessage.APPEND_ERROR) }
            return
        }
        pausePreviews()
        mutableState.update { it.copy(isAppending = true, message = null) }
        val operationSession = session
        viewModelScope.launch {
            try {
                val updated = ioMutex.withLock { repository.appendScans(workspace, uris) }
                if (operationSession != session) return@launch
                rememberUndo(workspace)
                mutableState.update {
                    it.copy(workspace = updated, selectedPageId = updated.pages.lastOrNull()?.id, hasChanges = updated != baseline,
                        canUndo = true, isComparingOriginal = false, qualityReport = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (operationSession == session) mutableState.update { it.copy(message = DocumentToolsMessage.APPEND_ERROR) }
            } finally {
                if (operationSession == session) {
                    mutableState.update { it.copy(isAppending = false) }
                    if (!uiState.value.isLocked) renderPreview()
                }
            }
        }
    }

    private fun setProtection(enabled: Boolean) {
        if (!canChange()) return
        if (!passwords.state.value.isConfigured || passwords.activeKeyId == null) {
            pendingProtection = enabled
            effectChannel.trySend(DocumentToolsEffect.NavigatePasswordSettings)
            return
        }
        if (!passwords.state.value.isUnlocked) {
            pendingProtection = enabled
            effectChannel.trySend(DocumentToolsEffect.NeedUnlock)
            return
        }
        change { workspace ->
            workspace.copy(
                isProtected = enabled,
                protectionKeyId = if (enabled) workspace.protectionKeyId ?: passwords.activeKeyId else null,
            )
        }
    }

    private fun unlock() {
        if (!passwords.state.value.isUnlocked) return
        mutableState.update { it.copy(isLocked = false, loadError = false) }
        if (uiState.value.workspace == null) {
            uiState.value.document?.let(::open)
        } else {
            pendingProtection?.let { enabled -> pendingProtection = null; setProtection(enabled) }
            val scans = pendingScans
            pendingScans = null
            when {
                mergeCoordinator.resumeAfterUnlock() -> Unit
                scans != null -> appendScans(scans)
                else -> renderPreview()
            }
        }
    }

    private fun requiresAuthentication(): Boolean =
        uiState.value.document?.protectionKeyId != null || uiState.value.workspace?.isProtected == true

    private fun lockWorkspace() {
        cancelAnalysis(resumePreview = false)
        pagePreviews.clear()
        clearToolPreviews()
        visiblePages.clear()
        // O retorno do seletor pode bloquear o cofre; mantenha as URIs, mas descarte senhas externas.
        mergeCoordinator.onWorkspaceLocked()
        pausePreviews()
        mutableState.update {
            it.copy(
                isLocked = true, previewUri = null, isRendering = false, tool = null,
                qualityReport = null, isComparingOriginal = false, analysisError = false,
                displayedComparisonPageId = null,
                pendingDeletePageId = null,
            )
        }
        val workspace = uiState.value.workspace ?: return
        viewModelScope.launch {
            runCatching { ioMutex.withLock { repository.clearPreview(workspace) } }
        }
    }

    private fun insertSignature(id: String) {
        if (!canChange() || uiState.value.signatureDraft != null) return
        val workspace = uiState.value.workspace ?: return
        val signature = uiState.value.signatures.firstOrNull { it.id == id } ?: return
        val page = uiState.value.selectedPageId ?: return
        if (!signature.aspectRatio.isFinite() || signature.aspectRatio <= 0f) return
        val ratio = (595f / 842f) / signature.aspectRatio
        val width = minOf(0.52f, 0.35f / ratio)
        val height = width * ratio
        val operationSession = session
        pausePreviews()
        mutableState.update { it.copy(isCopyingSignature = true, message = null) }
        viewModelScope.launch {
            try {
                // A cópia pertence à edição; excluir a assinatura da biblioteca não a invalida.
                val imageUri = ioMutex.withLock { repository.copySignature(workspace, signature.imageUri) }
                if (operationSession != session) return@launch
                mutableState.update {
                    it.copy(
                        signatureDraft = PdfSignaturePlacement(
                            UUID.randomUUID().toString(), imageUri,
                            (1f - width) / 2f, 0.62f - height / 2f, width, height,
                        ),
                        signaturePageId = page,
                        tool = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (operationSession == session) {
                    mutableState.update { it.copy(message = DocumentToolsMessage.SIGNATURES_ERROR) }
                }
            } finally {
                if (operationSession == session) {
                    mutableState.update { it.copy(isCopyingSignature = false) }
                    if (!uiState.value.isLocked) renderPreview()
                }
            }
        }
    }

    private fun editSignature(pageId: String, placementId: String) {
        if (!canChange()) return
        val placement = uiState.value.workspace?.pages?.find { it.id == pageId }?.signatures?.find { it.id == placementId } ?: return
        mutableState.update {
            it.copy(signatureDraft = placement, signaturePageId = pageId, selectedPageId = pageId, tool = null)
        }
        renderPreview()
        uiState.value.document?.id?.let { effectChannel.trySend(DocumentToolsEffect.NavigateSignatureEditor(it)) }
    }

    private fun cropSignature(event: DocumentToolsEvent.CropSignature) {
        if (!canChange()) return
        val state = uiState.value
        val workspace = state.workspace ?: return
        val draft = state.signatureDraft ?: return
        val crop = PdfSignatureCrop(event.left, event.top, event.right, event.bottom)
        if (!crop.isValid) return
        val operationSession = session
        pausePreviews()
        mutableState.update { it.copy(isCopyingSignature = true, message = null) }
        viewModelScope.launch {
            try {
                val image = ioMutex.withLock { repository.cropSignature(workspace, draft.imageUri, crop) }
                if (operationSession != session || uiState.value.signatureDraft != draft) return@launch
                require(image.aspectRatio.isFinite() && image.aspectRatio > 0f)
                val height = if (draft.stretchToBounds) {
                    draft.height * (crop.bottom - crop.top) / (crop.right - crop.left)
                } else {
                    draft.width * (595f / 842f) / image.aspectRatio
                }
                val cropped = transformSignaturePlacement(
                    // Mantém a proporção escolhida pelo usuário, inclusive após esticar a assinatura.
                    draft.copy(imageUri = image.imageUri, height = height),
                    y = draft.y + (draft.height - height) / 2f,
                )
                mutableState.update { it.copy(signatureDraft = cropped) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (operationSession == session && uiState.value.signatureDraft == draft) {
                    mutableState.update { it.copy(message = DocumentToolsMessage.SIGNATURES_ERROR) }
                }
            } finally {
                if (operationSession == session) {
                    mutableState.update { it.copy(isCopyingSignature = false) }
                    if (!uiState.value.isLocked) renderPreview()
                }
            }
        }
    }

    private fun updateSignature(transform: (PdfSignaturePlacement) -> PdfSignaturePlacement) {
        if (!canChange()) return
        mutableState.update { it.copy(signatureDraft = it.signatureDraft?.let(transform)) }
    }

    private fun commitSignature() {
        if (!canChange()) return
        val state = uiState.value
        val draft = state.signatureDraft ?: return
        val pageId = state.signaturePageId ?: return
        mutableState.update { it.copy(signatureDraft = null, signaturePageId = null) }
        change { workspace ->
            workspace.copy(pages = workspace.pages.map { page ->
                if (page.id == pageId) page.copy(signatures = page.signatures.filterNot { it.id == draft.id } + draft) else page
            })
        }
        // Confirmar sem mover também precisa recolocar a assinatura excluída da prévia.
        renderPreview()
    }

    /** A edição prepara somente as folhas em tela; o documento inteiro é montado ao salvar. */
    private fun renderPreview(force: Boolean = false) {
        val state = uiState.value
        val workspace = state.workspace ?: return
        if (!state.canEdit || state.tool == DocumentTool.LAYOUT || state.tool == DocumentTool.CORRECTIONS) return
        val comparisonPageId = state.selectedPageId.takeIf { state.isComparingOriginal && state.canCompare }
        val comparison = if (comparisonPageId == null) workspace else workspace.withoutImageEnhancements(comparisonPageId)
        val draft = state.signatureDraft
        val signatureSnapshot = if (draft == null) comparison else comparison.copy(pages = comparison.pages.map { page ->
            if (page.id == state.signaturePageId) page.copy(signatures = page.signatures.filterNot { it.id == draft.id }) else page
        })
        // Só a prévia omite a seleção; a receita e a exportação mantêm o conteúdo completo.
        val snapshot = if (state.overlayPreviewId == null ||
            state.tool !in setOf(DocumentTool.FORMS, DocumentTool.WATERMARK)) signatureSnapshot
        else signatureSnapshot.copy(pages = signatureSnapshot.pages.map { page ->
            if (page.id == state.overlayPreviewPageId) page.copy(
                overlays = page.overlays.filterNot { it.id == state.overlayPreviewId }
            ) else page
        })
        val existing = workspace.pages.mapTo(mutableSetOf()) { it.id }
        val requested = (listOfNotNull(state.selectedPageId) + visiblePages.keys).filter { it in existing }.toSet()
        if (force) {
            // A nova saída salva substitui também as miniaturas antigas fora da tela.
            pagePreviews.retainPages(requested)
            pagePreviews.invalidate()
        }
        pagePreviews.retainPages(existing)
        pagePreviews.cancelExcept(requested)
        requested.forEach { id ->
            pagePreviews.request(snapshot, id, showingOriginal = id == comparisonPageId,
                debounceMillis = if (state.pagePreviews[id]?.preview != null) 80 else 0)
        }
    }

    private suspend fun renderInteractivePixels(workspace: PdfWorkspace, pageId: String, maxSide: Int) =
        ioMutex.withLock { repository.renderInteractivePixels(workspace, pageId, maxSide) }

    private suspend fun renderPage(workspace: PdfWorkspace, pageId: String) =
        ioMutex.withLock {
            val state = uiState.value
            val retained = state.pagePreviews.values.mapNotNull { it.preview?.uri }.toMutableSet()
            state.layoutPreview.preview?.uri?.let(retained::add)
            state.correctionsPreview.preview?.uri?.let(retained::add)
            state.filterPreview.preview?.uri?.let(retained::add)
            repository.renderPagePreview(workspace, pageId, retainedPreviewUris = retained)
        }

    private fun DocumentToolsUiState.withPagePreviews(
        previews: Map<String, DocumentPagePreviewState>,
    ): DocumentToolsUiState {
        val selected = previews[selectedPageId]
        return copy(
            pagePreviews = previews,
            previewUri = selected?.preview?.uri,
            isRendering = previews.values.any { it.isRendering },
            previewError = selected?.error == true,
            displayedComparisonPageId = selectedPageId.takeIf { selected?.showingOriginal == true },
        )
    }

    /** O gesto altera apenas a prévia da folha em foco; a receita só muda ao tocar em Aplicar. */
    private fun previewLayout(event: DocumentToolsEvent.PreviewLayout) {
        val state = uiState.value
        val workspace = state.workspace ?: return
        if (!state.canEdit || state.tool != DocumentTool.LAYOUT) return
        val page = workspace.pages.firstOrNull { it.id == event.pageId } ?: return
        val layout = if (event.showOriginal) page.layout else page.layout.copy(
            alignment = event.layout.alignment,
            scaleMode = event.layout.scaleMode,
            rotationQuarterTurns = ((event.layout.rotationQuarterTurns % 4) + 4) % 4,
            contentScale = (event.layout.contentScale.takeIf(Float::isFinite) ?: page.layout.contentScale)
                .coerceIn(0.25f, 2f),
        )
        if (state.layoutPreview.pageId != event.pageId) clearLayoutPreview()
        mutableState.update { it.copy(layoutPreview = it.layoutPreview.copy(pageId = event.pageId)) }
        val snapshot = workspace.copy(pages = workspace.pages.map {
            if (it.id == page.id) it.copy(layout = layout) else it
        })
        layoutPreviews.request(snapshot, page.id, showingOriginal = event.showOriginal, debounceMillis = 120)
    }

    private fun previewCorrections(event: DocumentToolsEvent.PreviewCorrections) {
        val state = uiState.value
        val workspace = state.workspace ?: return
        if (!state.canEdit || state.tool != DocumentTool.CORRECTIONS) return
        val page = workspace.pages.firstOrNull { it.id == event.pageId } ?: return
        if (event.layout.crop?.isValid() == false || event.layout.perspective?.isValidSelection() == false ||
            event.layout.backgroundMask?.isValidBackgroundMask() == false
        ) return
        val layout = if (event.showOriginal) page.layout else page.layout.copy(
            straightenDegrees = (event.layout.straightenDegrees.takeIf(Float::isFinite) ?: 0f).coerceIn(-15f, 15f),
            perspective = event.layout.perspective.takeIf { event.layout.crop == null },
            crop = event.layout.crop?.takeUnless { it.isIdentity() },
            backgroundMask = event.layout.backgroundMask,
            curvature = (event.layout.curvature.takeIf(Float::isFinite) ?: 0f).coerceIn(-1f, 1f),
            rasterizeText = event.layout.rasterizeText ||
                (page.layout.rasterizeText && page.layout.imageFilter != PdfImageFilter.ORIGINAL && page.layout.filterIntensity > 0f),
        )
        if (state.correctionsPreview.pageId != event.pageId || correctionCaptureOnly != event.captureOnly) {
            clearCorrectionsPreview()
        }
        correctionCaptureOnly = event.captureOnly
        mutableState.update { it.copy(correctionsPreview = it.correctionsPreview.copy(pageId = event.pageId)) }
        val snapshot = workspace.copy(pages = workspace.pages.map {
            if (it.id == page.id) it.copy(layout = layout) else it
        })
        if (event.captureOnly) captureCorrections.request(snapshot, page.id)
        else correctionsPreviews.request(snapshot, page.id, showingOriginal = event.showOriginal, debounceMillis = 120)
    }

    private fun previewFilter(event: DocumentToolsEvent.PreviewFilter) {
        val state = uiState.value
        val workspace = state.workspace ?: return
        if (!state.canEdit || state.tool != DocumentTool.FILTERS) return
        val page = workspace.pages.firstOrNull { it.id == event.pageId } ?: return
        val layout = if (event.showOriginal) page.layout else page.layout.copy(
            imageFilter = event.layout.imageFilter,
            filterIntensity = (event.layout.filterIntensity.takeIf(Float::isFinite) ?: 1f).coerceIn(0f, 1f),
            rasterizeText = event.layout.rasterizeText,
        )
        if (state.filterPreview.pageId != event.pageId) {
            filterPreviews.clear()
        }
        mutableState.update { it.copy(filterPreview = it.filterPreview.copy(pageId = event.pageId)) }
        val snapshot = workspace.copy(pages = workspace.pages.map {
            if (it.id == page.id) it.copy(layout = layout) else it
        })
        filterPreviews.request(snapshot, page.id, showingOriginal = event.showOriginal, debounceMillis = 120)
    }

    private fun clearFilterPreview() {
        filterPreviews.clear()
        filterThumbnails.clear()
        mutableState.update { it.copy(filterPreview = DocumentToolPreviewState()) }
    }

    private fun pausePreviews() {
        pagePreviews.pause()
        layoutPreviews.pause()
        correctionsPreviews.pause()
        captureCorrections.pause()
        correctionSources.pause()
        filterPreviews.pause()
        filterThumbnails.pause()
    }

    private fun clearToolPreviews() {
        mutableState.update { it.copy(overlayPreviewPageId = null, overlayPreviewId = null) }
        correctionSources.clear()
        clearLayoutPreview()
        clearCorrectionsPreview()
        clearFilterPreview()
    }

    private fun clearLayoutPreview() {
        layoutPreviews.clear()
        mutableState.update { it.copy(layoutPreview = DocumentToolPreviewState()) }
    }

    private fun clearCorrectionsPreview() {
        correctionCaptureOnly = false
        captureCorrections.clear()
        correctionsPreviews.clear()
        mutableState.update { it.copy(correctionsPreview = DocumentToolPreviewState()) }
    }

    private fun loadFormValues() {
        val repository = formValues ?: return
        viewModelScope.launch {
            try {
                val values = repository.list()
                mutableState.update { it.copy(savedFormValues = values, canStoreFormValues = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(canStoreFormValues = true, message = DocumentToolsMessage.FORM_VALUES_ERROR) }
            }
        }
    }

    private fun updateFormValues(action: suspend (FormValueRepository) -> List<com.joseleandro.scaniqo.domain.model.SavedFormValue>) {
        val repository = formValues ?: return
        if (!uiState.value.canEdit || uiState.value.isUpdatingFormValues) return
        mutableState.update { it.copy(isUpdatingFormValues = true) }
        viewModelScope.launch {
            try {
                val values = action(repository)
                mutableState.update { it.copy(savedFormValues = values) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(message = DocumentToolsMessage.FORM_VALUES_ERROR) }
            } finally {
                mutableState.update { it.copy(isUpdatingFormValues = false) }
            }
        }
    }

    private fun addImageMark(event: DocumentToolsEvent.AddImageMark) {
        val state = uiState.value
        val workspace = state.workspace ?: return
        if (!state.canEdit || !PdfOverlay("image", PdfOverlayKind.TEXT, x = event.x, y = event.y,
            width = event.width, height = event.height).isValid()) return
        if (workspace.pages.none { it.id in event.pageIds } ||
            workspace.pages.any { it.id in event.pageIds && it.signatures.size >= 20 }) return
        val operationSession = session
        pausePreviews()
        mutableState.update { it.copy(isCopyingSignature = true, message = null) }
        viewModelScope.launch {
            try {
                val copy = ioMutex.withLock { repository.copySignature(workspace, event.uri) }
                if (operationSession != session) return@launch
                mutableState.update { it.copy(isCopyingSignature = false) }
                val image = PdfSignaturePlacement(UUID.randomUUID().toString(), copy,
                    event.x, event.y, event.width, event.height)
                change { current ->
                    current.copy(pages = current.pages.map { page ->
                        if (page.id in event.pageIds) page.copy(signatures = page.signatures + image) else page
                    })
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (operationSession == session) mutableState.update { it.copy(message = DocumentToolsMessage.SIGNATURES_ERROR) }
            } finally {
                if (operationSession == session) {
                    mutableState.update { it.copy(isCopyingSignature = false) }
                    if (!uiState.value.isLocked) renderPreview()
                }
            }
        }
    }

    private fun saveRedactedCopy(event: DocumentToolsEvent.SaveRedactedCopy) {
        val state = uiState.value
        val workspace = state.workspace ?: return
        if (!state.canEdit || event.areas.isEmpty() || event.areas.any { !it.isValid() }) return
        if (requiresAuthentication() && !passwords.state.value.isUnlocked) {
            effectChannel.trySend(DocumentToolsEffect.NeedUnlock)
            return
        }
        val operationSession = session
        pausePreviews()
        mutableState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            try {
                ioMutex.withLock {
                    val key = workspace.protectionKeyId ?: sourceProtectionKeyId
                    if (workspace.isProtected || state.document?.isProtected == true) {
                        passwords.withPassword(checkNotNull(key)) { password ->
                            repository.saveRedactedCopy(workspace, event.areas, password, key)
                        }
                    } else repository.saveRedactedCopy(workspace, event.areas)
                }
                if (operationSession == session) mutableState.update {
                    it.copy(message = DocumentToolsMessage.REDACTED_COPY_SAVED, redactionSaveVersion = it.redactionSaveVersion + 1)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (operationSession == session) mutableState.update { it.copy(message = DocumentToolsMessage.SAVE_ERROR) }
            } finally {
                if (operationSession == session) {
                    mutableState.update { it.copy(isSaving = false) }
                    if (!uiState.value.isLocked) renderPreview()
                }
            }
        }
    }

    private fun save() {
        val state = uiState.value
        val workspace = state.workspace ?: return
        if (!state.canEdit || !state.hasChanges || state.signatureDraft != null) return
        if (requiresAuthentication() && !passwords.state.value.isUnlocked) {
            effectChannel.trySend(DocumentToolsEffect.NeedUnlock)
            return
        }
        if (workspace.isProtected && workspace.protectionKeyId == null && passwords.activeKeyId == null) {
            effectChannel.trySend(DocumentToolsEffect.NavigatePasswordSettings)
            return
        }
        pausePreviews()
        mutableState.update { it.copy(isSaving = true, isRendering = false, tool = null, message = null, isComparingOriginal = false) }
        val operationSession = session
        viewModelScope.launch {
            var refreshSavedPreview = false
            try {
                val saved = ioMutex.withLock {
                    if (workspace.isProtected) {
                        val key = checkNotNull(workspace.protectionKeyId ?: passwords.activeKeyId)
                        passwords.withPassword(key) { password -> repository.save(workspace, password, key) }
                    } else if (state.document?.protectionKeyId != null) {
                        val key = checkNotNull(sourceProtectionKeyId)
                        passwords.withPassword(key) { password ->
                            repository.save(workspace, password = password, removeProtection = true)
                        }
                    } else repository.save(workspace)
                }
                if (operationSession != session) return@launch
                baseline = workspace
                refreshSavedPreview = true
                undoHistory.clear()
                sourceProtectionKeyId = saved.protectionKeyId
                mutableState.update {
                    it.copy(document = saved, hasChanges = false, canUndo = false, message = DocumentToolsMessage.SAVED)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (operationSession == session) mutableState.update { it.copy(message = DocumentToolsMessage.SAVE_ERROR) }
            } finally {
                if (operationSession == session) {
                    mutableState.update { it.copy(isSaving = false) }
                    if (!uiState.value.isLocked) renderPreview(force = refreshSavedPreview)
                }
            }
        }
    }

    private fun requestBack() {
        val state = uiState.value
        if (state.isBusy) return
        when {
            state.signatureDraft != null -> onEvent(DocumentToolsEvent.CancelSignature)
            state.tool != null -> onEvent(DocumentToolsEvent.DismissTool)
            state.hasChanges -> mutableState.update { it.copy(confirmDiscard = true) }
            else -> close()
        }
    }

    private fun close() {
        if (uiState.value.isBusy) return
        val workspace = uiState.value.workspace
        mergeCoordinator.clear()
        pausePreviews()
        mutableState.update { it.copy(isDiscarding = true, previewUri = null, confirmDiscard = false) }
        val closingSession = session
        viewModelScope.launch {
            try {
                if (workspace != null) ioMutex.withLock { repository.discard(workspace) }
                if (closingSession != session) return@launch
                session++
                baseline = null
                pagePreviews.clear()
                clearToolPreviews()
                visiblePages.clear()
                undoHistory.clear()
                sourceProtectionKeyId = null
                mutableState.value = DocumentToolsUiState(signatures = uiState.value.signatures,
                    savedFormValues = uiState.value.savedFormValues, canStoreFormValues = formValues != null)
                effectChannel.send(DocumentToolsEffect.NavigateBack)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (closingSession == session) mutableState.update { it.copy(isDiscarding = false, message = DocumentToolsMessage.CLEANUP_ERROR) }
            }
        }
    }

    override fun onCleared() {
        pagePreviews.clear()
        clearToolPreviews()
        cancelAnalysis(resumePreview = false)
        mergeCoordinator.clear()
        effectChannel.close()
        super.onCleared()
    }
}
