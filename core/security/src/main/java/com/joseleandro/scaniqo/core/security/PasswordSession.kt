package com.joseleandro.scaniqo.core.security

/** Mantém somente a chave do cofre, com prazo de inatividade e descarte explícito do buffer. */
internal class PasswordSession(
    private val now: () -> Long,
    private val timeoutMillis: Long = 5 * 60 * 1_000L,
) {
    private var key: ByteArray? = null
    private var expiresAt = 0L

    @Synchronized
    fun unlock(value: ByteArray) {
        lock()
        key = value.copyOf()
        expiresAt = now() + timeoutMillis
    }

    @Synchronized
    fun isUnlocked(): Boolean {
        if (key != null && now() >= expiresAt) lock()
        return key != null
    }

    @Synchronized
    fun copyKey(): ByteArray {
        if (!isUnlocked()) throw PasswordVaultLockedException()
        expiresAt = now() + timeoutMillis
        return checkNotNull(key).copyOf()
    }

    @Synchronized
    fun lock() {
        key?.fill(0)
        key = null
        expiresAt = 0L
    }
}
