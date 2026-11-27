package com.joseleandro.scaniqo.core.navigation

import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** Menu visual independente da pilha de navegação e das operações com documentos. */
@Composable
internal fun AppNavigationDrawer(
    currentRoute: AppRoute?,
    onClose: () -> Unit,
    onImportPdf: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    permanent: Boolean = false,
    navigationEnabled: Boolean = true,
) {
    val drawerItems: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))) {
                    Image(
                        painterResource(R.drawable.ic_launcher_background),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                    )
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        // Desenha o vetor na dimensão final, sem ampliar uma camada rasterizada.
                        modifier = Modifier.requiredSize(52.dp * 1.5f).align(Alignment.Center),
                    )
                }
                Text(
                    stringResource(R.string.app_name),
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            DrawerDestination(
                enabled = navigationEnabled,
                label = stringResource(CoreResourcesR.string.documents_title),
                icon = CoreResourcesR.drawable.ic_tool_pages,
                selected = currentRoute == AppRoute.Documents,
                onClick = onClose,
            )
            DrawerDestination(
                enabled = navigationEnabled,
                label = stringResource(R.string.menu_import_pdf),
                icon = CoreResourcesR.drawable.ic_symbol_file_open,
                onClick = onImportPdf,
            )
            DrawerDestination(
                enabled = navigationEnabled,
                label = stringResource(CoreResourcesR.string.tools_hub_title),
                icon = CoreResourcesR.drawable.ic_tag_tools,
                selected = currentRoute == AppRoute.Tools,
                onClick = { onNavigate(AppRoute.Tools) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
            )
            DrawerDestination(
                enabled = navigationEnabled,
                label = stringResource(R.string.menu_tags),
                icon = CoreResourcesR.drawable.ic_tag_label,
                selected = currentRoute == AppRoute.Tags,
                onClick = { onNavigate(AppRoute.Tags) },
            )
            DrawerDestination(
                enabled = navigationEnabled,
                label = stringResource(R.string.menu_signatures),
                icon = CoreResourcesR.drawable.ic_symbol_draw,
                selected = currentRoute == AppRoute.Signatures,
                onClick = { onNavigate(AppRoute.Signatures) },
            )
            DrawerDestination(
                enabled = navigationEnabled,
                label = stringResource(CoreResourcesR.string.trash_title),
                icon = CoreResourcesR.drawable.delete_24px,
                selected = currentRoute == AppRoute.Trash,
                onClick = { onNavigate(AppRoute.Trash) },
            )
            DrawerDestination(
                enabled = navigationEnabled,
                label = stringResource(CoreResourcesR.string.settings_title),
                icon = R.drawable.ic_settings,
                selected = currentRoute in setOf(AppRoute.Settings, AppRoute.Personalization,
                    AppRoute.PdfPreferences, AppRoute.DriveBackup, AppRoute.PasswordSettings,
                    AppRoute.LanguageSettings),
                onClick = { onNavigate(AppRoute.Settings) },
            )
        }
        DrawerPremiumCard(
            onClick = { onNavigate(AppRoute.Premium) },
            modifier = Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            selected = currentRoute == AppRoute.Premium,
            enabled = navigationEnabled,
        )
    }
    if (permanent) {
        PermanentDrawerSheet(
            modifier = modifier.width(280.dp).fillMaxHeight(),
            drawerContainerColor = MaterialTheme.colorScheme.surface,
            drawerTonalElevation = 0.dp,
            content = drawerItems,
        )
    } else {
        ModalDrawerSheet(
            modifier = modifier.widthIn(max = 320.dp).fillMaxHeight(),
            drawerContainerColor = MaterialTheme.colorScheme.surface,
            drawerTonalElevation = 0.dp,
            content = drawerItems,
        )
    }
}

@Composable
private fun DrawerPremiumCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { role = Role.Tab },
        shape = RoundedCornerShape(24.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer.copy(alpha = if (enabled) 1f else .38f),
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) colors.primary else colors.onPrimaryContainer.copy(alpha = .08f)),
    ) {
        Row(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .background(colors.onPrimaryContainer.copy(alpha = .07f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(CoreResourcesR.drawable.ic_tag_star),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(CoreResourcesR.string.premium_access_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.menu_premium_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = if (enabled) 1f else .38f),
                )
            }
            Icon(
                painterResource(CoreResourcesR.drawable.arrow_back_24px),
                contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(180f),
            )
        }
    }
}

@Composable
private fun DrawerDestination(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val disabledColor = colors.onSurface.copy(alpha = .38f)
    val labelContent: @Composable () -> Unit = {
        Text(
            label,
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (!enabled) disabledColor else if (selected) colors.primary else colors.onSurface,
        )
    }
    val iconContent: @Composable () -> Unit = {
        Icon(
            painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp),
            tint = if (!enabled) disabledColor else if (selected) colors.primary else colors.onSurfaceVariant,
        )
    }
    val itemModifier = modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    if (enabled) {
        NavigationDrawerItem(
            label = labelContent,
            icon = iconContent,
            selected = selected,
            onClick = onClick,
            modifier = itemModifier,
            shape = RoundedCornerShape(28.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = colors.primary.copy(alpha = .10f),
                selectedIconColor = colors.primary,
                selectedTextColor = colors.primary,
                unselectedContainerColor = Color.Transparent,
                unselectedIconColor = colors.onSurfaceVariant,
                unselectedTextColor = colors.onSurface,
            ),
        )
    } else {
        Surface(
            modifier = itemModifier.semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                disabled()
            }.heightIn(min = 56.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = if (selected) colors.primary.copy(alpha = .10f) else Color.Transparent,
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                iconContent()
                Box(Modifier.weight(1f)) { labelContent() }
            }
        }
    }
}

@Preview(name = "Menu claro", widthDp = 320, heightDp = 700, locale = "pt-rBR")
@Preview(name = "Menu escuro", widthDp = 320, heightDp = 700, locale = "pt-rBR", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Menu fonte ampliada", widthDp = 320, heightDp = 700, fontScale = 1.5f, locale = "pt-rBR")
@Preview(name = "Menu compacto", widthDp = 320, heightDp = 360, locale = "pt-rBR")
@Preview(name = "Menu árabe fonte ampliada", widthDp = 320, heightDp = 700, fontScale = 1.5f, locale = "ar")
@Composable
private fun AppNavigationDrawerPreview() {
    ScaniqoTheme {
        AppNavigationDrawer(
            currentRoute = AppRoute.Documents,
            onClose = {},
            onImportPdf = {},
            onNavigate = {},
        )
    }
}

@Preview(name = "Menu indisponível", widthDp = 320, heightDp = 700, locale = "pt-rBR")
@Composable
private fun AppNavigationDrawerDisabledPreview() {
    ScaniqoTheme {
        AppNavigationDrawer(
            currentRoute = AppRoute.Premium,
            onClose = {},
            onImportPdf = {},
            onNavigate = {},
            navigationEnabled = false,
        )
    }
}
