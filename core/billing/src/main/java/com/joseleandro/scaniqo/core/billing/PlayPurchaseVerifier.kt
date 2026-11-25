package com.joseleandro.scaniqo.core.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal data class SignedPlayReceipt(val json: String, val signature: String)

internal data class VerifiedPlayPurchase(
    val receipt: SignedPlayReceipt,
    val token: String,
    val purchaseTimeMillis: Long,
)

/** Verifies the original bytes before trusting any purchase fields. No persisted premium flag. */
internal class PlayPurchaseVerifier(publicKey: String, private val packageName: String) {
    private val key: RSAPublicKey? = try {
        val encoded = publicKey.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "").filterNot(Char::isWhitespace)
        (KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.decode(encoded)),
        ) as? RSAPublicKey)?.takeIf { it.modulus.bitLength() >= 2048 }
    } catch (_: Exception) {
        null
    }

    val isConfigured: Boolean get() = key != null

    fun verify(receipt: SignedPlayReceipt): VerifiedPlayPurchase? {
        val publicKey = key ?: return null
        if (receipt.json.length !in 1..MAX_RECEIPT_LENGTH ||
            receipt.signature.length !in 1..MAX_SIGNATURE_LENGTH) return null
        return try {
            val verifier = Signature.getInstance("SHA1withRSA")
            verifier.initVerify(publicKey)
            verifier.update(receipt.json.toByteArray(Charsets.UTF_8))
            if (!verifier.verify(Base64.decode(receipt.signature))) return null
            val payload = Json.parseToJsonElement(receipt.json) as? JsonObject ?: return null
            if (payload.string("packageName") != packageName) return null
            // Signed Play JSON uses 0 for PURCHASED; SDK PurchaseState.PURCHASED is 1.
            if ((payload["purchaseState"] as? JsonPrimitive)?.intOrNull != 0) return null
            val products = (payload["productIds"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            } ?: listOfNotNull(payload.string("productId"))
            if (PREMIUM_PRODUCT_ID !in products) return null
            val token = payload.string("purchaseToken") ?: payload.string("token") ?: return null
            if (token.isBlank() || token.length > 4096) return null
            val purchaseTime = (payload["purchaseTime"] as? JsonPrimitive)?.longOrNull ?: return null
            if (purchaseTime <= 0L) return null
            VerifiedPlayPurchase(receipt, token, purchaseTime)
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull

    companion object {
        const val PREMIUM_PRODUCT_ID = "scaniqo_premium"
        const val MAX_RECEIPT_LENGTH = 64 * 1024
        const val MAX_SIGNATURE_LENGTH = 4096
    }
}
