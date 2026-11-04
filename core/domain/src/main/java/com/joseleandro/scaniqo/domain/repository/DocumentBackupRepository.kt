package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.DocumentBackupProgress
import com.joseleandro.scaniqo.domain.model.DocumentBackupRestoreResult
import com.joseleandro.scaniqo.domain.model.PreparedDocumentBackup
import java.io.File

/** Cópias manuais de documentos salvos; o cofre e as sessões de edição nunca são exportados. */
interface DocumentBackupRepository {
    suspend fun createBackup(onProgress: (DocumentBackupProgress) -> Unit = {}): PreparedDocumentBackup
    suspend fun createRestoreFile(): File
    suspend fun restoreBackup(file: File, onProgress: (DocumentBackupProgress) -> Unit = {}): DocumentBackupRestoreResult
    suspend fun discard(file: File)
}
