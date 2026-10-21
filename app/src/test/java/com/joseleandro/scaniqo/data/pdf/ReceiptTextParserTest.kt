package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.ReceiptDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ReceiptTextParserTest {
    @Test fun receiptUsesExplicitTotalAndKeepsSourceForReview() {
        val text = "MERCADO CENTRAL\nCNPJ 12.345.678/0001-90\n18/10/2026\nARROZ 10,50\nFEIJAO 9,00\nSUBTOTAL 25,00\nDESCONTO 5,50\nTOTAL R$ 19,50\nTOTAL DE ITENS 2\nDINHEIRO 20,00\nTROCO 0,50"
        val result = ReceiptTextParser.parse(text)
        assertEquals("MERCADO CENTRAL", result.merchant)
        assertEquals("18/10/2026", result.date)
        assertEquals("R$ 19,50", result.total)
        assertEquals("ARROZ 10,50\nFEIJAO 9,00", result.items)
        assertEquals(text, result.sourceText)
    }
    @Test fun noExplicitTotalDoesNotSumItemsOrReuseSubtotal() {
        val result = ReceiptTextParser.parse("LOJA\nITEM 12,00\nSUBTOTAL 12,00")
        assertEquals("", result.total)
        assertEquals("", result.date)
    }
    @Test fun totalTributesDoesNotReplaceAmountPayable() {
        assertEquals("R$ 1.234,56", ReceiptTextParser.parse(
            "LOJA\nVALOR TOTAL R$ 1.234,56\nTOTAL TRIBUTOS 123,45").total)
    }
    @Test fun englishReceiptRecognizesMonthFirstDateAndAmountDueWithoutTaxOrChange() {
        val source = "RECEIPT\nCorner Shop\n10/21/2026\nCoffee $3.50\nSUBTOTAL $3.50\nSALES TAX $0.35\nAMOUNT DUE $3.85\nCASH $5.00\nCHANGE $1.15"
        val result = ReceiptTextParser.parse(source)
        assertEquals("Corner Shop", result.merchant)
        assertEquals("10/21/2026", result.date)
        assertEquals("$3.85", result.total)
        assertEquals("Coffee $3.50", result.items)
        assertEquals(source, result.sourceText)
    }

    @Test fun frenchReceiptKeepsCommaAmountAndTrailingEuroWithIsoDate() {
        val source = "FACTURE\nCafé de Paris\n2026-10-21\nCafé 2,50 €\nSOUS-TOTAL 2,50 €\nTVA 0,25 €\nNET À PAYER 2,75 €\nCARTE 2,75 €"
        val result = ReceiptTextParser.parse(source)
        assertEquals("Café de Paris", result.merchant)
        assertEquals("2026-10-21", result.date)
        assertEquals("2,75 €", result.total)
        assertEquals("Café 2,50 €", result.items)
        assertEquals(source, result.sourceText)
    }

    @Test fun indonesianReceiptKeepsRupiahThousandsWithoutInventingDecimals() {
        val result = ReceiptTextParser.parse(
            "STRUK\nTOKO KOPI\n21.10.2026\nKopi Rp 12.000\nSUBTOTAL Rp 12.000\nPAJAK Rp 1.200\nTOTAL BAYAR Rp 13.200\nTUNAI Rp 20.000\nKEMBALIAN Rp 6.800",
        )
        assertEquals("TOKO KOPI", result.merchant)
        assertEquals("21.10.2026", result.date)
        assertEquals("Rp 13.200", result.total)
        assertEquals("Kopi Rp 12.000", result.items)
    }

    @Test fun spanishReceiptPreservesAmbiguousDateAndBothNumberSeparators() {
        val result = ReceiptTextParser.parse(
            "RECIBO\nLibrería Central\n03/04/2026\nLibro 1.234,56 EUR\nDESCUENTO 0,00 EUR\nIMPORTE TOTAL 1.234,56 EUR\nTOTAL ARTICULOS 1",
        )
        assertEquals("03/04/2026", result.date)
        assertEquals("1.234,56 EUR", result.total)
        assertEquals("Libro 1.234,56 EUR", result.items)
    }

    @Test fun sameValueIsNotReformattedForDifferentDeviceLocales() {
        val original = Locale.getDefault()
        try {
            listOf(Locale.US, Locale.FRANCE, Locale.forLanguageTag("id-ID")).forEach { locale ->
                Locale.setDefault(locale)
                assertEquals("USD 1,234.56", ReceiptTextParser.parse("SHOP\nTOTAL USD 1,234.56").total)
                assertEquals("1,234", ReceiptTextParser.parse("SHOP\nTOTAL 1,234").total)
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun multipleCurrenciesOnOneTotalLineAreLeftForManualReview() {
        val source = "SHOP\nTOTAL USD 10.00 / EUR 9,50"
        val result = ReceiptTextParser.parse(source)
        assertEquals("", result.total)
        assertEquals(source, result.sourceText)
    }

    @Test fun totalItemCountsAndTaxDoNotReplacePayableTotal() {
        val result = ReceiptTextParser.parse(
            "SHOP\nGRAND TOTAL $12.34\nTOTAL ITEMS 2\nTOTAL TAX $1.34",
        )
        assertEquals("$12.34", result.total)
    }

    @Test fun translatedItemCountsAndTaxTotalsAreNotPayableTotals() {
        assertEquals("", ReceiptTextParser.parse("TOKO\nJUMLAH BARANG 2").total)
        assertEquals("", ReceiptTextParser.parse("BOUTIQUE\nTOTAL HT 100,00 €").total)
        assertEquals("", ReceiptTextParser.parse("TIENDA\nIVA TOTAL 12,00 EUR").total)
    }

    @Test fun datesAndPercentagesAreNotMistakenForAmounts() {
        assertEquals("", ReceiptTextParser.parse("SHOP\nTOTAL 2026-10-21").total)
        assertEquals("", ReceiptTextParser.parse("SHOP\nTOTAL 2026-10-21 13:30").total)
        assertEquals("", ReceiptTextParser.parse("SHOP\nTOTAL 10.50%").total)
        assertEquals("", ReceiptTextParser.parse("SHOP\n31/02/2026").date)
        assertEquals("", ReceiptTextParser.parse("SHOP\n2025-02-29").date)
        assertEquals("29/02/2024", ReceiptTextParser.parse("SHOP\n29/02/2024").date)
    }

    @Test fun explicitWholeNumberTotalKeepsItsOriginalText() {
        assertEquals("IDR 12000", ReceiptTextParser.parse("TOKO\nTOTAL IDR 12000").total)
        assertEquals("12000", ReceiptTextParser.parse("TOKO\nTOTAL 12000").total)
    }

    @Test fun emptyOcrDoesNotInventValues() {
        assertEquals(ReceiptDraft(), ReceiptTextParser.parse(""))
    }
    @Test fun csvPreservesQuotesSemicolonsAndNewlines() {
        val csv = ReceiptCsv.encode(ReceiptDraft(merchant = "Loja \"A\"; Sul", date = "18/10/2026", total = "19,50",
            items = "ARROZ 10,50\nFEIJAO 9,00"))
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"Loja \"\"A\"\"; Sul\""))
        assertEquals(3, csv.trimEnd().lineSequence().count())
        assertTrue(csv.contains("\"19,50\";\"ARROZ 10,50\""))
    }
    @Test fun csvNeutralizesSpreadsheetFormulaPrefixesInEveryField() {
        val csv = ReceiptCsv.encode(ReceiptDraft(merchant = "=1+1", date = " +cmd", total = "-2", items = "@SUM(A1)"))
        assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"' +cmd\""))
        assertTrue(csv.contains("\"'-2\""))
        assertTrue(csv.contains("\"'@SUM(A1)\""))
        assertFalse(csv.contains("\"=1+1\""))
    }
    @Test fun csvUsesLocalizedHeadersAndRegionalSeparatorWithoutChangingReceiptValues() {
        val receipt = ReceiptDraft(merchant = "Café", date = "03/04/2026", total = "EUR 1.234,56", items = "Livre")
        val headers = ReceiptCsv.Headers("Commerce", "Date", "Total", "Article")
        val french = ReceiptCsv.encode(receipt, headers, ReceiptCsv.separatorFor(Locale.FRANCE))
        assertTrue(french.startsWith("\uFEFF\"Commerce\";\"Date\";\"Total\";\"Article\"\r\n"))
        assertTrue(french.contains("\"03/04/2026\";\"EUR 1.234,56\""))
        val english = ReceiptCsv.encode(receipt, separator = ReceiptCsv.separatorFor(Locale.US))
        assertTrue(english.startsWith("\uFEFF\"Merchant\",\"Date\",\"Total\",\"Item\"\r\n"))
        assertTrue(english.contains("\"03/04/2026\",\"EUR 1.234,56\""))
    }

    @Test fun csvStillExportsMetadataWithoutItems() {
        val csv = ReceiptCsv.encode(ReceiptDraft(merchant = "Loja"))
        assertEquals(2, csv.trimEnd().lineSequence().count())
    }
}
