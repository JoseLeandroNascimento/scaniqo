package com.joseleandro.scaniqo.core.security

import kotlinx.coroutines.flow.StateFlow

data class PasswordVaultState(
    val isConfigured: Boolean = false,
    val isUnlocked: Boolean = false,
    val recoveryRequired: Boolean = false,
    val availableDocumentKeys: Set<String> = emptySet(),
)

/** A interface expõe acesso temporário; nenhum segredo faz parte do estado da interface. */
interface PasswordAccess {
    val state: StateFlow<PasswordVaultState>
    val activeKeyId: String?

    suspend fun <T> withPassword(
        keyId: String?,
        block: suspend (String) -> T,
    ): T

    fun isPasswordAvailable(keyId: String?): Boolean =
        keyId != null && (keyId in state.value.availableDocumentKeys ||
            (!isDocumentPasswordKey(keyId) && state.value.isUnlocked))

    /** New document credentials are memory-only; this never changes the legacy vault. */
    suspend fun rememberDocumentPassword(password: String, keyId: String? = null): String =
        throw UnsupportedOperationException("Document password session unavailable.")

    fun matchesDocumentPassword(keyId: String, password: String): Boolean? = null
    fun forgetDocumentPassword(keyId: String) = Unit
    fun lock()
}

class PasswordVaultLockedException : IllegalStateException("Autenticação necessária.")
class PasswordVaultRecoveryException : IllegalStateException("O cofre precisa ser recuperado.")
class PasswordVersionMissingException : IllegalStateException("Versão da senha indisponível.")
class PasswordVaultUnavailableException : IllegalStateException("Configure o bloqueio de tela.")
