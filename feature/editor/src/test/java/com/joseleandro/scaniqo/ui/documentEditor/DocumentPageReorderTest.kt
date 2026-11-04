package com.joseleandro.scaniqo.ui.documentEditor

import com.joseleandro.scaniqo.ui.documentEditor.model.PageDragBounds
import com.joseleandro.scaniqo.ui.documentEditor.model.PageReorderDrag
import com.joseleandro.scaniqo.ui.documentEditor.util.movePageOrder
import com.joseleandro.scaniqo.ui.documentEditor.util.pageDragScrollSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentPageReorderTest {
    @Test fun directMoveUsesFinalIndexInBothDirections() {
        val order = (1..10).map(Int::toString)
        assertEquals((2..10).map(Int::toString) + "1", movePageOrder(order, "1", 9))
        assertEquals(listOf("10") + (1..9).map(Int::toString), movePageOrder(order, "10", 0))
        assertEquals(order, movePageOrder(order, "missing", 2))
        assertEquals(order, movePageOrder(order, "1", -1))
        assertEquals(order, movePageOrder(order, "1", 10))
    }

    @Test fun dragOnlyReordersAfterCrossingAnotherPagesCenter() {
        val order = listOf("a", "b", "c")
        val bounds = listOf(
            PageDragBounds("a", 0, 80),
            PageDragBounds("b", 88, 80),
            PageDragBounds("c", 176, 80),
        )
        val start = PageReorderDrag(order, "a", centerY = 40f, height = 80)
        assertNull(start.moveBy(87f, bounds).committedOrder())
        val crossed = start.moveBy(89f, bounds)
        assertEquals(listOf("b", "a", "c"), crossed.order)
        assertEquals(order, start.order)
        assertEquals(order, crossed.initialOrder)
    }

    @Test fun returningDragToOriginalPositionDoesNotCommit() {
        val initial = listOf("a", "b", "c")
        val start = PageReorderDrag(initial, "a", centerY = 40f, height = 80)
        val moved = start.moveBy(
            90f,
            listOf(PageDragBounds("a", 0, 80), PageDragBounds("b", 88, 80), PageDragBounds("c", 176, 80)),
        )
        val returned = moved.moveBy(
            -100f,
            listOf(PageDragBounds("b", 0, 80), PageDragBounds("a", 88, 80), PageDragBounds("c", 176, 80)),
        )
        assertEquals(initial, returned.order)
        assertNull(returned.committedOrder())
    }

    @Test fun autoscrollCanRetargetPagesWhilePointerStaysStill() {
        val initial = listOf("a", "b", "c", "d")
        val moving = PageReorderDrag(initial, "a", centerY = 170f, height = 80)
        val first = moving.retarget(
            listOf(PageDragBounds("a", 0, 80), PageDragBounds("b", 88, 80), PageDragBounds("c", 176, 80)),
        )
        assertEquals(listOf("b", "a", "c", "d"), first.order)
        val afterScroll = first.retarget(
            listOf(PageDragBounds("a", -10, 80), PageDragBounds("c", 78, 80), PageDragBounds("d", 166, 80)),
        )
        assertEquals(listOf("b", "c", "a", "d"), afterScroll.order)
        assertEquals(moving.centerY, afterScroll.centerY, .001f)
    }

    @Test fun dragCanCrossSeveralVariableHeightPagesInOneEvent() {
        val order = listOf("a", "b", "c", "d")
        val moved = PageReorderDrag(order, "a", 30f, 60).moveBy(
            300f,
            listOf(PageDragBounds("a", 0, 60), PageDragBounds("b", 68, 100), PageDragBounds("c", 176, 80)),
        )
        assertEquals(listOf("b", "c", "a", "d"), moved.committedOrder())
    }

    @Test fun edgeScrollIsGradualBoundedAndDisabledInMiddle() {
        assertEquals(0f, pageDragScrollSpeed(200f, 0f, 400f, 50f, 800f), .001f)
        assertEquals(-400f, pageDragScrollSpeed(25f, 0f, 400f, 50f, 800f), .001f)
        assertEquals(400f, pageDragScrollSpeed(375f, 0f, 400f, 50f, 800f), .001f)
        assertEquals(-800f, pageDragScrollSpeed(-100f, 0f, 400f, 50f, 800f), .001f)
        assertEquals(800f, pageDragScrollSpeed(700f, 0f, 400f, 50f, 800f), .001f)
        assertEquals(0f, pageDragScrollSpeed(0f, 0f, 0f, 50f, 800f), .001f)
    }
}
