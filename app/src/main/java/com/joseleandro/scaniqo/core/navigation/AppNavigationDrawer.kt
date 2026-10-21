package com.joseleandro.scaniqo.core.navigation

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/** Menu visual independente da pilha de navegação e das operações com documentos. */
@Composable
internal fun AppNavigationDrawer(
    currentRoute: AppRoute?,
    onClose: () -> Unit,
    onImportPdf: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 320.dp).fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerTonalElevation = 0.dp,
    ) {
        // A lista inteira rola para acomodar fontes ampliadas e janelas menores.
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
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
                label = stringResource(R.string.documents_title),
                icon = R.drawable.ic_tool_pages,
                selected = currentRoute == AppRoute.Documents,
                onClick = onClose,
            )
            DrawerDestination(
                label = stringResource(R.string.menu_import_pdf),
                icon = R.drawable.ic_document_import,
                onClick = onImportPdf,
            )
            DrawerDestination(
                label = stringResource(R.string.tools_hub_title),
                icon = R.drawable.ic_tag_tools,
                selected = currentRoute == AppRoute.Tools,
                onClick = { onNavigate(AppRoute.Tools) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
            )
            DrawerDestination(
                label = stringResource(R.string.menu_tags),
                icon = R.drawable.ic_tag_label,
                selected = currentRoute == AppRoute.Tags,
                onClick = { onNavigate(AppRoute.Tags) },
            )
            DrawerDestination(
                label = stringResource(R.string.menu_signatures),
                icon = R.drawable.ic_tool_signature,
                selected = currentRoute == AppRoute.Signatures,
                onClick = { onNavigate(AppRoute.Signatures) },
            )
            DrawerDestination(
                label = stringResource(R.string.trash_title),
                icon = R.drawable.delete_24px,
                selected = currentRoute == AppRoute.Trash,
                onClick = { onNavigate(AppRoute.Trash) },
            )
            DrawerDestination(
                label = stringResource(R.string.settings_title),
                icon = R.drawable.ic_settings,
                selected = currentRoute in setOf(AppRoute.Settings, AppRoute.Personalization,
                    AppRoute.PdfPreferences, AppRoute.DriveBackup, AppRoute.PasswordSettings),
                onClick = { onNavigate(AppRoute.Settings) },
            )
        }
    }
}

@Composable
private fun DrawerDestination(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    NavigationDrawerItem(
        label = {
            Text(
                label,
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        },
        icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
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
