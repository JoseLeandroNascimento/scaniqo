package com.joseleandro.scaniqo.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.R as AppR
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdaptiveNavigationFrameInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun busyDrawerDisablesActionsAndPreservesPremiumSelection() {
        val enabled = mutableStateOf(false)
        val navigations = mutableListOf<AppRoute>()
        var imports = 0
        var documentLabel = ""
        var importLabel = ""
        var settingsLabel = ""
        var premiumLabel = ""
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp))) {
                ScaniqoTheme {
                    documentLabel = stringResource(CoreResourcesR.string.documents_title)
                    importLabel = stringResource(AppR.string.menu_import_pdf)
                    settingsLabel = stringResource(CoreResourcesR.string.settings_title)
                    premiumLabel = stringResource(CoreResourcesR.string.premium_access_title)
                    AppNavigationDrawer(
                        currentRoute = AppRoute.Premium,
                        onClose = { navigations += AppRoute.Documents },
                        onImportPdf = { imports++ },
                        onNavigate = navigations::add,
                        navigationEnabled = enabled.value,
                    )
                }
            }
        }
        compose.waitForIdle()
        listOf(documentLabel, importLabel, settingsLabel).forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsNotEnabled()
                .assertHasNoClickAction().performTouchInput { click() }
        }
        compose.onNodeWithText(premiumLabel).assertIsNotEnabled().assertIsSelected()
            .performTouchInput { click() }
        compose.runOnIdle {
            assertTrue(navigations.isEmpty())
            assertEquals(0, imports)
            enabled.value = true
        }
        compose.onNodeWithText(importLabel).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(settingsLabel).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(premiumLabel).assertIsEnabled().assertIsSelected().performClick()
        compose.runOnIdle {
            assertEquals(1, imports)
            assertEquals(listOf(AppRoute.Settings, AppRoute.Premium), navigations)
        }
    }

    @Test
    fun openingDocumentFromPersistentNavigationKeepsModalDrawerClosed() {
        val size = mutableStateOf(DpSize(1280.dp, 800.dp))
        val route = mutableStateOf<AppRoute>(AppRoute.Documents)
        lateinit var drawerState: DrawerState
        var persistentNavigationVisible = false
        var readerClicks = 0

        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)) {
                ScaniqoTheme {
                    drawerState = rememberDrawerState(DrawerValue.Closed)
                    AdaptiveNavigationFrame(
                        currentRoute = route.value,
                        drawerState = drawerState,
                        navigationEnabled = true,
                        onNavigate = { route.value = it },
                        onImportPdf = {},
                        drawerContent = { permanent ->
                            if (permanent) {
                                PermanentDrawerSheet(
                                    modifier = Modifier.width(280.dp).fillMaxHeight()
                                        .testTag("permanent_drawer"),
                                ) {
                                    Text("Documents")
                                }
                            } else {
                                ModalDrawerSheet(
                                    modifier = Modifier.width(280.dp).fillMaxHeight()
                                        .testTag("modal_drawer"),
                                ) {
                                    Text("Menu")
                                }
                            }
                        },
                    ) { hasPersistentNavigation ->
                        persistentNavigationVisible = hasPersistentNavigation
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (route.value == AppRoute.Documents) {
                                Button(
                                    onClick = { route.value = AppRoute.DocumentReader(1L) },
                                    modifier = Modifier.testTag("open_document"),
                                ) {
                                    Text("Open document")
                                }
                            } else {
                                Button(
                                    onClick = { readerClicks++ },
                                    modifier = Modifier.testTag("reader_action"),
                                ) {
                                    Text("Read document")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Exercise both the permanent drawer and rail before entering an immersive route.
        listOf(1280.dp, 800.dp).forEachIndexed { index, width ->
            compose.runOnIdle {
                route.value = AppRoute.Documents
                size.value = DpSize(width, 800.dp)
            }
            compose.runOnIdle {
                assertTrue(persistentNavigationVisible)
                assertEquals(DrawerValue.Closed, drawerState.currentValue)
            }
            compose.onNodeWithTag("open_document").assertIsDisplayed()
                .performTouchInput { click() }

            compose.runOnIdle {
                assertFalse(persistentNavigationVisible)
                assertEquals(DrawerValue.Closed, drawerState.currentValue)
                assertEquals(DrawerValue.Closed, drawerState.targetValue)
            }
            compose.onNodeWithTag("modal_drawer").assertIsNotDisplayed()

            // A semantic click bypasses overlays; a real touch also detects a stray drawer scrim.
            compose.onNodeWithTag("reader_action").assertIsDisplayed()
                .performTouchInput { click() }
            compose.runOnIdle {
                assertEquals(index + 1, readerClicks)
            }
        }
    }
}
