package com.joseleandro.scaniqo.domain.model

data class BackupHistoryEntry(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val documentCount: Int? = null,
)
