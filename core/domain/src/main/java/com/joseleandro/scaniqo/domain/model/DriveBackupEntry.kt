package com.joseleandro.scaniqo.domain.model

import java.io.IOException

data class DriveBackupEntry(
    val id: String,
    val name: String,
    val modifiedTimeMillis: Long,
    val sizeBytes: Long,
)

/** Erros sem tokens, URLs de sessão ou mensagens remotas que possam conter dados pessoais. */
sealed class DriveBackupException(message: String) : IOException(message) {
    class Unauthorized : DriveBackupException("Conecte sua conta Google novamente.")
    class Configuration : DriveBackupException("A configuração de autorização do aplicativo é inválida.")
    class ApiDisabled : DriveBackupException("A API do Google Drive não está habilitada para este aplicativo.")
    class AccessBlocked : DriveBackupException("O acesso ao Google Drive foi bloqueado pelo administrador da conta.")
    class Quota : DriveBackupException("O Google Drive atingiu um limite de armazenamento ou de solicitações.")
    class Network : DriveBackupException("Não foi possível concluir a conexão com o Google Drive.")
    class Remote(val statusCode: Int? = null) :
        DriveBackupException("O Google Drive não conseguiu concluir esta operação.")
}
