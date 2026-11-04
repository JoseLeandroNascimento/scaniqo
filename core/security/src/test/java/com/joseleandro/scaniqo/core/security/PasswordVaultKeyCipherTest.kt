package com.joseleandro.scaniqo.core.security

import java.security.AlgorithmParameters
import java.security.Key
import java.security.Provider
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.IllegalBlockSizeException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PasswordVaultKeyCipherTest {
    // Material fictício exclusivo dos testes. O provider de teste não participa do app.
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val dataKey = ByteArray(32) { (it + 1).toByte() }
    private val mode = VaultAuthenticationMode.COMBINED

    @Test
    fun preparationDoesNotSendAadBeforeTheBiometricResult() {
        val operation = AuthenticatedCipherFixture()
        val prepared = PasswordVaultKeyCipher.prepare(key, cipher = operation.cipher)

        assertSame(operation.cipher, prepared)
        assertEquals(listOf("init"), operation.spi.events)

        operation.spi.authenticate()
        val encrypted = PasswordVaultKeyCipher.encryptAuthenticated(
            prepared, dataKey, "vault", mode,
        )

        assertEquals(listOf("init", "authenticate", "aad", "finish"), operation.spi.events)
        assertEquals(12, encrypted.iv.size)
        assertEquals(48, encrypted.bytes.size)
    }

    @Test
    fun unlockAlsoWaitsForAuthenticationBeforeSendingAad() {
        val encryption = AuthenticatedCipherFixture()
        PasswordVaultKeyCipher.prepare(key, cipher = encryption.cipher)
        encryption.spi.authenticate()
        val encrypted = PasswordVaultKeyCipher.encryptAuthenticated(
            encryption.cipher, dataKey, "vault", mode,
        )
        val decryption = AuthenticatedCipherFixture()
        PasswordVaultKeyCipher.prepare(key, encrypted, decryption.cipher)

        assertEquals(listOf("init"), decryption.spi.events)
        decryption.spi.authenticate()
        val restored = PasswordVaultKeyCipher.decryptAuthenticated(
            decryption.cipher, encrypted, "vault", mode,
        )

        assertArrayEquals(dataKey, restored)
        assertEquals(listOf("init", "authenticate", "aad", "finish"), decryption.spi.events)
    }

    @Test
    fun successfulCallbackDoesNotRepairAnOperationUsedBeforeAuthentication() {
        val operation = AuthenticatedCipherFixture()
        PasswordVaultKeyCipher.prepare(key, cipher = operation.cipher)
        // Reproduz a ordem antiga: o Keystore guarda a falha de AAD e doFinal continua inválido.
        operation.cipher.updateAAD(PasswordVaultCrypto.wrappingAad("vault", mode))
        operation.spi.authenticate()

        assertThrows(IllegalBlockSizeException::class.java) {
            PasswordVaultKeyCipher.encryptAuthenticated(operation.cipher, dataKey, "vault", mode)
        }
    }

    @Test
    fun missingAuthenticationStillPreventsWrappingTheKey() {
        val operation = AuthenticatedCipherFixture()
        PasswordVaultKeyCipher.prepare(key, cipher = operation.cipher)

        assertThrows(IllegalBlockSizeException::class.java) {
            PasswordVaultKeyCipher.encryptAuthenticated(operation.cipher, dataKey, "vault", mode)
        }
    }

    @Test
    fun authenticatedWrappingStillRejectsAnotherVaultIdentity() {
        val encryption = AuthenticatedCipherFixture()
        PasswordVaultKeyCipher.prepare(key, cipher = encryption.cipher)
        encryption.spi.authenticate()
        val encrypted = PasswordVaultKeyCipher.encryptAuthenticated(
            encryption.cipher, dataKey, "vault-a", mode,
        )
        val decryption = AuthenticatedCipherFixture()
        PasswordVaultKeyCipher.prepare(key, encrypted, decryption.cipher)
        decryption.spi.authenticate()

        assertThrows(AEADBadTagException::class.java) {
            PasswordVaultKeyCipher.decryptAuthenticated(
                decryption.cipher, encrypted, "vault-b", mode,
            )
        }
    }

    private class AuthenticatedCipherFixture {
        val spi = AuthenticationRequiredCipherSpi()
        val cipher: Cipher = TestCipher(spi)
    }

    private class TestCipher(spi: CipherSpi) : Cipher(
        spi,
        object : Provider("ScaniqoTestOnly", 1.0, "Provider exclusivo de testes") {},
        PasswordVaultCrypto.TRANSFORMATION,
    )

    /**
     * Reproduz a regra do KeyMint: AAD exige token e a falha anterior invalida a operação.
     * A cifra delegada continua sendo AES-GCM real, inclusive a validação da integridade.
     */
    private class AuthenticationRequiredCipherSpi : CipherSpi() {
        private val delegate = Cipher.getInstance(PasswordVaultCrypto.TRANSFORMATION)
        val events = mutableListOf<String>()
        private var authenticated = false
        private var cachedAuthenticationFailure = false

        fun authenticate() {
            authenticated = true
            events += "authenticate"
        }

        override fun engineSetMode(mode: String) = Unit
        override fun engineSetPadding(padding: String) = Unit
        override fun engineGetBlockSize(): Int = delegate.blockSize
        override fun engineGetOutputSize(inputLen: Int): Int = delegate.getOutputSize(inputLen)
        override fun engineGetIV(): ByteArray? = delegate.iv
        override fun engineGetParameters(): AlgorithmParameters? = delegate.parameters

        override fun engineInit(opmode: Int, key: Key, random: SecureRandom?) {
            events += "init"
            delegate.init(opmode, key, random)
        }

        override fun engineInit(
            opmode: Int, key: Key, params: AlgorithmParameterSpec?, random: SecureRandom?,
        ) {
            events += "init"
            delegate.init(opmode, key, params, random)
        }

        override fun engineInit(
            opmode: Int, key: Key, params: AlgorithmParameters?, random: SecureRandom?,
        ) {
            events += "init"
            delegate.init(opmode, key, params, random)
        }

        override fun engineUpdateAAD(input: ByteArray, inputOffset: Int, inputLen: Int) {
            events += "aad"
            if (!authenticated) cachedAuthenticationFailure = true
            if (!cachedAuthenticationFailure) delegate.updateAAD(input, inputOffset, inputLen)
        }

        override fun engineUpdate(input: ByteArray, inputOffset: Int, inputLen: Int): ByteArray =
            delegate.update(input, inputOffset, inputLen)

        override fun engineUpdate(
            input: ByteArray, inputOffset: Int, inputLen: Int, output: ByteArray, outputOffset: Int,
        ): Int = delegate.update(input, inputOffset, inputLen, output, outputOffset)

        override fun engineDoFinal(input: ByteArray, inputOffset: Int, inputLen: Int): ByteArray {
            events += "finish"
            if (!authenticated || cachedAuthenticationFailure) {
                throw IllegalBlockSizeException("Operação de teste sem autenticação válida.")
            }
            return delegate.doFinal(input, inputOffset, inputLen)
        }

        override fun engineDoFinal(
            input: ByteArray, inputOffset: Int, inputLen: Int, output: ByteArray, outputOffset: Int,
        ): Int {
            val result = engineDoFinal(input, inputOffset, inputLen)
            result.copyInto(output, outputOffset)
            return result.size
        }
    }
}
