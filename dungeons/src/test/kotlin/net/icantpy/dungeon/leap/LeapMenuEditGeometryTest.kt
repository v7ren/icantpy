package net.icantpy.dungeon.leap

import net.icantpy.hud.HudHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LeapMenuEditGeometryTest {
    private val boxes = listOf(
        LeapMenuBox(10f, 20f, 100f, 50f),
        LeapMenuBox(130f, 40f, 80f, 40f),
        LeapMenuBox(220f, 100f, 60f, 30f)
    )

    @Test
    fun selectionBoundsIncludeAllSelectedCardsAndIgnoreInvalidIndices() {
        assertEquals(LeapMenuBox(10f, 20f, 200f, 60f),
            LeapMenuEditGeometry.bounds(boxes, setOf(-1, 0, 1, 5)))
        assertEquals(null, LeapMenuEditGeometry.bounds(boxes, emptySet()))
    }

    @Test
    fun movementUsesOneClampedDeltaForTheWholeSelection() {
        val moved = LeapMenuEditGeometry.move(boxes, setOf(0, 1), -50f, 500f, 300, 200)
        assertEquals(0f, moved[0].left)
        assertEquals(120f, moved[1].left)
        assertEquals(140f, moved[0].top)
        assertEquals(200f, moved[1].bottom)
        assertSame(boxes[2], moved[2])
        assertEquals(10f, boxes[0].left)
    }

    @Test
    fun independentResizeKeepsCardCenterAndLeavesOthersUntouched() {
        val resized = LeapMenuEditGeometry.resize(boxes, setOf(1), 1.5f, 2f, 500, 300)
        assertEquals(120f, resized[1].width)
        assertEquals(80f, resized[1].height)
        assertEquals(boxes[1].centerX, resized[1].centerX)
        assertEquals(boxes[1].centerY, resized[1].centerY)
        assertSame(boxes[0], resized[0])
        assertSame(boxes[2], resized[2])
    }

    @Test
    fun groupResizeScalesSizesAndSpacingInSync() {
        val resized = LeapMenuEditGeometry.resize(boxes, setOf(0, 1), 2f, 1.5f, 600, 300)
        assertEquals(200f, resized[0].width)
        assertEquals(160f, resized[1].width)
        assertEquals(75f, resized[0].height)
        assertEquals(40f, resized[1].left - resized[0].right)
        assertEquals(240f, resized[1].left - resized[0].left)
        assertSame(boxes[2], resized[2])
    }

    @Test
    fun proportionalResizePreservesEveryAspectRatioAndUsesDominantDrag() {
        val resized = LeapMenuEditGeometry.resize(boxes, setOf(0, 1), 1.1f, 1.5f, 600, 300, true)
        assertEquals(150f, resized[0].width)
        assertEquals(75f, resized[0].height)
        assertEquals(120f, resized[1].width)
        assertEquals(60f, resized[1].height)
    }

    @Test
    fun smallestSelectedCardSetsSharedMinimumScale() {
        val resized = LeapMenuEditGeometry.resize(boxes, setOf(0, 1), 0.01f, 0.01f, 600, 300)
        assertEquals(60f, resized[0].width, 0.001f)
        assertEquals(48f, resized[1].width, 0.001f)
        assertEquals(30f, resized[0].height, 0.001f)
        assertEquals(24f, resized[1].height, 0.001f)
        assertEquals(12f, resized[1].left - resized[0].right, 0.001f)
    }

    @Test
    fun viewportCapsSharedScaleWithoutCollapsingGroupSpacing() {
        val resized = LeapMenuEditGeometry.resize(boxes, setOf(0, 1), 100f, 100f, 300, 180)
        val bounds = LeapMenuEditGeometry.bounds(resized, setOf(0, 1))!!
        assertEquals(300f, bounds.width)
        assertEquals(180f, bounds.height)
        assertEquals(30f, resized[1].left - resized[0].right)
        assertTrue(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= 300 && bounds.bottom <= 180)
    }

    @Test
    fun proportionalViewportCapKeepsAspectRatios() {
        val resized = LeapMenuEditGeometry.resize(boxes, setOf(0, 1), 100f, 100f, 300, 180, true)
        assertEquals(150f, resized[0].width)
        assertEquals(75f, resized[0].height)
        assertEquals(120f, resized[1].width)
        assertEquals(60f, resized[1].height)
    }

    @Test
    fun impossibleMinimumSizeInTinyViewportStillFitsWithoutThrowing() {
        val resized = LeapMenuEditGeometry.resize(boxes, setOf(0, 1), 0.01f, 0.01f, 20, 10)
        val bounds = LeapMenuEditGeometry.bounds(resized, setOf(0, 1))!!
        assertTrue(bounds.width <= 20 && bounds.height <= 10)
    }

    @Test
    fun invalidTransformsAndEmptySelectionAreNoOps() {
        assertSame(boxes, LeapMenuEditGeometry.move(boxes, setOf(0), Float.NaN, 1f, 300, 200))
        assertSame(boxes, LeapMenuEditGeometry.resize(boxes, setOf(0), Float.POSITIVE_INFINITY, 1f, 300, 200))
        assertSame(boxes, LeapMenuEditGeometry.resize(boxes, emptySet(), 2f, 2f, 300, 200))
        assertSame(boxes, LeapMenuEditGeometry.resize(boxes, setOf(0), 2f, 2f, 0, 200))
    }

    @Test
    fun numericSizeSyncsEverySelectedCardAndLeavesOthers() {
        val resized = LeapMenuEditGeometry.setSize(boxes, setOf(0, 1), 90f, 40f, 500, 300)
        assertEquals(90f, resized[0].width)
        assertEquals(90f, resized[1].width)
        assertEquals(40f, resized[0].height)
        assertEquals(40f, resized[1].height)
        assertEquals(boxes[0].left, resized[0].left)
        assertEquals(boxes[0].top, resized[0].top)
        assertSame(boxes[2], resized[2])
    }

    @Test
    fun edgeResizeGrowsFromTheDraggedSideOnly() {
        val box = LeapMenuBox(100f, 80f, 100f, 50f)
        val cards = listOf(box)
        val east = LeapMenuEditGeometry.resize(cards, setOf(0), 1.5f, 1f, 500, 300, handle = HudHandle.E)
        assertEquals(100f, east[0].left)
        assertEquals(150f, east[0].width)
        assertEquals(80f, east[0].top)
        val west = LeapMenuEditGeometry.resize(cards, setOf(0), 1.5f, 1f, 500, 300, handle = HudHandle.W)
        assertEquals(50f, west[0].left)
        assertEquals(200f, west[0].right)
        assertEquals(80f, west[0].top)
        val south = LeapMenuEditGeometry.applyDrag(
            cards, setOf(0), HudHandle.S, box, 0f, 0f, 150f, 155f, 500, 300,
        )
        assertEquals(80f, south[0].top)
        assertEquals(75f, south[0].height)
    }

    @Test
    fun handleDragScalesFromTheGrabbedEdge() {
        val bounds = LeapMenuBox(10f, 20f, 100f, 50f)
        assertEquals(1.5f to 1f, LeapMenuEditGeometry.scaleFromHandle(bounds, HudHandle.E, 160f, 40f))
        assertEquals(1f to 2f, LeapMenuEditGeometry.scaleFromHandle(bounds, HudHandle.S, 60f, 120f))
        val moved = LeapMenuEditGeometry.applyDrag(
            boxes, setOf(0), HudHandle.MOVE, bounds, 0f, 0f, 20f, 20f, 500, 300,
        )
        assertEquals(20f, moved[0].left)
        assertEquals(20f, moved[0].top)
    }

    @Test
    fun sharedDimensionIsNullWhenSelectedSizesDiffer() {
        assertEquals(null, LeapMenuEditGeometry.sharedDimension(boxes, setOf(0, 1), true))
        assertEquals(100f, LeapMenuEditGeometry.sharedDimension(boxes, setOf(0), true))
    }

    @Test
    fun historyUndoRedoRestoresLayoutMapsAndLayerOrder() {
        val history = LeapMenuEditHistory()
        val start = LeapOrientSettings(menuGap = 12)
        val edited = start.copy(
            boxSizes = mapOf("0" to "300,100"),
            boxPositions = mapOf("0" to "0.2,0.3"),
            boxOrder = listOf("1", "0", "2", "3", "4"),
        )
        history.record(start)
        val undone = history.undo(edited)!!
        assertEquals(start.boxSizes, undone.boxSizes)
        assertEquals(start.boxPositions, undone.boxPositions)
        assertEquals(start.boxOrder, undone.boxOrder)
        assertEquals(12, undone.menuGap)
        val redone = history.redo(undone)!!
        assertEquals(edited.boxSizes, redone.boxSizes)
        assertEquals(edited.boxPositions, redone.boxPositions)
        assertEquals(edited.boxOrder, redone.boxOrder)
        assertEquals(12, redone.menuGap)
    }
}
