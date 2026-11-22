package com.joseleandro.scaniqo.ui.components.toast

import com.joseleandro.scaniqo.core.designsystem.R
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme

/**
 * Use o estado de [rememberAppToastState] em
 * `Scaffold(snackbarHost = { AppToastHost(state) })`.
 * Em uma coroutine, chame `state.show("Tag salva", AppToastType.SUCCESS)`.
 *
 * O Scaffold posiciona as mensagens acima da FAB e respeita os insets da tela.
 * SnackbarHost mantém a fila, animações, tempo acessível e anúncio da mensagem.
 */
@Composable
fun AppToastHost(
    state: AppToastState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = state.hostState,
        modifier = modifier,
    ) { data ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppToast(
                message = data.visuals.message,
                type = (data.visuals as? AppToastVisuals)?.type ?: AppToastType.INFO,
                onDismiss = data::dismiss,
            )
        }
    }
}

/** Aparência da mensagem; o estado e o tempo de exibição pertencem ao host. */
@Composable
fun AppToast(
    message: String,
    type: AppToastType,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = when (type) {
        AppToastType.SUCCESS -> colors.secondary
        AppToastType.ERROR -> colors.error
        AppToastType.INFO -> colors.primary
    }
    val onAccent = when (type) {
        AppToastType.SUCCESS -> colors.onSecondary
        AppToastType.ERROR -> colors.onError
        AppToastType.INFO -> colors.onPrimary
    }
    val container = when (type) {
        AppToastType.SUCCESS -> colors.secondaryContainer
        AppToastType.ERROR -> colors.errorContainer
        AppToastType.INFO -> colors.primaryContainer
    }
    val onContainer = when (type) {
        AppToastType.SUCCESS -> colors.onSecondaryContainer
        AppToastType.ERROR -> colors.onErrorContainer
        AppToastType.INFO -> colors.onPrimaryContainer
    }
    val title = stringResource(
        when (type) {
            AppToastType.SUCCESS -> R.string.toast_success_title
            AppToastType.ERROR -> R.string.toast_error_title
            AppToastType.INFO -> R.string.toast_info_title
        },
    )
    val iconRes = when (type) {
        AppToastType.SUCCESS -> CoreResourcesR.drawable.ic_check_24
        AppToastType.ERROR -> CoreResourcesR.drawable.ic_toast_error
        AppToastType.INFO -> CoreResourcesR.drawable.ic_toast_info
    }
    Surface(
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = onContainer,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(40.dp)
                    .background(accent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(CoreResourcesR.drawable.ic_toast_close),
                    contentDescription = stringResource(R.string.toast_dismiss),
                    tint = onContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Preview(name = "Toast claro", showBackground = true, widthDp = 420, locale = "pt-rBR")
@Preview(
    name = "Toast escuro",
    showBackground = true,
    widthDp = 420,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    locale = "pt-rBR",
)
@Preview(
    name = "Toast fonte grande",
    showBackground = true,
    widthDp = 360,
    fontScale = 1.6f,
    locale = "pt-rBR",
)
@Composable
private fun AppToastPreview() {
    ScaniqoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppToast(
                    message = "A tag Comprovantes foi salva.",
                    type = AppToastType.SUCCESS,
                    onDismiss = {},
                )
                AppToast(
                    message = "Não foi possível salvar. Suas escolhas foram mantidas para tentar novamente.",
                    type = AppToastType.ERROR,
                    onDismiss = {},
                )
                AppToast(
                    message = "Use cores e ícones para reconhecer suas tags.",
                    type = AppToastType.INFO,
                    onDismiss = {},
                )
            }
        }
    }
}
