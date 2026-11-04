package com.joseleandro.scaniqo.core.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Separa a criação do CryptoObject do uso efetivo da chave protegida pelo Keystore.
 * Até updateAAD exige autenticação; antes do prompt só podemos inicializar o Cipher.
 */
internal object PasswordVaultKeyCipher {
    fun prepare(
        key: SecretKey,
        encrypted: VaultCiphertext? = null,
        cipher: Cipher = Cipher.getInstance(PasswordVaultCrypto.TRANSFORMATION),
    ): Cipher = cipher.apply {
        if (encrypted == null) {
            // O provider gera um IV novo; fornecer um IV próprio enfraqueceria essa garantia.
            init(Cipher.ENCRYPT_MODE, key)
        } else {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.iv))
        }
    }

    /** Chamado somente com o mesmo Cipher autorizado pelo resultado do prompt. */
    fun encryptAuthenticated(
        cipher: Cipher,
        dataKey: ByteArray,
        vaultId: String,
        mode: VaultAuthenticationMode,
    ): VaultCiphertext {
        // Copiamos o IV antes de doFinal, pois o provider pode encerrar o estado da operação.
        val iv = checkNotNull(cipher.iv).copyOf()
        check(iv.size == 12)
        cipher.updateAAD(PasswordVaultCrypto.wrappingAad(vaultId, mode))
        return VaultCiphertext(iv, cipher.doFinal(dataKey))
    }

    fun decryptAuthenticated(
        cipher: Cipher,
        encrypted: VaultCiphertext,
        vaultId: String,
        mode: VaultAuthenticationMode,
    ): ByteArray {
        cipher.updateAAD(PasswordVaultCrypto.wrappingAad(vaultId, mode))
        return cipher.doFinal(encrypted.bytes)
    }
}
