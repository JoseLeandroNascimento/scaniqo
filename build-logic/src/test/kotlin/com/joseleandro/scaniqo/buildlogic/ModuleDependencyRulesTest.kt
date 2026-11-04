package com.joseleandro.scaniqo.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ModuleDependencyRulesTest {
    @Test fun generatedSelfDependenciesDoNotHideRealBoundaryViolations() {
        for (source in listOf(":feature:editor", ":feature:backup")) {
            assertTrue(ModuleDependencyRules.violations(source, listOf(source, source)).isEmpty())
            assertEquals(
                listOf("$source -> :data:documents"),
                ModuleDependencyRules.violations(source, listOf(source, ":data:documents", source)),
            )
        }
    }

    @Test fun testingHelpersCannotLeakIntoProductionIncludingTheApplication() {
        for (source in listOf(":app", ":feature:editor", ":core:common", ":data:documents")) {
            for (configuration in listOf(
                "implementation", "api", "runtimeOnly", "compileOnly", "debugImplementation",
                "releaseRuntimeClasspath", "latestImplementation",
            )) {
                assertEquals(
                    "$source / $configuration",
                    listOf("$source -> :core:testing"),
                    ModuleDependencyRules.violations(source, listOf(":core:testing"), configuration),
                )
            }
        }
    }

    @Test fun testingHelpersRemainAvailableForUnitInstrumentedAndFixtureConfigurations() {
        for (configuration in listOf(
            "testImplementation", "testRuntimeOnly", "testRuntimeClasspath", "testDebugImplementation",
            "debugUnitTestCompileClasspath", "androidTestImplementation", "androidTestRuntimeOnly",
            "debugAndroidTestRuntimeClasspath", "testFixturesApi", "debugTestFixturesImplementation",
            "integrationTestImplementation",
        )) {
            assertTrue(
                configuration,
                ModuleDependencyRules.violations(":app", listOf(":core:testing"), configuration).isEmpty(),
            )
            assertTrue(
                configuration,
                ModuleDependencyRules.violations(":feature:backup", listOf(":core:testing"), configuration).isEmpty(),
            )
        }
    }

    @Test fun testingModuleGeneratedSelfDependencyIsStillAllowed() {
        assertTrue(
            ModuleDependencyRules.violations(":core:testing", listOf(":core:testing"), "api").isEmpty(),
        )
    }

    @Test fun testConfigurationDoesNotBypassOtherLayerBoundaries() {
        assertEquals(
            listOf(":feature:editor -> :data:documents"),
            ModuleDependencyRules.violations(
                ":feature:editor", listOf(":core:testing", ":data:documents"), "testImplementation",
            ),
        )
    }

    @Test fun applicationCanComposeEveryLayer() {
        assertTrue(ModuleDependencyRules.violations(
            ":app", listOf(":core:domain", ":data:documents", ":feature:editor"),
        ).isEmpty())
    }

    @Test fun librariesCannotReachApplicationAndCoreCannotReachImplementations() {
        assertEquals(listOf(":core:domain -> :app", ":core:domain -> :data:documents", ":core:domain -> :feature:editor"),
            ModuleDependencyRules.violations(
                ":core:domain", listOf(":feature:editor", ":app", ":data:documents", ":app"),
            ),
        )
    }

    @Test fun featuresUseContractsInsteadOfDataImplementations() {
        assertEquals(
            listOf(":feature:editor -> :data:documents", ":feature:editor -> :feature:settings"),
            ModuleDependencyRules.violations(
                ":feature:editor", listOf(":core:domain", ":data:documents", ":feature:settings"),
            ),
        )
    }

    @Test fun editorCanReuseSignatureAcquisitionButReverseDependencyIsForbidden() {
        assertTrue(ModuleDependencyRules.violations(":feature:editor", listOf(":feature:signatures")).isEmpty())
        assertEquals(
            listOf(":feature:signatures -> :feature:editor"),
            ModuleDependencyRules.violations(":feature:signatures", listOf(":feature:editor")),
        )
    }

    @Test fun dataCanDependOnCoreAndOtherDataButCannotReachFeatures() {
        assertEquals(
            listOf(":data:documents -> :feature:editor"),
            ModuleDependencyRules.violations(
                ":data:documents", listOf(":core:domain", ":data:storage", ":feature:editor"),
            ),
        )
    }
}
