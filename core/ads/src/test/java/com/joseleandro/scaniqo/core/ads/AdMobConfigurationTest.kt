package com.joseleandro.scaniqo.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMobConfigurationTest {
    @Test
    fun noConfiguredPlacementsDisablesAds() {
        assertFalse(AdMobConfiguration(APP_ID).enabled)
    }

    @Test
    fun eachPlacementCanEnableAdsWithoutAnyOtherPlacement() {
        AdPlacement.entries.forEach { placement ->
            val unitId = PRODUCTION_UNITS.getValue(placement)
            val configuration = configuration(mapOf(placement to unitId))

            assertTrue("$placement should enable the SDK independently", configuration.enabled)
            assertEquals(unitId, configuration.unitId(placement))
            AdPlacement.entries.filterNot { it == placement }.forEach { missing ->
                assertNull("$missing should remain disabled", configuration.unitId(missing))
            }
        }
    }

    @Test
    fun placementsReturnOnlyTheirOwnProductionUnit() {
        val configuration = configuration(PRODUCTION_UNITS)

        AdPlacement.entries.forEach { placement ->
            assertEquals(PRODUCTION_UNITS.getValue(placement), configuration.unitId(placement))
        }
    }

    @Test
    fun officialTestUnitsAreAcceptedForEveryPlacement() {
        val configuration = configuration(TEST_UNITS)

        assertTrue(configuration.enabled)
        AdPlacement.entries.forEach { placement ->
            assertEquals(TEST_UNITS.getValue(placement), configuration.unitId(placement))
        }
    }

    @Test
    fun missingAndMalformedUnitsDoNotEnableAnyPlacement() {
        AdPlacement.entries.forEach { placement ->
            INVALID_UNITS.forEach { invalid ->
                val configuration = configuration(mapOf(placement to invalid))

                assertFalse("$placement must reject $invalid", configuration.enabled)
                assertNull(configuration.unitId(placement))
            }
        }
    }

    @Test
    fun validPlacementDoesNotExposeInvalidUnitFromAnotherPlacement() {
        AdPlacement.entries.forEach { validPlacement ->
            val validUnit = TEST_UNITS.getValue(validPlacement)
            AdPlacement.entries.filterNot { it == validPlacement }.forEach { invalidPlacement ->
                INVALID_UNITS.forEach { invalidUnit ->
                    val configuration = configuration(
                        mapOf(validPlacement to validUnit, invalidPlacement to invalidUnit),
                    )

                    assertTrue(configuration.enabled)
                    assertEquals(validUnit, configuration.unitId(validPlacement))
                    assertNull(
                        "$validPlacement must not enable invalid $invalidPlacement",
                        configuration.unitId(invalidPlacement),
                    )
                }
            }
        }
    }

    @Test
    fun malformedAppIdDisablesEveryOtherwiseValidPlacement() {
        listOf(
            "",
            "ca-app-pub-7067924995647630/4025082822",
            "ca-app-pub-706792499564763~4025082822",
            "ca-app-pub-7067924995647630~402508282",
            "ca-app-pub-7067924995647630~4025082822-extra",
        ).forEach { appId ->
            val configuration = configuration(PRODUCTION_UNITS).copy(appId = appId)

            assertFalse(configuration.enabled)
            AdPlacement.entries.forEach { placement ->
                assertNull(configuration.unitId(placement))
            }
        }
    }

    private fun configuration(units: Map<AdPlacement, String?>) = AdMobConfiguration(
        appId = APP_ID,
        bannerUnitId = units[AdPlacement.TOOLS_BANNER],
        editorBannerUnitId = units[AdPlacement.EDITOR_BANNER],
        documentsNativeUnitId = units[AdPlacement.DOCUMENTS_NATIVE],
        toolsRewardedUnitId = units[AdPlacement.TOOLS_REWARDED],
    )

    private companion object {
        const val APP_ID = "ca-app-pub-7067924995647630~4025082822"
        val PRODUCTION_UNITS = mapOf(
            AdPlacement.TOOLS_BANNER to "ca-app-pub-7067924995647630/9085837810",
            AdPlacement.EDITOR_BANNER to "ca-app-pub-7067924995647630/1526911992",
            AdPlacement.DOCUMENTS_NATIVE to "ca-app-pub-7067924995647630/1384149609",
            AdPlacement.TOOLS_REWARDED to "ca-app-pub-7067924995647630/3275107937",
        )
        val TEST_UNITS = mapOf(
            AdPlacement.TOOLS_BANNER to "ca-app-pub-3940256099942544/9214589741",
            AdPlacement.EDITOR_BANNER to "ca-app-pub-3940256099942544/6300978111",
            AdPlacement.DOCUMENTS_NATIVE to "ca-app-pub-3940256099942544/2247696110",
            AdPlacement.TOOLS_REWARDED to "ca-app-pub-3940256099942544/5224354917",
        )
        val INVALID_UNITS = listOf(
            null,
            "",
            " ",
            APP_ID,
            "ca-app-pub-706792499564763/1234567890",
            "ca-app-pub-7067924995647630/123456789",
            "ca-app-pub-7067924995647630/1234567890-extra",
            "ca-app-pub-7067924995647630/abcdefghij",
        )
    }
}
