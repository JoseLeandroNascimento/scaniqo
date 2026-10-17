package com.joseleandro.scaniqo.core.security

object PasswordPolicy {
    const val MIN_GENERAL_LENGTH = 8
    const val MAX_UTF8_BYTES = 127

    fun isValidGeneral(password: String): Boolean =
        password.codePointCount(0, password.length) >= MIN_GENERAL_LENGTH &&
            isValidDocument(password)

    fun isValidDocument(password: String): Boolean {
        val bytes = password.toByteArray(Charsets.UTF_8)
        return try {
            bytes.size in 1..MAX_UTF8_BYTES
        } finally {
            bytes.fill(0)
        }
    }
}
