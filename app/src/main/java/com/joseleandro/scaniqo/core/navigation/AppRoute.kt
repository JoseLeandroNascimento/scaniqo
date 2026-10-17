package com.joseleandro.scaniqo.core.navigation

import androidx.navigation3.runtime.NavKey
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
    @SerialName("tags")
    data object Tags : AppRoute

    @Serializable
    @SerialName("signatures")
    data object Signatures : AppRoute

    @Serializable
    @SerialName("password_settings")
    data object PasswordSettings : AppRoute
}
