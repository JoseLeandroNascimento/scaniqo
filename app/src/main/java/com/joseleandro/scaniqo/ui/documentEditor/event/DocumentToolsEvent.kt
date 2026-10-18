package com.joseleandro.scaniqo.ui.documentEditor.event

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.PageAlignment
import com.joseleandro.scaniqo.domain.model.PageScaleMode
import com.joseleandro.scaniqo.domain.model.PdfExportQuality
import com.joseleandro.scaniqo.domain.model.PdfImageFilter
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool

sealed interface DocumentToolsEvent {
    data class SelectPage(val pageId: String) : DocumentToolsEvent
    data class PageVisibility(val pageId: String, val visible: Boolean) : DocumentToolsEvent
    data class RetryPagePreview(val pageId: String) : DocumentToolsEvent
    data class PreviewLayout(
        val pageId: String,
        val layout: PdfPageLayout,
        val showOriginal: Boolean = false,
    ) : DocumentToolsEvent

    data object ClearLayoutPreview : DocumentToolsEvent
    data class PreviewCorrections(
        val pageId: String,
        val layout: PdfPageLayout,
        val showOriginal: Boolean = false,
    ) : DocumentToolsEvent

    data object ClearCorrectionsPreview : DocumentToolsEvent
    data class PreviewFilter(
        val pageId: String,
        val layout: PdfPageLayout,
        val showOriginal: Boolean = false,
    ) : DocumentToolsEvent

    data object ClearFilterPreview : DocumentToolsEvent
    data class LoadFilterThumbnails(
        val pageId: String,
        val rasterizeText: Boolean = false,
        val force: Boolean = false,
    ) : DocumentToolsEvent

    data class OpenTool(val tool: DocumentTool) : DocumentToolsEvent
    data object DismissTool : DocumentToolsEvent
    data class SelectGlobalFilter(val imageFilter: PdfImageFilter) : DocumentToolsEvent
    data class ApplyLayout(
        val alignment: PageAlignment,
        val scaleMode: PageScaleMode,
        val allPages: Boolean,
        val rotationQuarterTurns: Int = 0,
        val contentScale: Float = 1f,
        val pageIds: Set<String>? = null,
    ) : DocumentToolsEvent

    data class ApplyFilter(
        val imageFilter: PdfImageFilter,
        val allPages: Boolean,
        val intensity: Float = 1f,
        val rasterizeText: Boolean = false,
        val pageIds: Set<String>? = null,
    ) : DocumentToolsEvent

    data class ApplyCorrections(
        val allPages: Boolean,
        val straightenDegrees: Float,
        val perspective: PdfPerspective?,
        val curvature: Float,
        val rasterizeText: Boolean = false,
        val pageIds: Set<String>? = null,
    ) : DocumentToolsEvent

    data class AutoImprove(val allPages: Boolean) : DocumentToolsEvent
    data object AnalyzeQuality : DocumentToolsEvent
    data class AnalyzeCorrectionPage(val pageId: String) : DocumentToolsEvent
    data object CancelAnalysis : DocumentToolsEvent
    data class SetExportOptions(val quality: PdfExportQuality, val ocrEnabled: Boolean) :
        DocumentToolsEvent

    data class SetCompareOriginal(val enabled: Boolean) : DocumentToolsEvent
    data object Undo : DocumentToolsEvent
    data class SetNumbering(val enabled: Boolean) : DocumentToolsEvent
    data class MovePage(val pageId: String, val offset: Int) : DocumentToolsEvent
    data class MovePageTo(val pageId: String, val targetIndex: Int) : DocumentToolsEvent
    data class ReorderPages(val pageIds: List<String>) : DocumentToolsEvent
    data class RequestDeletePage(val pageId: String) : DocumentToolsEvent
    data object ConfirmDeletePage : DocumentToolsEvent
    data object DismissPageDeletion : DocumentToolsEvent
    data object AddPages : DocumentToolsEvent
    data class ScanCompleted(val pageUris: List<String>) : DocumentToolsEvent
    data object ScanCancelled : DocumentToolsEvent
    data object ScanFailed : DocumentToolsEvent
    data class MergePdfs(val insertionIndex: Int) : DocumentToolsEvent
    data class MergeAppDocuments(
        val documents: List<DocumentModel>,
        val insertionIndex: Int,
    ) : DocumentToolsEvent

    data class MergePdfsSelected(val uris: List<String>) : DocumentToolsEvent
    data object MergePdfsCancelled : DocumentToolsEvent
    data object MergePdfsFailed : DocumentToolsEvent
    data object CancelPdfMerge : DocumentToolsEvent
    data class SubmitMergePassword(val password: String) : DocumentToolsEvent
    data object CancelMergePassword : DocumentToolsEvent
    data class SetProtection(val enabled: Boolean) : DocumentToolsEvent
    data object Unlock : DocumentToolsEvent
    data object AuthenticationSucceeded : DocumentToolsEvent
    data object AuthenticationDismissed : DocumentToolsEvent
    data object RequestPasswordRecovery : DocumentToolsEvent
    data class RecoverWithPassword(val password: String, val protectionKeyId: String?) :
        DocumentToolsEvent

    data object ManageSignatures : DocumentToolsEvent
    data object PasswordSettings : DocumentToolsEvent
    data class InsertSignature(val signatureId: String) : DocumentToolsEvent
    data class EditSignature(val pageId: String, val placementId: String) : DocumentToolsEvent
    data class MoveSignature(val x: Float, val y: Float) : DocumentToolsEvent
    data class ResizeSignature(val width: Float) : DocumentToolsEvent
    data object ConfirmSignature : DocumentToolsEvent
    data object CancelSignature : DocumentToolsEvent
    data class RemoveSignature(val pageId: String, val placementId: String) : DocumentToolsEvent
    data object Save : DocumentToolsEvent
    data object Retry : DocumentToolsEvent
    data object DismissMessage : DocumentToolsEvent
    data object RequestBack : DocumentToolsEvent
    data object ConfirmDiscard : DocumentToolsEvent
    data object CancelDiscard : DocumentToolsEvent
    data object Backgrounded : DocumentToolsEvent
}

