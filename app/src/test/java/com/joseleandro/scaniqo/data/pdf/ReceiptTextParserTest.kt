package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.ReceiptDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTextParserTest {
    @Test fun receiptUsesExplicitTotalAndKeepsSourceForReview() {
        val text = "MERCADO CENTRAL\nCNPJ 12.345.678/0001-90\n18/10/2026\nARROZ 10,50\nFEIJAO 9,00\nSUBTOTAL 25,00\nDESCONTO 5,50\nTOTAL R$ 19,50\nTOTAL DE ITENS 2\nDINHEIRO 20,00\nTROCO 0,50"
        val result = ReceiptTextParser.parse(text)
        assertEquals("MERCADO CENTRAL", result.merchant)
        assertEquals("18/10/2026", result.date)
        assertEquals("19,50", result.total)
        assertEquals("ARROZ 10,50\nFEIJAO 9,00", result.items)
        assertEquals(text, result.sourceText)
    }
    @Test fun noExplicitTotalDoesNotSumItemsOrReuseSubtotal() {
        val result = ReceiptTextParser.parse("LOJA\nITEM 12,00\nSUBTOTAL 12,00")
        assertEquals("", result.total)
        assertEquals("", result.date)
    }
    @Test fun totalTributesDoesNotReplaceAmountPayable() {
        assertEquals("1.234,56", ReceiptTextParser.parse(
            "LOJA\nVALOR TOTAL R$ 1.234,56\nTOTAL TRIBUTOS 123,45").total)
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
    @Test fun csvStillExportsMetadataWithoutItems() {
        val csv = ReceiptCsv.encode(ReceiptDraft(merchant = "Loja"))
        assertEquals(2, csv.trimEnd().lineSequence().count())
    }
}
