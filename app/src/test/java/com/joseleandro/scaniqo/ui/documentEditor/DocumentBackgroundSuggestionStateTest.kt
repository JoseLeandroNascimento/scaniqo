package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.domain.model.PdfPageQuality
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfQualityReport
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentBackgroundFeedback
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentBackgroundSuggestionState
import com.joseleandro.scaniqo.ui.documentEditor.state.DocumentToolsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentBackgroundSuggestionStateTest {
    private val crop = PdfPerspective(
        topLeft = PdfPoint(.1f, .1f),
        topRight = PdfPoint(.9f, .15f),
        bottomRight = PdfPoint(.85f, .9f),
        bottomLeft = PdfPoint(.15f, .85f),
    )

    @Test
    fun oldReportCannotReplaceTheCurrentManualSelection() {
        val suggestion = DocumentBackgroundSuggestionState { "new-request" }
        suggestion.begin("page")

        assertNull(suggestion.consume(completed("old-request"), "page"))

        assertNotNull(suggestion.pending)
        assertNull(suggestion.feedback)
    }

    @Test
    fun matchingCompletionReturnsTheMaskOnceWithoutChangingTheDocument() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.begin("page")
        val state = completed("request")

        assertEquals(crop, suggestion.consume(state, "page"))

        assertNull(suggestion.pending)
        assertEquals(DocumentBackgroundFeedback.SUGGESTED, suggestion.feedback)
        assertEquals(crop, state.qualityReport!!.pages.single().suggestedCrop)
        assertNull(suggestion.consume(state, "page"))
    }

    @Test
    fun completionWaitsUntilAnalysisHasStopped() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.begin("page")
        val state = completed("request")

        assertNull(suggestion.consume(state.copy(isAnalyzing = true), "page"))
        assertNotNull(suggestion.pending)
        assertEquals(crop, suggestion.consume(state, "page"))
    }

    @Test
    fun failedAnalysisIgnoresAnOlderSuccessfulReport() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.begin("page")

        assertNull(suggestion.consume(completed("request").copy(analysisError = true), "page"))

        assertNull(suggestion.pending)
        assertEquals(DocumentBackgroundFeedback.FAILED, suggestion.feedback)
    }

    @Test
    fun noDetectedEdgesLeavesTheManualSelectionUnchanged() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.begin("page")

        assertNull(suggestion.consume(completed("request", perspective = null), "page"))

        assertEquals(DocumentBackgroundFeedback.NO_EDGES, suggestion.feedback)
        assertNull(suggestion.pending)
    }

    @Test
    fun reportForAnotherPageCannotSupplyTheCrop() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.begin("page")

        assertNull(suggestion.consume(completed("request", pageId = "other-page"), "page"))

        assertEquals(DocumentBackgroundFeedback.NO_EDGES, suggestion.feedback)
    }

    @Test
    fun changingThePageDiscardsThePendingSuggestion() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.begin("page")

        assertNull(suggestion.consume(completed("request"), "other-page"))

        assertNull(suggestion.pending)
        assertNull(suggestion.feedback)
    }

    @Test
    fun lockingDiscardsThePendingSuggestion() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.begin("page")

        assertNull(suggestion.consume(completed("request").copy(isLocked = true), "page"))

        assertNull(suggestion.pending)
        assertNull(suggestion.feedback)
    }

    @Test
    fun cancellationRejectsALateCompletionAndAllowsAnotherRequest() {
        var nextId = 0
        val suggestion = DocumentBackgroundSuggestionState { (++nextId).toString() }
        suggestion.begin("page")
        suggestion.cancel()
        assertNull(suggestion.consume(completed("1"), "page"))

        val retry = suggestion.begin("page")

        assertEquals("2", retry!!.id)
        assertNull(suggestion.consume(completed("1"), "page"))
        assertEquals(crop, suggestion.consume(completed("2"), "page"))
    }

    @Test
    fun duplicateTapKeepsTheOriginalRequest() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        val request = suggestion.begin("page")

        assertNull(suggestion.begin("other-page"))

        assertEquals(request, suggestion.pending)
    }

    @Test
    fun malformedContourCannotReplaceTheManualSelection() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.begin("page")
        val crossed = crop.copy(topRight = crop.bottomLeft, bottomLeft = crop.topRight)

        assertNull(suggestion.consume(completed("request", perspective = crossed), "page"))

        assertEquals(DocumentBackgroundFeedback.NO_EDGES, suggestion.feedback)
    }

    @Test
    fun automaticDetectionRunsOnlyOnceForEachPage() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        assertNotNull(suggestion.beginAutomatically("page", hasMask = false))
        suggestion.consume(completed("request"), "page")

        assertNull(suggestion.beginAutomatically("page", hasMask = false))
        assertNotNull(suggestion.beginAutomatically("another-page", hasMask = false))
    }

    @Test
    fun cancellingDoesNotRestartAutomaticDetection() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.beginAutomatically("page", hasMask = false)
        suggestion.cancel()

        assertNull(suggestion.beginAutomatically("page", hasMask = false))
        assertNotNull(suggestion.begin("page"))
    }

    @Test
    fun anExistingManualMaskIsNeverReplacedAutomatically() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }

        assertNull(suggestion.beginAutomatically("page", hasMask = true))
        assertNull(suggestion.beginAutomatically("page", hasMask = false))
        assertNotNull(suggestion.begin("page"))
    }

    @Test
    fun restoredVisitedPagesWaitForExplicitDetection() {
        val suggestion = DocumentBackgroundSuggestionState(setOf("page")) { "request" }

        assertNull(suggestion.beginAutomatically("page", hasMask = false))
        assertNull(suggestion.pending)
        assertNotNull(suggestion.begin("page"))
    }

    @Test
    fun aValidMaskOnASmallCaptureIsAcceptedInPageCoordinates() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        val smallMask = PdfPerspective(
            PdfPoint(.1f, .1f), PdfPoint(.12f, .1f),
            PdfPoint(.12f, .12f), PdfPoint(.1f, .12f),
        )
        suggestion.begin("page")

        assertEquals(smallMask, suggestion.consume(completed("request", perspective = smallMask), "page"))

        assertEquals(DocumentBackgroundFeedback.SUGGESTED, suggestion.feedback)
    }

    @Test
    fun resultForAnOlderDraftRevisionIsIgnored() {
        val suggestion = DocumentBackgroundSuggestionState { "request" }
        suggestion.begin("page", revision = 4)

        assertNull(suggestion.consume(completed("request"), "page", revision = 5))
        assertNull(suggestion.pending)
        assertNull(suggestion.feedback)
    }

    private fun completed(
        requestId: String,
        pageId: String = "page",
        perspective: PdfPerspective? = crop,
    ) = DocumentToolsUiState(
        correctionAnalysisRequestId = requestId,
        qualityReport = PdfQualityReport(listOf(PdfPageQuality(pageId, suggestedCrop = perspective))),
    )
}
