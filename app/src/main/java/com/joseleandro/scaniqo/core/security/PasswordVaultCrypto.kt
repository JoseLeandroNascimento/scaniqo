package com.joseleandro.scaniqo.core.security

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object PasswordVaultCrypto {
    const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(key: ByteArray, plaintext: ByteArray, vaultId: String): VaultCiphertext {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        cipher.updateAAD(payloadAad(vaultId))
        return VaultCiphertext(cipher.iv, cipher.doFinal(plaintext))
    }

    fun decrypt(key: ByteArray, ciphertext: VaultCiphertext, vaultId: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, ciphertext.iv),
        )
        cipher.updateAAD(payloadAad(vaultId))
        return cipher.doFinal(ciphertext.bytes)
    }

    fun wrappingAad(vaultId: String, mode: VaultAuthenticationMode): ByteArray =
        ("Scaniqo.PasswordVault.wrap.v1:" + vaultId + ":" + mode.name).toByteArray(Charsets.UTF_8)

    private fun payloadAad(vaultId: String): ByteArray =
        ("Scaniqo.PasswordVault.payload.v1:" + vaultId).toByteArray(Charsets.UTF_8)
}
