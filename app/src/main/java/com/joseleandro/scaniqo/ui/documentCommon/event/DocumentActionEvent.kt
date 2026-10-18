package com.joseleandro.scaniqo.ui.documentCommon.event

import com.joseleandro.scaniqo.domain.model.DocumentModel
import com.joseleandro.scaniqo.domain.model.ScannedDocument

/** Ações de documento reutilizadas pela lista, pelo editor e pelos contratos Android. */
sealed interface DocumentActionEvent {
    data class StartScan(val defaultTagId: Long? = null) : DocumentActionEvent
    data class ImportStarted(val defaultTagId: Long? = null) : DocumentActionEvent
    data class ImportPrepared(val scan: ScannedDocument, val name: String) : DocumentActionEvent
    data object ImportCancelled : DocumentActionEvent
    data class ScanCompleted(val pageUris: List<String>) : DocumentActionEvent
    data object ScanCancelled : DocumentActionEvent
    data object ScanFailed : DocumentActionEvent
    data class EditMetadata(val document: DocumentModel) : DocumentActionEvent
    data class NameChanged(val name: String) : DocumentActionEvent
    data class TagChanged(val tagId: Long?) : DocumentActionEvent
    data object SaveDocument : DocumentActionEvent
    data object DismissEditor : DocumentActionEvent
    data class RequestDelete(val document: DocumentModel) : DocumentActionEvent
    data object ConfirmDelete : DocumentActionEvent
    data object DismissDelete : DocumentActionEvent
    data class Download(val document: DocumentModel) : DocumentActionEvent
    data class ExportDestinationSelected(val uri: String?) : DocumentActionEvent
    data object ExportFailed : DocumentActionEvent
    data class Share(val document: DocumentModel) : DocumentActionEvent
    data object ShareFailed : DocumentActionEvent
    data object DismissMessage : DocumentActionEvent
    data object RetryTags : DocumentActionEvent
}
