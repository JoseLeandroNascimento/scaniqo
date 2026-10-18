package com.joseleandro.scaniqo.domain.repository

import com.joseleandro.scaniqo.domain.model.DriveBackupEntry
import java.io.File

/** O chamador fornece um token drive.file em memória; a implementação nunca o persiste. */
interface DriveBackupRemote {
    suspend fun listBackups(accessToken: String): List<DriveBackupEntry>

    suspend fun upload(
        accessToken: String,
        archive: File,
        name: String,
        onProgress: (transferredBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): DriveBackupEntry

    /** O destino só é publicado após a transferência completa; falhas preservam o arquivo anterior. */
    suspend fun download(
        accessToken: String,
        entry: DriveBackupEntry,
        destination: File,
        onProgress: (transferredBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    )

    suspend fun accountEmail(accessToken: String): String?
}
