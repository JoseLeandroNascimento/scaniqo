package com.joseleandro.scaniqo.domain.model

import java.io.File

object DocumentBackupLimits {
    const val MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_UNCOMPRESSED_BYTES = MAX_ARCHIVE_BYTES
    const val MAX_DOCUMENTS = 10_000
    const val MAX_MANIFEST_BYTES = 8L * 1024 * 1024
}

data class DocumentBackupProgress(val completedDocuments: Int, val totalDocuments: Int)

data class PreparedDocumentBackup(
    val file: File,
    val documentCount: Int,
    val protectedCount: Int,
    val editableProjectCount: Int,
)

data class DocumentBackupRestoreResult(
    val documentCount: Int,
    val protectedCount: Int,
    val editableProjectCount: Int,
)
