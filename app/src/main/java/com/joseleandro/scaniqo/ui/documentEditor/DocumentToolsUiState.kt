package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.domain.model.PdfPagePreview
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.SignatureModel

enum class DocumentTool { LAYOUT, FILTERS, CORRECTIONS, QUALITY, PAGES, MERGE, SIGNATURES, PROTECTION }
enum class DocumentToolsMessage { SAVED, SAVE_ERROR, SCAN_ERROR, APPEND_ERROR, MERGED, MERGE_ERROR, MERGE_LIMIT_ERROR, SIGNATURES_ERROR, CLEANUP_ERROR }

data class DocumentToolsUiState(
    val document: DocumentModel? = null,
    val workspace: PdfWorkspace? = null,
    val signatures: List<SignatureModel> = emptyList(),
    val selectedPageId: String? = null,
    val previewUri: String? = null,
    val pagePreviews: Map<String, DocumentPagePreviewState> = emptyMap(),
    val layoutPreview: PdfPagePreview? = null,
    val layoutLivePreview: DocumentLivePreviewState = DocumentLivePreviewState(),
    val layoutPreviewPageId: String? = null,
    val isPreviewingLayout: Boolean = false,
    val layoutPreviewError: Boolean = false,
    val layoutPreviewShowingOriginal: Boolean = false,
    val correctionsPreview: PdfPagePreview? = null,
    val correctionsLivePreview: DocumentLivePreviewState = DocumentLivePreviewState(),
    val correctionsPreviewPageId: String? = null,
    val isPreviewingCorrections: Boolean = false,
    val correctionsPreviewError: Boolean = false,
    val correctionsPreviewShowingOriginal: Boolean = false,
    val filterPreview: PdfPagePreview? = null,
    val filterPreviewPageId: String? = null,
    val isPreviewingFilter: Boolean = false,
    val filterPreviewError: Boolean = false,
    val filterPreviewShowingOriginal: Boolean = false,
    val filterThumbnails: Map<PdfImageFilter, String> = emptyMap(),
    val filterThumbnailsPageId: String? = null,
    val isLoadingFilterThumbnails: Boolean = false,
    val filterThumbnailsError: Boolean = false,
    val filterThumbnailsRequireRasterization: Boolean = false,
    val isLoading: Boolean = false,
    val isRendering: Boolean = false,
    val isSaving: Boolean = false,
    val isAppending: Boolean = false,
    val isCopyingSignature: Boolean = false,
    val isDiscarding: Boolean = false,
    val isScanning: Boolean = false,
    val isPickingPdfMerge: Boolean = false,
    val isAwaitingMergeUnlock: Boolean = false,
    val isMerging: Boolean = false,
    val mergePasswordUri: String? = null,
    val mergePasswordName: String? = null,
    val mergePasswordError: Boolean = false,
    val isLocked: Boolean = false,
    val loadError: Boolean = false,
    val recoveryRequired: Boolean = false,
    val previewError: Boolean = false,
    val hasChanges: Boolean = false,
    val qualityReport: PdfQualityReport? = null,
    val isAnalyzing: Boolean = false,
    val analysisError: Boolean = false,
    val isComparingOriginal: Boolean = false,
    val displayedComparisonPageId: String? = null,
    val canUndo: Boolean = false,
    val tool: DocumentTool? = null,
    val pendingDeletePageId: String? = null,
    val confirmDiscard: Boolean = false,
    val signatureDraft: PdfSignaturePlacement? = null,
    val signaturePageId: String? = null,
    val message: DocumentToolsMessage? = null,
) {
    val isBusy: Boolean
        get() = isLoading || isSaving || isAppending || isCopyingSignature || isDiscarding || isScanning ||
            isPickingPdfMerge || isAwaitingMergeUnlock || isMerging || mergePasswordUri != null || isAnalyzing
    val canEdit: Boolean
        get() = workspace != null && !isLocked && !isBusy
    val selectedPageIndex: Int
        get() = workspace?.pages?.indexOfFirst { it.id == selectedPageId }?.coerceAtLeast(0) ?: 0
    /** Só destaca uma opção quando o documento inteiro usa o mesmo filtro da barra. */
    val globalImageFilter: PdfImageFilter?
        get() {
            val pages = workspace?.pages?.takeIf { it.isNotEmpty() } ?: return null
            val filter = pages.first().layout.imageFilter
            return filter.takeIf {
                pages.all { page ->
                    page.layout.imageFilter == filter &&
                        (filter == PdfImageFilter.ORIGINAL || page.layout.filterIntensity == 1f)
                }
            }
        }
    val canExport: Boolean
        get() = canEdit && !hasChanges && signatureDraft == null
    val canCompare: Boolean
        get() = workspace?.pages?.find { it.id == selectedPageId }?.layout?.let {
            (it.imageFilter != PdfImageFilter.ORIGINAL && it.filterIntensity > 0f) ||
                it.straightenDegrees != 0f || it.perspective != null || it.curvature != 0f
        } == true
}
