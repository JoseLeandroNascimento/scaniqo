package com.joseleandro.scaniqo.ui.documentSearch

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchHighlightTest {
    private val style = SpanStyle(fontWeight = FontWeight.SemiBold)

    @Test
    fun highlightsEveryMatchWithoutChangingTheOriginalText() {
        val result = highlightSearchMatches("Contrato e CONTRATO", "contrato", style)

        assertEquals("Contrato e CONTRATO", result.text)
        assertEquals(listOf(0 to 8, 11 to 19), result.spanStyles.map { it.start to it.end })
        assertTrue(result.spanStyles.all { it.item == style })
    }

    @Test
    fun highlightsWordsSeparatedByDifferentWhitespace() {
        val result = highlightSearchMatches("Serviços\u00a0  prestados", "  serviços  prestados  ", style)

        assertEquals(listOf(0 to result.length), result.spanStyles.map { it.start to it.end })
    }

    @Test
    fun treatsRegexCharactersAsLiteralText() {
        val result = highlightSearchMatches("Total (R$): 42", "(R$)", style)

        assertEquals(listOf(6 to 10), result.spanStyles.map { it.start to it.end })
    }

    @Test
    fun emptyOrUnmatchedQueriesLeaveTextUnhighlighted() {
        assertTrue(highlightSearchMatches("Contrato", " \t ", style).spanStyles.isEmpty())
        assertTrue(highlightSearchMatches("Contrato", "proposta", style).spanStyles.isEmpty())
    }
}
