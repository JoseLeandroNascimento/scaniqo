package com.joseleandro.scaniqo.domain.model

import java.io.IOException

/** A senha existe somente durante a tentativa de mesclagem, sem serialização em estado salvo. */
data class PdfMergeSource(val uri: String, val password: String? = null) {
    override fun toString(): String = "PdfMergeSource(uri=$uri, password=<oculta>)"
}

class PdfMergePasswordRequiredException(val uri: String) :
    IOException("Informe a senha correta do PDF selecionado.")

class PdfMergeLimitException(val reason: Reason) : IOException(
    when (reason) {
        Reason.PAGE_COUNT -> "O documento pode conter até 100 páginas."
        Reason.TOTAL_BYTES -> "As fontes do documento podem ocupar até 50 MB."
    }
) {
    enum class Reason { PAGE_COUNT, TOTAL_BYTES }
}
