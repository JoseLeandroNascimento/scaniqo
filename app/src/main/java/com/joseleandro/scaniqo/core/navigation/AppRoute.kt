package com.joseleandro.scaniqo.core.navigation

import androidx.navigation3.runtime.NavKey
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    @SerialName("documents")
    data object Documents : AppRoute

    @Serializable
    @SerialName("document_viewer")
    data class DocumentViewer(val documentId: Long) : AppRoute

    @Serializable
    @SerialName("document_signature")
    data class DocumentSignature(val documentId: Long) : AppRoute

    @Serializable
    @SerialName("document_merge")
    data class DocumentMerge(val documentId: Long? = null) : AppRoute

    @Serializable
    @SerialName("document_editor_tool")
    data class DocumentEditorTool(val documentId: Long, val tool: DocumentTool) : AppRoute

    @Serializable
    @SerialName("tags")
    data object Tags : AppRoute

    @Serializable
    @SerialName("signatures")
    data object Signatures : AppRoute

    @Serializable
    @SerialName("password_settings")
    data object PasswordSettings : AppRoute

    @Serializable
    @SerialName("drive_backup")
    data object DriveBackup : AppRoute
}
