package com.joseleandro.scaniqo.core.security

import kotlinx.coroutines.flow.StateFlow

data class PasswordVaultState(
    val isConfigured: Boolean = false,
    val isUnlocked: Boolean = false,
    val recoveryRequired: Boolean = false,
)

/** A interface expõe acesso temporário; nenhum segredo faz parte do estado da interface. */
interface PasswordAccess {
    val state: StateFlow<PasswordVaultState>
    val activeKeyId: String?

    suspend fun <T> withPassword(
        keyId: String? = activeKeyId,
        block: suspend (String) -> T,
    ): T

    fun lock()
}

class PasswordVaultLockedException : IllegalStateException("Autenticação necessária.")
class PasswordVaultRecoveryException : IllegalStateException("O cofre precisa ser recuperado.")
class PasswordVersionMissingException : IllegalStateException("Versão da senha indisponível.")
class PasswordVaultUnavailableException : IllegalStateException("Configure o bloqueio de tela.")
