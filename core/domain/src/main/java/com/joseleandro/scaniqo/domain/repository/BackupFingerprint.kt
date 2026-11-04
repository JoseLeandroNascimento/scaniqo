package com.joseleandro.scaniqo.domain.repository

import java.io.File

/** Identifies the exported document content without exposing its storage or archive format. */
interface BackupFingerprint {
    suspend fun current(): String?
    suspend fun fromArchive(file: File): String
    fun recognizes(value: String?): Boolean
}
