package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.ReceiptDraft
import java.text.Normalizer
import java.util.Locale

/** Extração conservadora: campos sem evidência ficam vazios, nunca são calculados ou inventados. */
internal object ReceiptTextParser {
    private val amount = Regex("""(?:R\$\s*)?\d[\d.]*,\d{2}\b""")
    private val date = Regex("""\b\d{2}[/.-]\d{2}[/.-]\d{4}\b""")

    fun parse(text: String): ReceiptDraft {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val merchant = lines.firstOrNull { line ->
            line.count(Char::isLetter) >= 3 && !amount.containsMatchIn(line) && !normalize(line).let {
                it.startsWith("CNPJ") || it.startsWith("CPF") || it.startsWith("DANFE") ||
                    it.startsWith("CUPOM") || it.startsWith("DOCUMENTO AUXILIAR") ||
                    it.startsWith("NOTA FISCAL") || it.startsWith("CONSUMIDOR") ||
                    it.startsWith("TOTAL") || it.startsWith("PAGAMENTO") || it.startsWith("TROCO")
            }
        }.orEmpty()
        val totalLine = lines.lastOrNull { line ->
            val normalized = normalize(line)
            Regex("""\bTOTAL\b""").containsMatchIn(normalized) &&
                !Regex("""SUB\s*TOTAL|TOTAL\s+(?:DE\s+)?ITENS|TOTAL\s+TRIBUT""").containsMatchIn(normalized) &&
                amount.containsMatchIn(line)
        }
        val items = lines.filter { line ->
            amount.containsMatchIn(line) && line.any(Char::isLetter) && !normalize(line).let {
                Regex("""TOTAL|TROCO|DESCONTO|DINHEIRO|CARTAO|PAGAMENTO|TRIBUTO|CNPJ|CPF""").containsMatchIn(it)
            }
        }
        return ReceiptDraft(
            merchant = merchant,
            date = date.find(text)?.value.orEmpty(),
            total = totalLine?.let { amount.findAll(it).lastOrNull()?.value?.removePrefix("R$")?.trim() }.orEmpty(),
            items = items.joinToString("\n"),
            sourceText = text,
        )
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
}

internal object ReceiptCsv {
    fun encode(receipt: ReceiptDraft): String {
        val lines = receipt.items.lineSequence().filter(String::isNotBlank).toList().ifEmpty { listOf("") }
        return "\uFEFF" + (listOf(listOf("Estabelecimento", "Data", "Total", "Item")) +
            lines.map { listOf(receipt.merchant, receipt.date, receipt.total, it) })
            .joinToString("\r\n") { row -> row.joinToString(";") { cell(it) } } + "\r\n"
    }

    /** Aspas protegem delimitadores; apóstrofo neutraliza fórmulas ao abrir em planilhas. */
    private fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
            value.startsWith('\t') || value.startsWith('\r') || value.startsWith('\n')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
