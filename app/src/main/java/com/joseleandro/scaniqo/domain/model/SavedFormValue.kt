package com.joseleandro.scaniqo.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedFormValue(val id: String, val label: String, val value: String) {
    fun isValid() = id.isNotBlank() && label.isNotBlank() && label.length <= 60 &&
        value.isNotBlank() && value.length <= 2000
}
