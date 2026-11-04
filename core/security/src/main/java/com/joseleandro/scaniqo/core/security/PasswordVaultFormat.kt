package com.joseleandro.scaniqo.core.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal enum class VaultAuthenticationMode {
    COMBINED,
    BIOMETRIC,
    DEVICE_CREDENTIAL,
}

internal data class VaultCiphertext(val iv: ByteArray, val bytes: ByteArray)

internal data class VaultKeyEnvelope(
    val alias: String,
    val mode: VaultAuthenticationMode,
    val ciphertext: VaultCiphertext,
)

internal data class VaultEnvelope(
    val id: String,
    val activeKeyId: String?,
    val primary: VaultKeyEnvelope,
    val biometric: VaultKeyEnvelope?,
    val payload: VaultCiphertext,
)

/** Existe apenas durante uma operação; close apaga todas as versões decifradas. */
internal class VaultPayload(
    var activeKeyId: String?,
    val versions: MutableMap<String, ByteArray>,
) : AutoCloseable {
    override fun close() {
        versions.values.forEach { it.fill(0) }
        versions.clear()
    }
}

internal object PasswordVaultFormat {
    private const val MAGIC = 0x53435631
    private const val MAX_VERSIONS = 10_000
    private const val MAX_BLOB_BYTES = 4 * 1024 * 1024

    fun encodeEnvelope(value: VaultEnvelope): ByteArray = encode { output ->
        output.writeInt(MAGIC)
        output.writeUTF(value.id)
        output.writeNullableString(value.activeKeyId)
        output.writeKeyEnvelope(value.primary)
        output.writeBoolean(value.biometric != null)
        value.biometric?.let { output.writeKeyEnvelope(it) }
        output.writeCiphertext(value.payload)
    }

    fun decodeEnvelope(bytes: ByteArray): VaultEnvelope = decode(bytes) { input ->
        require(input.readInt() == MAGIC)
        val id = input.readUTF()
        val activeId = input.readNullableString()
        val primary = input.readKeyEnvelope()
        val biometric = if (input.readBoolean()) input.readKeyEnvelope() else null
        VaultEnvelope(id, activeId, primary, biometric, input.readCiphertext())
    }

    fun encodePayload(value: VaultPayload): ByteArray = encode { output ->
        require(value.versions.size <= MAX_VERSIONS)
        output.writeNullableString(value.activeKeyId)
        output.writeInt(value.versions.size)
        value.versions.forEach { (id, password) ->
            require(password.size in 1..PasswordPolicy.MAX_UTF8_BYTES)
            output.writeUTF(id)
            output.writeBytesWithSize(password)
        }
    }

    fun decodePayload(bytes: ByteArray): VaultPayload {
        val versions = linkedMapOf<String, ByteArray>()
        try {
            return decode(bytes) { input ->
                val activeId = input.readNullableString()
                val count = input.readInt()
                require(count in 1..MAX_VERSIONS)
                repeat(count) {
                    val id = input.readUTF()
                    require(id.isNotBlank() && !versions.containsKey(id))
                    val password = input.readBytesWithSize(PasswordPolicy.MAX_UTF8_BYTES)
                    require(password.isNotEmpty())
                    versions[id] = password
                }
                require(activeId == null || versions.containsKey(activeId))
                VaultPayload(activeId, versions)
            }
        } catch (error: Exception) {
            versions.values.forEach { it.fill(0) }
            throw error
        }
    }

    private fun encode(write: (DataOutputStream) -> Unit): ByteArray =
        WipeableBuffer().use { buffer ->
            val output = DataOutputStream(buffer)
            write(output)
            output.flush()
            buffer.toByteArray()
        }

    private class WipeableBuffer : ByteArrayOutputStream() {
        override fun close() {
            buf.fill(0)
            super.close()
        }
    }

    private fun <T> decode(bytes: ByteArray, read: (DataInputStream) -> T): T {
        require(bytes.size <= MAX_BLOB_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val result = read(input)
            require(input.available() == 0)
            result
        }
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readUTF() else null

    private fun DataOutputStream.writeBytesWithSize(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readBytesWithSize(limit: Int = MAX_BLOB_BYTES): ByteArray {
        val length = readInt()
        require(length in 0..limit && length <= available())
        return ByteArray(length).also { readFully(it) }
    }

    private fun DataOutputStream.writeCiphertext(value: VaultCiphertext) {
        writeBytesWithSize(value.iv)
        writeBytesWithSize(value.bytes)
    }

    private fun DataInputStream.readCiphertext(): VaultCiphertext {
        val iv = readBytesWithSize(12)
        require(iv.size == 12)
        val ciphertext = readBytesWithSize()
        require(ciphertext.size >= 16)
        return VaultCiphertext(iv, ciphertext)
    }

    private fun DataOutputStream.writeKeyEnvelope(value: VaultKeyEnvelope) {
        writeUTF(value.alias)
        writeInt(value.mode.ordinal)
        writeCiphertext(value.ciphertext)
    }

    private fun DataInputStream.readKeyEnvelope(): VaultKeyEnvelope {
        val alias = readUTF()
        require(alias.startsWith("scaniqo.password."))
        val mode = VaultAuthenticationMode.entries.getOrNull(readInt())
        requireNotNull(mode)
        return VaultKeyEnvelope(alias, mode, readCiphertext())
    }
}
