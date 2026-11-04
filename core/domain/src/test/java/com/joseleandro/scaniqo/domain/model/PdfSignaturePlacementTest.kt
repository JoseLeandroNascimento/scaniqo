package com.joseleandro.scaniqo.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfSignaturePlacementTest {
    @Test fun legacyPlacementsWithoutLibraryIdentityStillLoad() {
        val placement = Json.decodeFromString<PdfSignaturePlacement>(
            """{"id":"placed","imageUri":"file:///signature.png","x":0.2,"y":0.3,"rotationDegrees":37.0}"""
        )
        assertNull(placement.sourceSignatureId)
        assertEquals(37f, placement.rotationDegrees, 0f)
        assertEquals(.2f, placement.x, 0f)
    }

    @Test fun savedLibraryIdentitySurvivesSessionRoundTripWithCroppedImage() {
        val placement = PdfSignaturePlacement(
            id = "placed",
            imageUri = "file:///session/cropped.png",
            rotationDegrees = -23f,
            sourceSignatureId = "library-signature",
        )
        assertEquals(placement, Json.decodeFromString<PdfSignaturePlacement>(Json.encodeToString(placement)))
    }
}
