package com.joseleandro.scaniqo.core.billing

import java.security.KeyPairGenerator
import kotlin.io.encoding.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPurchaseVerifierTest {
    private val verifier = PlayPurchaseVerifier(PlayBillingFixtures.publicKey, PlayBillingFixtures.PACKAGE)

    @Test fun completedSignedPremiumReceiptIsAccepted() {
        assertTrue(verifier.isConfigured)
        assertNotNull(verifier.verify(PlayBillingFixtures.receipt()))
    }

    @Test fun tamperedPayloadAndSignatureAreRejected() {
        val receipt = PlayBillingFixtures.receipt()
        assertNull(verifier.verify(receipt.copy(json = receipt.json.replace("GPA.FIXTURE", "GPA.CHANGED"))))
        assertNull(verifier.verify(receipt.copy(signature = Base64.encode(ByteArray(256)))))
        assertNull(verifier.verify(receipt.copy(signature = "not-a-signature")))
    }

    @Test fun unrelatedProductsPackagesAndPendingStatesNeverGrantPremium() {
        assertNull(verifier.verify(PlayBillingFixtures.receipt(product = "another_product")))
        assertNull(verifier.verify(PlayBillingFixtures.receipt(packageName = "another.package")))
        assertNull(verifier.verify(PlayBillingFixtures.receipt(rawState = 4)))
        assertNull(verifier.verify(PlayBillingFixtures.receipt(rawState = 1)))
        assertNull(verifier.verify(PlayBillingFixtures.receipt(rawState = 2)))
    }

    @Test fun emptyTokenMissingFieldsAndMalformedSignedJsonAreRejected() {
        assertNull(verifier.verify(PlayBillingFixtures.receipt(token = "")))
        assertNull(verifier.verify(PlayBillingFixtures.receipt(purchaseTime = 0)))
        assertNull(verifier.verify(PlayBillingFixtures.sign("{}")))
        assertNull(verifier.verify(PlayBillingFixtures.sign("not JSON")))
        assertNull(verifier.verify(PlayBillingFixtures.sign("[]")))
    }

    @Test fun missingMalformedOrDifferentPublicKeyFailsClosed() {
        for (key in listOf("", "missing", "-----BEGIN PUBLIC KEY-----invalid")) {
            val configured = PlayPurchaseVerifier(key, PlayBillingFixtures.PACKAGE)
            assertFalse(configured.isConfigured)
            assertNull(configured.verify(PlayBillingFixtures.receipt()))
        }
        val otherKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertNull(PlayPurchaseVerifier(Base64.encode(otherKey.public.encoded), PlayBillingFixtures.PACKAGE)
            .verify(PlayBillingFixtures.receipt()))
    }

    @Test fun pemFormattedPublicKeyIsAcceptedWithoutChangingSignedPayload() {
        val pem = "-----BEGIN PUBLIC KEY-----\n" + PlayBillingFixtures.publicKey.chunked(64).joinToString("\n") +
            "\n-----END PUBLIC KEY-----"
        assertNotNull(PlayPurchaseVerifier(pem, PlayBillingFixtures.PACKAGE).verify(PlayBillingFixtures.receipt()))
    }

    @Test fun multiProductSignedReceiptRequiresPremiumProduct() {
        val receipt = PlayBillingFixtures.receipt()
        val multiple = receipt.json.replace(
            "\"productId\":\"scaniqo_premium\"",
            "\"productIds\":[\"scaniqo_premium\",\"another_product\"]",
        )
        assertNotNull(verifier.verify(PlayBillingFixtures.sign(multiple)))
    }

    @Test fun offlineGraceIsBoundedAndClockRollbackFailsClosed() {
        val cached = CachedPlayReceipt(PlayBillingFixtures.receipt(), PlayBillingFixtures.NOW)
        assertTrue(cached.isWithinGrace(PlayBillingFixtures.NOW))
        assertTrue(cached.isWithinGrace(PlayBillingFixtures.NOW + CachedPlayReceipt.OFFLINE_GRACE_MILLIS))
        assertFalse(cached.isWithinGrace(PlayBillingFixtures.NOW + CachedPlayReceipt.OFFLINE_GRACE_MILLIS + 1))
        assertFalse(cached.isWithinGrace(PlayBillingFixtures.NOW - 1))
    }
}
