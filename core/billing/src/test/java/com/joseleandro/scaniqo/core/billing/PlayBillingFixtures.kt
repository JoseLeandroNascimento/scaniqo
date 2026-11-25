package com.joseleandro.scaniqo.core.billing

import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object PlayBillingFixtures {
    const val PACKAGE = "com.joseleandro.scaniqo"
    const val NOW = 1_800_000_000_000L
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val publicKey: String = Base64.encode(keys.public.encoded)

    fun receipt(
        product: String = "scaniqo_premium",
        packageName: String = PACKAGE,
        rawState: Int = 0,
        token: String = "fixture-purchase-token",
        purchaseTime: Long = NOW - 1_000,
    ): SignedPlayReceipt = sign(buildJsonObject {
        put("orderId", "GPA.FIXTURE")
        put("packageName", packageName)
        put("productId", product)
        put("purchaseTime", purchaseTime)
        put("purchaseState", rawState)
        put("purchaseToken", token)
        put("acknowledged", false)
    }.toString())

    fun sign(json: String): SignedPlayReceipt {
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(keys.private)
        signer.update(json.toByteArray(Charsets.UTF_8))
        return SignedPlayReceipt(json, Base64.encode(signer.sign()))
    }

    fun purchase(
        receipt: SignedPlayReceipt = receipt(),
        state: StorePurchaseState = StorePurchaseState.COMPLETED,
        token: String = "fixture-purchase-token",
        products: List<String> = listOf("scaniqo_premium"),
        acknowledged: Boolean = false,
    ) = StorePurchase(receipt, token, products, state, acknowledged)
}
