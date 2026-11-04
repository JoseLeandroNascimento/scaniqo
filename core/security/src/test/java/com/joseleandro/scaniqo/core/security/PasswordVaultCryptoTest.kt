package com.joseleandro.scaniqo.core.security

import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PasswordVaultCryptoTest {
    // Valores determinísticos exclusivos dos testes, sem relação com credenciais reais.
    private val key = ByteArray(32) { it.toByte() }
    private val content = "conteúdo fictício".toByteArray(Charsets.UTF_8)

    @Test
    fun encryptedPayloadCanBeReadWithItsKeyAndVaultIdentity() {
        val encrypted = PasswordVaultCrypto.encrypt(key, content, "vault-a")

        assertFalse(encrypted.bytes.contentEquals(content))
        assertArrayEquals(content, PasswordVaultCrypto.decrypt(key, encrypted, "vault-a"))
    }

    @Test
    fun eachWriteUsesAnIndependentInitializationVector() {
        val first = PasswordVaultCrypto.encrypt(key, content, "vault-a")
        val second = PasswordVaultCrypto.encrypt(key, content, "vault-a")

        assertFalse(first.iv.contentEquals(second.iv))
        assertFalse(first.bytes.contentEquals(second.bytes))
    }

    @Test
    fun changingCiphertextIsRejected() {
        val encrypted = PasswordVaultCrypto.encrypt(key, content, "vault-a")
        encrypted.bytes[0] = (encrypted.bytes[0].toInt() xor 1).toByte()

        assertThrows(AEADBadTagException::class.java) {
            PasswordVaultCrypto.decrypt(key, encrypted, "vault-a")
        }
    }

    @Test
    fun anotherVaultCannotReuseTheEncryptedPayload() {
        val encrypted = PasswordVaultCrypto.encrypt(key, content, "vault-a")

        assertThrows(AEADBadTagException::class.java) {
            PasswordVaultCrypto.decrypt(key, encrypted, "vault-b")
        }
    }

    @Test
    fun anotherKeyCannotReadTheEncryptedPayload() {
        val encrypted = PasswordVaultCrypto.encrypt(key, content, "vault-a")

        assertThrows(AEADBadTagException::class.java) {
            PasswordVaultCrypto.decrypt(ByteArray(32) { 9 }, encrypted, "vault-a")
        }
    }

    @Test
    fun wrappingContextSeparatesVaultsAndAuthenticationModes() {
        val combined = PasswordVaultCrypto.wrappingAad("vault-a", VaultAuthenticationMode.COMBINED)

        assertFalse(combined.contentEquals(
            PasswordVaultCrypto.wrappingAad("vault-b", VaultAuthenticationMode.COMBINED),
        ))
        assertFalse(combined.contentEquals(
            PasswordVaultCrypto.wrappingAad("vault-a", VaultAuthenticationMode.BIOMETRIC),
        ))
    }
}
