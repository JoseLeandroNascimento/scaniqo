package com.joseleandro.scaniqo.ui.documentCommon.effect

/** Efeitos de consumo único, executados pelo ponto central de integração com o Android. */
sealed interface DocumentActionEffect {
    data object LaunchScanner : DocumentActionEffect
    data class ChooseDownloadLocation(val fileName: String) : DocumentActionEffect
    data class SharePdf(val uri: String, val name: String) : DocumentActionEffect
}
