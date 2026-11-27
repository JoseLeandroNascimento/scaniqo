package com.joseleandro.scaniqo.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

internal fun navigationSuiteType(
    windowSizeClass: WindowSizeClass,
    route: AppRoute?,
): NavigationSuiteType {
    val supportsNavigation = route in listOf(
        AppRoute.Documents, AppRoute.Tools, AppRoute.Tags, AppRoute.Signatures,
        AppRoute.Trash, AppRoute.Settings, AppRoute.Personalization,
        AppRoute.PdfPreferences, AppRoute.DriveBackup, AppRoute.PasswordSettings,
        AppRoute.LanguageSettings,
    )
    return when {
        !supportsNavigation ||
            !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.None
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) ->
            NavigationSuiteType.NavigationDrawer
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.None
    }
}

@Composable
internal fun AdaptiveNavigationFrame(
    currentRoute: AppRoute?,
    drawerState: DrawerState,
    navigationEnabled: Boolean,
    onNavigate: (AppRoute) -> Unit,
    onImportPdf: () -> Unit,
    drawerContent: @Composable (permanent: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (hasPersistentNavigation: Boolean) -> Unit,
) {
    val windowInfo = currentWindowAdaptiveInfoV2()
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Use the hinge-safe pane's bounds, which may be smaller than the activity window.
        val adaptiveInfo = WindowAdaptiveInfo(
            windowSizeClass = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(
                widthDp = maxWidth.value,
                heightDp = maxHeight.value,
            ),
            windowPosture = windowInfo.windowPosture,
        )
        val layoutType = navigationSuiteType(adaptiveInfo.windowSizeClass, currentRoute)
        val hasPersistentNavigation = layoutType != NavigationSuiteType.None
        LaunchedEffect(hasPersistentNavigation) {
            if (hasPersistentNavigation) drawerState.close()
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !hasPersistentNavigation && currentRoute == AppRoute.Documents && navigationEnabled,
            // Keep the closed drawer measured so its anchors survive navigation and resizing.
            drawerContent = { drawerContent(false) },
        ) {
            NavigationSuiteScaffoldLayout(
                layoutType = layoutType,
                navigationSuite = {
                    when (layoutType) {
                        NavigationSuiteType.NavigationDrawer -> drawerContent(true)
                        NavigationSuiteType.NavigationRail -> AppNavigationRail(
                            currentRoute, navigationEnabled, onNavigate, onImportPdf,
                        )
                    }
                },
            ) {
                Box(Modifier.fillMaxSize()) { content(hasPersistentNavigation) }
            }
        }
    }
}

@Composable
private fun AppNavigationRail(
    currentRoute: AppRoute?,
    enabled: Boolean,
    onNavigate: (AppRoute) -> Unit,
    onImportPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routes = listOf(
        Triple(AppRoute.Documents, CoreResourcesR.string.documents_title, CoreResourcesR.drawable.ic_tool_pages),
        Triple(AppRoute.Tools, CoreResourcesR.string.tools_hub_title, CoreResourcesR.drawable.ic_tag_tools),
        Triple(AppRoute.Tags, R.string.menu_tags, CoreResourcesR.drawable.ic_tag_label),
        Triple(AppRoute.Signatures, R.string.menu_signatures, CoreResourcesR.drawable.ic_symbol_draw),
        Triple(AppRoute.Trash, CoreResourcesR.string.trash_title, CoreResourcesR.drawable.delete_24px),
        Triple(AppRoute.Settings, CoreResourcesR.string.settings_title, R.drawable.ic_settings),
        Triple(AppRoute.Premium, CoreResourcesR.string.premium_access_title, CoreResourcesR.drawable.ic_tag_star),
    )
    val settingsSelected = currentRoute in listOf(
        AppRoute.Settings, AppRoute.Personalization, AppRoute.PdfPreferences,
        AppRoute.DriveBackup, AppRoute.PasswordSettings, AppRoute.LanguageSettings,
    )
    NavigationRail(
        modifier = modifier.width(104.dp).fillMaxHeight().testTag("navigation_rail"),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = onImportPdf,
                enabled = enabled,
                modifier = Modifier.padding(vertical = 8.dp).testTag("navigation_rail_import"),
            ) {
                Icon(
                    painterResource(CoreResourcesR.drawable.ic_symbol_file_open),
                    stringResource(R.string.menu_import_pdf),
                )
            }
            routes.forEach { (route, labelRes, iconRes) ->
                val label = stringResource(labelRes)
                NavigationRailItem(
                    selected = currentRoute == route || (route == AppRoute.Settings && settingsSelected),
                    onClick = { onNavigate(route) },
                    enabled = enabled,
                    icon = {
                        Icon(
                            painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).testTag("navigation_rail_icon"),
                        )
                    },
                    label = {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 8.dp).testTag("navigation_rail_label"),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .testTag("navigation_rail_item"),
                )
            }
        }
    }
}

@Preview(name = "Navegação celular", widthDp = 400, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Navegação tablet retrato", widthDp = 800, heightDp = 1100, locale = "pt-rBR")
@Preview(name = "Navegação tablet paisagem", widthDp = 1280, heightDp = 800, locale = "pt-rBR")
@Preview(name = "Janela baixa", widthDp = 900, heightDp = 400, locale = "pt-rBR", fontScale = 1.5f)
@Preview(name = "Menu lateral paisagem", widthDp = 960, heightDp = 600, locale = "en")
@Preview(name = "Menu lateral fonte ampliada", widthDp = 800, heightDp = 1100, locale = "pt-rBR", fontScale = 2f)
@Preview(name = "Menu lateral árabe", widthDp = 800, heightDp = 1100, locale = "ar", fontScale = 1.5f)
@Composable
private fun AdaptiveNavigationFramePreview() {
    ScaniqoTheme {
        AdaptiveNavigationFrame(
            currentRoute = AppRoute.Documents,
            drawerState = rememberDrawerState(DrawerValue.Closed),
            navigationEnabled = true,
            onNavigate = {},
            onImportPdf = {},
            drawerContent = { permanent ->
                AppNavigationDrawer(
                    currentRoute = AppRoute.Documents,
                    onClose = {},
                    onImportPdf = {},
                    onNavigate = {},
                    permanent = permanent,
                )
            },
        ) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(stringResource(CoreResourcesR.string.documents_title), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
