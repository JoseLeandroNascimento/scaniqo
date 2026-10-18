package com.joseleandro.scaniqo.ui.documentEditor.state

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.domain.model.PdfSignaturePlacement
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentToolsMessage

data class DocumentToolsUiState(
    val document: DocumentModel? = null,
    val workspace: PdfWorkspace? = null,
    val signatures: List<SignatureModel> = emptyList(),
    val selectedPageId: String? = null,
    val previewUri: String? = null,
    val pagePreviews: Map<String, DocumentPagePreviewState> = emptyMap(),
    val layoutPreview: DocumentToolPreviewState = DocumentToolPreviewState(),
    val correctionsPreview: DocumentToolPreviewState = DocumentToolPreviewState(),
    val correctionSource: DocumentCaptureSourceState = DocumentCaptureSourceState(),
    val correctionCapturePreview: DocumentLivePreviewState = DocumentLivePreviewState(),
    val filterPreview: DocumentToolPreviewState = DocumentToolPreviewState(),
    val filterThumbnails: DocumentFilterThumbnailState = DocumentFilterThumbnailState(),
    val isLoading: Boolean = false,
    val isRendering: Boolean = false,
    val isSaving: Boolean = false,
    val isAppending: Boolean = false,
    val isCopyingSignature: Boolean = false,
    val isDiscarding: Boolean = false,
    val isScanning: Boolean = false,
    val merge: DocumentMergeUiState = DocumentMergeUiState.Idle,
    val isLocked: Boolean = false,
    val loadError: Boolean = false,
    val recoveryRequired: Boolean = false,
    val previewError: Boolean = false,
    val hasChanges: Boolean = false,
    val qualityReport: PdfQualityReport? = null,
    val isAnalyzing: Boolean = false,
    val analysisError: Boolean = false,
    /** Conclusão da análise solicitada na correção, mesmo quando o relatório não mudou. */
    val correctionAnalysisRequestId: String? = null,
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
            merge != DocumentMergeUiState.Idle || isAnalyzing
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
                it.straightenDegrees != 0f || it.perspective != null || it.curvature != 0f ||
                it.crop?.isIdentity() == false || it.backgroundMask != null
        } == true
}
