package com.joseleandro.scaniqo.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordVaultFormatTest {
    @Test
    fun changingTheActiveVersionRetainsOlderDocumentPasswords() {
        val older = "versao-antiga".toByteArray()
        val newer = "versao-nova".toByteArray()
        val original = VaultPayload("new", linkedMapOf("old" to older, "new" to newer))

        PasswordVaultFormat.decodePayload(PasswordVaultFormat.encodePayload(original)).use { restored ->
            assertEquals("new", restored.activeKeyId)
            assertArrayEquals(older, restored.versions["old"])
            assertArrayEquals(newer, restored.versions["new"])
        }
        original.close()
    }

    @Test
    fun deactivatingTheDefaultPreservesEveryStoredVersion() {
        val original = VaultPayload(null, linkedMapOf("old" to "original".toByteArray()))

        PasswordVaultFormat.decodePayload(PasswordVaultFormat.encodePayload(original)).use { restored ->
            assertNull(restored.activeKeyId)
            assertArrayEquals("original".toByteArray(), restored.versions["old"])
        }
        original.close()
    }

    @Test
    fun closingPayloadErasesItsBuffersAndEntries() {
        val bytes = byteArrayOf(1, 2, 3)
        val payload = VaultPayload("version", linkedMapOf("version" to bytes))

        payload.close()

        assertArrayEquals(ByteArray(3), bytes)
        assertTrue(payload.versions.isEmpty())
    }

    @Test
    fun activeVersionMustExistInTheDecodedPayload() {
        val encoded = PasswordVaultFormat.encodePayload(
            VaultPayload("missing", linkedMapOf("present" to byteArrayOf(1))),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PasswordVaultFormat.decodePayload(encoded)
        }
    }

    @Test
    fun trailingDataAndTruncationAreRejected() {
        val encoded = PasswordVaultFormat.encodePayload(
            VaultPayload("version", linkedMapOf("version" to byteArrayOf(1, 2))),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PasswordVaultFormat.decodePayload(encoded + byteArrayOf(0))
        }
        assertThrows(Exception::class.java) {
            PasswordVaultFormat.decodePayload(encoded.copyOf(encoded.size - 1))
        }
    }

    @Test
    fun passwordSizeLimitIsEnforcedBeforeWriting() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordVaultFormat.encodePayload(
                VaultPayload("version", linkedMapOf("version" to ByteArray(128))),
            )
        }
    }

    @Test
    fun envelopeRestoresBothAuthenticationMethodsAndEncryptedPayload() {
        val primary = VaultKeyEnvelope(
            "scaniqo.password.credential",
            VaultAuthenticationMode.DEVICE_CREDENTIAL,
            VaultCiphertext(ByteArray(12) { 1 }, ByteArray(48) { 2 }),
        )
        val biometric = VaultKeyEnvelope(
            "scaniqo.password.biometric",
            VaultAuthenticationMode.BIOMETRIC,
            VaultCiphertext(ByteArray(12) { 3 }, ByteArray(48) { 4 }),
        )
        val original = VaultEnvelope(
            "vault-id", "version-id", primary, biometric,
            VaultCiphertext(ByteArray(12) { 5 }, ByteArray(32) { 6 }),
        )

        val restored = PasswordVaultFormat.decodeEnvelope(PasswordVaultFormat.encodeEnvelope(original))

        assertEquals(original.id, restored.id)
        assertEquals(original.activeKeyId, restored.activeKeyId)
        assertEquals(primary.alias, restored.primary.alias)
        assertEquals(primary.mode, restored.primary.mode)
        assertArrayEquals(primary.ciphertext.bytes, restored.primary.ciphertext.bytes)
        assertEquals(biometric.alias, restored.biometric?.alias)
        assertArrayEquals(biometric.ciphertext.iv, restored.biometric?.ciphertext?.iv)
        assertArrayEquals(original.payload.bytes, restored.payload.bytes)
        assertArrayEquals(original.payload.iv, restored.payload.iv)
    }

    @Test
    fun envelopeRejectsInvalidHeaderAndUnexpectedTrailingBytes() {
        val envelope = VaultEnvelope(
            "vault", null,
            VaultKeyEnvelope(
                "scaniqo.password.key", VaultAuthenticationMode.COMBINED,
                VaultCiphertext(ByteArray(12), ByteArray(48)),
            ),
            null,
            VaultCiphertext(ByteArray(12), ByteArray(16)),
        )
        val encoded = PasswordVaultFormat.encodeEnvelope(envelope)

        assertThrows(IllegalArgumentException::class.java) {
            PasswordVaultFormat.decodeEnvelope(encoded + byteArrayOf(0))
        }
        encoded[0] = 0
        assertThrows(IllegalArgumentException::class.java) {
            PasswordVaultFormat.decodeEnvelope(encoded)
        }
    }
}
