package com.joseleandro.scaniqo.data.backup

/** Obtém autorização sem abrir Activity; consentimento pendente deve resultar em Unauthorized. */
fun interface BackupAuthorizationProvider {
    suspend fun accessToken(accountEmail: String): String

    /** Remove apenas o token inválido do cache do Google Play Services. */
    suspend fun invalidate(accessToken: String) = Unit
}
