package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.util.UUID

internal object PdfProtection {
    fun apply(document: PDDocument, password: String) {
        require(password.isNotBlank() && password.toByteArray(Charsets.UTF_8).size <= 127) {
            "A senha deve conter entre 1 e 127 bytes."
        }
        document.version = 1.7f
        document.protect(
            StandardProtectionPolicy(UUID.randomUUID().toString(), password, AccessPermission()).apply {
                encryptionKeyLength = 256
                isPreferAES = true
            }
        )
    }
}
