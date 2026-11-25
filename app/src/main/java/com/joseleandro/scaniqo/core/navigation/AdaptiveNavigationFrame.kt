package com.joseleandro.scaniqo.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

internal fun showNavigationRail(widthDp: Int, route: AppRoute?): Boolean =
    widthDp >= 600 && route in listOf(
        AppRoute.Documents, AppRoute.Tools, AppRoute.Tags, AppRoute.Signatures,
        AppRoute.Trash, AppRoute.Settings, AppRoute.Personalization,
        AppRoute.PdfPreferences, AppRoute.DriveBackup, AppRoute.PasswordSettings,
        AppRoute.LanguageSettings,
    )

@Composable
internal fun AdaptiveNavigationFrame(
    currentRoute: AppRoute?,
    drawerState: DrawerState,
    navigationEnabled: Boolean,
    onNavigate: (AppRoute) -> Unit,
    onImportPdf: () -> Unit,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val showRail = showNavigationRail(maxWidth.value.toInt(), currentRoute)
        LaunchedEffect(showRail) {
            if (showRail) drawerState.close()
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !showRail && currentRoute == AppRoute.Documents && navigationEnabled,
            drawerContent = { drawerContent() },
        ) {
            Row(Modifier.fillMaxSize()) {
                if (showRail) {
                    AppNavigationRail(currentRoute, navigationEnabled, onNavigate, onImportPdf)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
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
    )
    val settingsSelected = currentRoute in listOf(
        AppRoute.Settings, AppRoute.Personalization, AppRoute.PdfPreferences,
        AppRoute.DriveBackup, AppRoute.PasswordSettings, AppRoute.LanguageSettings,
    )
    NavigationRail(
        modifier = modifier.width(104.dp).fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            IconButton(
                onClick = onImportPdf,
                enabled = enabled,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
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
                    icon = { Icon(painterResource(iconRes), null, Modifier.size(24.dp)) },
                    label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
