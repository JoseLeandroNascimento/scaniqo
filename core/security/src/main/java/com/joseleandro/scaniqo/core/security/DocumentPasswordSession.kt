package com.joseleandro.scaniqo.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Only opaque identifiers are persisted with PDFs. Passwords expire and never leave memory. */
internal class DocumentPasswordSession(
    private val now: () -> Long,
    private val timeoutMillis: Long = 5 * 60 * 1_000L,
) {
    private class Entry(val salt: ByteArray, val digest: ByteArray, var secret: ByteArray?, var expiresAt: Long)
    private val entries = linkedMapOf<String, Entry>()

    @Synchronized
    fun remember(password: String, keyId: String? = null): String {
        require(PasswordPolicy.isValidDocument(password))
        val id = keyId ?: newDocumentPasswordKey()
        val bytes = password.toByteArray(Charsets.UTF_8)
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        entries.remove(id)?.clear()
        entries[id] = Entry(salt, digest(salt, bytes), bytes, now() + timeoutMillis)
        return id
    }

    @Synchronized
    fun availableKeys(): Set<String> {
        entries.values.forEach { if (now() >= it.expiresAt) { it.secret?.fill(0); it.secret = null } }
        return entries.filterValues { it.secret != null }.keys.toSet()
    }

    @Synchronized
    fun copyPassword(keyId: String): ByteArray? {
        if (keyId !in availableKeys()) return null
        return entries.getValue(keyId).let { it.expiresAt = now() + timeoutMillis; it.secret?.copyOf() }
    }

    /** Verifies an unsaved protection draft after backgrounding, without retaining its password. */
    @Synchronized
    fun matches(keyId: String, password: String): Boolean? {
        val entry = entries[keyId] ?: return null
        val bytes = password.toByteArray(Charsets.UTF_8)
        val candidate = digest(entry.salt, bytes)
        return try { MessageDigest.isEqual(entry.digest, candidate) }
        finally { bytes.fill(0); candidate.fill(0) }
    }

    @Synchronized
    fun lock() {
        entries.values.forEach { it.secret?.fill(0); it.secret = null }
    }

    @Synchronized
    fun forget(keyId: String) { entries.remove(keyId)?.clear() }

    private fun Entry.clear() { secret?.fill(0); salt.fill(0); digest.fill(0) }
    private fun digest(salt: ByteArray, bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run { update(salt); digest(bytes) }
}

private const val DOCUMENT_PASSWORD_PREFIX = "pdf-password:"
fun newDocumentPasswordKey(): String = DOCUMENT_PASSWORD_PREFIX + UUID.randomUUID()
fun isDocumentPasswordKey(keyId: String?): Boolean = keyId?.startsWith(DOCUMENT_PASSWORD_PREFIX) == true
