package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.ReceiptDraft
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale

/** Extração conservadora: mantém datas, separadores e moedas como estão na fonte. */
internal object ReceiptTextParser {
    private const val GAP = """[ \u00A0\u202F]"""
    private const val CURRENCY_TOKEN =
        """(?:R\$|US\$|CA\$|A\$|HK\$|S\$|NT\$|Rp\.?|USD|EUR|GBP|BRL|IDR|INR|BDT|RUB|CNY|JPY|SAR|CAD|AUD|CHF|MXN|ARS|CLP|COP|PEN|\p{Sc})"""
    private const val NUMBER_TOKEN =
        """(?:\d{1,3}(?:[.,'’ \u00A0\u202F]\d{3})+|\d+)(?:[.,]\d{1,3})?"""
    private val currency = Regex(CURRENCY_TOKEN, RegexOption.IGNORE_CASE)
    private val amount = Regex(
        """(?<![\p{L}\p{N}.,])(?:$CURRENCY_TOKEN$GAP*)?[+-]?$NUMBER_TOKEN(?:$GAP*$CURRENCY_TOKEN)?(?![\p{L}\p{N}.,])""",
        RegexOption.IGNORE_CASE,
    )
    private val isoDate = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")
    private val localDate = Regex("""(?<!\d)(\d{1,2})([/.-])(\d{1,2})\2(\d{4})(?!\d)""")
    private val time = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val totalLabel = Regex(
        """\b(?:TOTAL|AMOUNT\s+(?:DUE|PAYABLE)|BALANCE\s+DUE|MONTANT\s+(?:A\s+PAYER|DU)|NET\s+A\s+PAYER|A\s+PAYER|IMPORTE|JUMLAH|TAGIHAN)\b""",
    )
    private val nonPayableTotal = Regex(
        """\b(?:SUB[\s-]*TOTAL|SOUS[\s-]*TOTAL|(?:TOTAL|JUMLAH)\s+(?:(?:DE|OF|DES)\s+)?(?:ITENS|ITEMS|ARTICLES|ARTICULOS|PRODUTOS|PRODUITS|BARANG|PRODUK|QUANTITY|QTY|UNITS|TRIBUT\w*|TAX\w*|IVA|VAT|TVA|DISCOUNT|DESCONTO|REMISE|DISKON|PAJAK|HEMAT)|TOTAL\s+(?:(?:BEFORE|EXCLUDING)\s+TAX|SIN\s+IVA|HT)|(?:TAX(?:ES)?|VAT|IVA|TVA|TRIBUT\w*|PAJAK)\s+TOTAL)\b""",
    )
    private val summary = Regex(
        """\b(?:TOTAL|SUB[\s-]*TOTAL|SOUS[\s-]*TOTAL|TROCO|DESCONTO|DINHEIRO|CARTAO|PAGAMENTO|TRIBUTO\w*|CNPJ|CPF|CASH|CHANGE|TENDER(?:ED)?|CREDIT|DEBIT|PAYMENT|PAID|TAX(?:ES)?|VAT|DISCOUNT|BALANCE|AMOUNT|ROUNDING|CAMBIO|VUELTO|EFECTIVO|TARJETA|PAGO|DESCUENTO|IVA|MONNAIE|RENDU|CARTE|ESPECES|PAIEMENT|TVA|REMISE|MONTANT|IMPORTE|NET\s+A\s+PAYER|A\s+PAYER|KEMBALI(?:AN)?|TUNAI|BAYAR|PAJAK|DISKON|HEMAT|TAGIHAN|JUMLAH)\b""",
    )
    private val documentHeader = Regex(
        """^(?:CNPJ|CPF|DANFE|CUPOM|DOCUMENTO AUXILIAR|NOTA FISCAL|CONSUMIDOR|RECEIPT|INVOICE|TAX INVOICE|SALES RECEIPT|RECIBO|FACTURA|TICKET|RECU|FACTURE|STRUK|NOTA)\b""",
    )

    fun parse(text: String): ReceiptDraft {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val merchant = lines.firstOrNull { line ->
            val normalized = normalize(line)
            line.count(Char::isLetter) >= 3 && monetaryAmounts(line).isEmpty() &&
                !documentHeader.containsMatchIn(normalized) && !summary.containsMatchIn(normalized)
        }.orEmpty()
        val total = lines.asReversed().firstNotNullOfOrNull { line ->
            val normalized = normalize(line)
            if (!totalLabel.containsMatchIn(normalized) || nonPayableTotal.containsMatchIn(normalized)) null
            else {
                val candidates = monetaryAmounts(line).ifEmpty { amounts(line) }
                // Não escolhe uma moeda entre valores de moedas diferentes na mesma linha.
                val currencies = candidates.mapNotNull { currency.find(it.value)?.value?.uppercase(Locale.ROOT) }.distinct()
                if (currencies.size > 1) null else candidates.lastOrNull()?.value?.trim()
            }
        }.orEmpty()
        val items = lines.filter { line ->
            monetaryAmounts(line).isNotEmpty() && line.any(Char::isLetter) &&
                !summary.containsMatchIn(normalize(line))
        }
        return ReceiptDraft(
            merchant = merchant,
            date = dates(text).firstOrNull()?.value.orEmpty(),
            total = total,
            items = items.joinToString("\n"),
            sourceText = text,
        )
    }

    private fun monetaryAmounts(line: String): List<MatchResult> = amounts(line).filter {
        currency.containsMatchIn(it.value) || it.value.any { character -> character in ".,'’" }
    }

    private fun amounts(line: String): List<MatchResult> {
        val temporalValues = dates(line) + time.findAll(line).toList()
        return amount.findAll(line).filter { match ->
            temporalValues.none { it.range.first <= match.range.last && match.range.first <= it.range.last } &&
                !line.substring(match.range.last + 1).trimStart().startsWith('%')
        }.toList()
    }

    private fun dates(text: String): List<MatchResult> {
        val iso = isoDate.findAll(text).filter {
            validDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        val local = localDate.findAll(text).filter {
            val first = it.groupValues[1].toInt()
            val second = it.groupValues[3].toInt()
            val year = it.groupValues[4].toInt()
            // Aceita dia/mês ou mês/dia, sem reordenar nem resolver datas ambíguas.
            validDate(year, first, second) || validDate(year, second, first)
        }
        return (iso + local).sortedBy { it.range.first }.toList()
    }

    private fun validDate(year: Int, month: Int, day: Int): Boolean {
        if (year !in 1..9999 || month !in 1..12) return false
        val maximumDay = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day in 1..maximumDay
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
}

internal object ReceiptCsv {
    data class Headers(
        val merchant: String = "Merchant",
        val date: String = "Date",
        val total: String = "Total",
        val item: String = "Item",
    )

    fun separatorFor(locale: Locale): Char =
        if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') ';' else ','

    fun encode(receipt: ReceiptDraft, headers: Headers = Headers(), separator: Char = ';'): String {
        require(separator == ';' || separator == ',')
        val lines = receipt.items.lineSequence().filter(String::isNotBlank).toList().ifEmpty { listOf("") }
        return "\uFEFF" + (listOf(listOf(headers.merchant, headers.date, headers.total, headers.item)) +
            lines.map { listOf(receipt.merchant, receipt.date, receipt.total, it) })
            .joinToString("\r\n") { row -> row.joinToString(separator.toString()) { cell(it) } } + "\r\n"
    }

    /** Aspas protegem delimitadores; apóstrofo neutraliza fórmulas ao abrir em planilhas. */
    private fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
            value.startsWith('\t') || value.startsWith('\r') || value.startsWith('\n')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
