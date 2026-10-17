package com.joseleandro.scaniqo.core.security

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** Somente envelopes cifrados são gravados, fora dos diretórios incluídos no backup. */
internal class PasswordVaultStorage(context: Context) {
    private val directory = File(context.noBackupFilesDir, "password-vault")
    private val file = AtomicFile(File(directory, "vault.bin"))

    fun read(): VaultEnvelope? {
        if (!exists()) return null
        require(file.baseFile.length() <= 4 * 1024 * 1024)
        require(File(file.baseFile.path + ".bak").length() <= 4 * 1024 * 1024)
        return PasswordVaultFormat.decodeEnvelope(file.readFully())
    }

    fun write(value: VaultEnvelope) {
        check(directory.isDirectory || directory.mkdirs())
        val stream = file.startWrite()
        try {
            stream.write(PasswordVaultFormat.encodeEnvelope(value))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun exists(): Boolean =
        file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    fun preserveRecoveryCopy() {
        if (exists()) {
            val backup = File(directory, "recovery-" + System.currentTimeMillis() + ".bin")
            backup.writeBytes(file.readFully())
        }
    }
}
