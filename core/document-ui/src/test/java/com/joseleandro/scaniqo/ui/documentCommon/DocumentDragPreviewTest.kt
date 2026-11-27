package com.joseleandro.scaniqo.ui.documentCommon

import com.joseleandro.scaniqo.ui.documentCommon.model.DocumentReorderDrag
import com.joseleandro.scaniqo.ui.documentCommon.util.documentDragPreviewTop
import com.joseleandro.scaniqo.ui.documentCommon.util.documentDragScrollSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDragPreviewTest {
    @Test fun floatingCardStaysEntirelyVisibleAtBothViewportEdges() {
        assertEquals(0, documentDragPreviewTop(-80f, 96, 400))
        assertEquals(0, documentDragPreviewTop(0f, 96, 400))
        assertEquals(152, documentDragPreviewTop(200f, 96, 400))
        assertEquals(304, documentDragPreviewTop(400f, 96, 400))
        assertEquals(304, documentDragPreviewTop(520f, 96, 400))
    }

    @Test fun largeFontCardTallerThanViewportNeverGetsANegativePlacement() {
        assertEquals(0, documentDragPreviewTop(80f, 500, 320))
        assertEquals(0, documentDragPreviewTop(80f, 96, 0))
    }

    @Test fun clippingPreviewDoesNotChangeGestureCenterOrPreventEdgeAutoscroll() {
        val drag = DocumentReorderDrag(listOf("a", "b"), "a", 440f, 96)
        assertEquals(304, documentDragPreviewTop(drag.centerY, drag.height, 400))
        assertEquals(440f, drag.centerY, 0f)
        assertTrue(documentDragScrollSpeed(drag.centerY, 0f, 400f, 56f, 720f) > 0f)
        assertEquals(listOf("a", "b"), drag.order)
    }
}
