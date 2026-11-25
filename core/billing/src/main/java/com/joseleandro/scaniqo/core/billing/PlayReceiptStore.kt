package com.joseleandro.scaniqo.core.billing

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class CachedPlayReceipt(
    val receipt: SignedPlayReceipt,
    val verifiedAtMillis: Long,
) {
    fun isWithinGrace(nowMillis: Long): Boolean =
        verifiedAtMillis > 0 && nowMillis >= verifiedAtMillis &&
            nowMillis - verifiedAtMillis <= OFFLINE_GRACE_MILLIS

    companion object {
        const val OFFLINE_GRACE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}

internal interface PlayReceiptStore {
    suspend fun read(): CachedPlayReceipt?
    suspend fun write(receipt: CachedPlayReceipt)
    suspend fun clear()
}

/** App-private and excluded from backup. Every read is re-verified by PlayPurchaseVerifier. */
internal class FilePlayReceiptStore(context: Context) : PlayReceiptStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "premium_play_receipt.json"))

    override suspend fun read(): CachedPlayReceipt? = withContext(Dispatchers.IO) {
        try {
            if (file.baseFile.length() > MAX_CACHE_BYTES) {
                file.delete()
                return@withContext null
            }
            val data = Json.parseToJsonElement(file.openRead().use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }) as? JsonObject ?: return@withContext null
            val json = (data["receipt"] as? JsonPrimitive)?.contentOrNull ?: return@withContext null
            val signature = (data["signature"] as? JsonPrimitive)?.contentOrNull ?: return@withContext null
            val verifiedAt = (data["verifiedAt"] as? JsonPrimitive)?.longOrNull ?: return@withContext null
            CachedPlayReceipt(SignedPlayReceipt(json, signature), verifiedAt)
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            file.delete()
            null
        }
    }

    override suspend fun write(receipt: CachedPlayReceipt) = withContext(Dispatchers.IO) {
        val json = buildJsonObject {
            put("receipt", receipt.receipt.json)
            put("signature", receipt.receipt.signature)
            put("verifiedAt", receipt.verifiedAtMillis)
        }.toString()
        val output = file.startWrite()
        try {
            output.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (failure: IOException) {
            file.failWrite(output)
            throw failure
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }

    private companion object {
        const val MAX_CACHE_BYTES = 160L * 1024
    }
}
