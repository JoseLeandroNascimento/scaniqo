package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument

/** Intenções recebidas tanto da lista quanto do visualizador e dos contratos Android. */
sealed interface DocumentsEvent {
    data class SelectTag(val tagId: Long?) : DocumentsEvent
    data object StartScan : DocumentsEvent
    data object ImportStarted : DocumentsEvent
    data class ImportPrepared(val scan: ScannedDocument, val name: String) : DocumentsEvent
    data object ImportCancelled : DocumentsEvent
    data class ScanCompleted(val pageUris: List<String>) : DocumentsEvent
    data object ScanCancelled : DocumentsEvent
    data object ScanFailed : DocumentsEvent
    data class EditMetadata(val document: DocumentModel) : DocumentsEvent
    data class NameChanged(val name: String) : DocumentsEvent
    data class TagChanged(val tagId: Long?) : DocumentsEvent
    data object SaveDocument : DocumentsEvent
    data object DismissEditor : DocumentsEvent
    data class RequestDelete(val document: DocumentModel) : DocumentsEvent
    data object ConfirmDelete : DocumentsEvent
    data object DismissDelete : DocumentsEvent
    data class Download(val document: DocumentModel) : DocumentsEvent
    data class ExportDestinationSelected(val uri: String?) : DocumentsEvent
    data object ExportFailed : DocumentsEvent
    data class Share(val document: DocumentModel) : DocumentsEvent
    data object ShareFailed : DocumentsEvent
    data object DismissMessage : DocumentsEvent
    data object RetryLoad : DocumentsEvent
}

/** Efeitos de consumo único, executados pelo ponto central de integração com o Android. */
sealed interface DocumentsEffect {
    data object LaunchScanner : DocumentsEffect
    data class ChooseDownloadLocation(val fileName: String) : DocumentsEffect
    data class SharePdf(val uri: String, val name: String) : DocumentsEffect
}
