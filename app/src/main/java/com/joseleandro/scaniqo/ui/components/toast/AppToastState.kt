package com.joseleandro.scaniqo.ui.components.toast

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.joseleandro.scaniqo.R

enum class AppToastType {
    SUCCESS,
    ERROR,
    INFO,
}

enum class AppToastDuration {
    SHORT,
    LONG,
}

/**
 * Controla avisos de uma tela sem guardar referências à Activity.
 *
 * A exibição suspende até o aviso fechar. Chamadas simultâneas entram em fila;
 * cancelar a coroutine também remove o aviso, inclusive se estiver aguardando.
 */
@Stable
class AppToastState(private val dismissActionLabel: String) {
    internal val hostState = SnackbarHostState()

    suspend fun show(
        message: String,
        type: AppToastType = AppToastType.INFO,
        duration: AppToastDuration = if (type == AppToastType.ERROR) {
            AppToastDuration.LONG
        } else {
            AppToastDuration.SHORT
        },
    ) {
        require(message.isNotBlank()) { "O aviso precisa ter uma mensagem." }
        hostState.showSnackbar(
            AppToastVisuals(
                message = message,
                type = type,
                duration = when (duration) {
                    AppToastDuration.SHORT -> SnackbarDuration.Short
                    AppToastDuration.LONG -> SnackbarDuration.Long
                },
                actionLabel = dismissActionLabel,
            ),
        )
    }

    /** Fecha somente o aviso visível, permitindo que a fila continue. */
    fun dismiss() {
        hostState.currentSnackbarData?.dismiss()
    }
}

/** Use o mesmo estado na chamada show e no AppToastHost do Scaffold. */
@Composable
fun rememberAppToastState(): AppToastState {
    val dismissActionLabel = stringResource(R.string.toast_dismiss)
    return remember(dismissActionLabel) { AppToastState(dismissActionLabel) }
}

internal data class AppToastVisuals(
    override val message: String,
    val type: AppToastType,
    override val duration: SnackbarDuration,
    // O rótulo informa ao host que há um controle e ajusta o tempo para acessibilidade.
    override val actionLabel: String,
    override val withDismissAction: Boolean = true,
) : SnackbarVisuals
