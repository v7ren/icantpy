package net.icantpy.slayer.blaze

import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirePitEscapeTest {
    private val anywhere: (Int, Int) -> Boolean = { _, _ -> true }

    private fun cells(xRange: IntRange, zRange: IntRange): Set<Long> =
        xRange.flatMap { x -> zRange.map { z -> packXZ(x, z) } }.toSet()

    private fun cellOf(value: Double): Int = floor(value).toInt()

    @Test
    fun playerOutsideHazardsNeedsNoEscape() {
        val hazards = setOf(packXZ(5, 5))
        assertNull(FirePitEscape.solve(0.5, 0.5, hazards, 0.2, 10.0, anywhere))
    }

    @Test
    fun threeByThreeHazardCenteredOnPlayerEscapesPastItsEdge() {
        // Player in the middle of cell (0,0); hazard spans cells -1..1, so the edge is 1.5 blocks away.
        val hazards = cells(-1..1, -1..1)
        val margin = 0.3
        val escape = FirePitEscape.solve(0.5, 0.5, hazards, margin, 6.0, anywhere)
        assertNotNull(escape)
        assertTrue(escape.distance in 1.8..2.05, "distance was ${escape.distance}")
        assertFalse(FirePitEscape.isInside(escape.targetX, escape.targetZ, hazards, margin))
    }

    @Test
    fun plusShapedHazardHasSafeDiagonalEscape() {
        val hazards = setOf(packXZ(0, 0), packXZ(1, 0), packXZ(-1, 0), packXZ(0, 1), packXZ(0, -1))
        val escape = FirePitEscape.solve(0.5, 0.5, hazards, 0.2, 3.0, anywhere)
        assertNotNull(escape)
        assertFalse(FirePitEscape.isInside(escape.targetX, escape.targetZ, hazards, 0.2))
        assertTrue(escape.distance <= 1.5, "distance was ${escape.distance}")
    }

    @Test
    fun ringWithOneGapEscapesThroughGap() {
        // Every cell in -1..1 is hazard except (1, 0), which is the gap on the +X side.
        val hazards = cells(-1..1, -1..1) - packXZ(1, 0)
        val escape = FirePitEscape.solve(0.5, 0.5, hazards, 0.2, 3.0, anywhere)
        assertNotNull(escape)
        assertEquals(1, cellOf(escape.targetX))
        assertEquals(0, cellOf(escape.targetZ))
    }

    @Test
    fun noStandableCellMeansNoEscape() {
        val hazards = cells(-1..1, -1..1)
        assertNull(FirePitEscape.solve(0.5, 0.5, hazards, 0.3, 6.0) { _, _ -> false })
    }

    @Test
    fun standabilityLimitsChosenEscape() {
        val hazards = cells(-1..1, -1..1)
        val escape = FirePitEscape.solve(0.5, 0.5, hazards, 0.3, 6.0) { x, _ -> x >= 2 }
        assertNotNull(escape)
        assertTrue(cellOf(escape.targetX) >= 2)
        assertTrue(escape.distance in 1.8..2.05, "distance was ${escape.distance}")
    }

    @Test
    fun escapeBearingMatchesYawConventionForTarget() {
        assertEquals(0f, FirePitEscape.bearing(0.0, 0.0, 0.0, 1.0), ANGLE_TOLERANCE)
        assertEquals(-90f, FirePitEscape.bearing(0.0, 0.0, 1.0, 0.0), ANGLE_TOLERANCE)
        assertEquals(90f, FirePitEscape.bearing(0.0, 0.0, -1.0, 0.0), ANGLE_TOLERANCE)
        assertEquals(180f, FirePitEscape.bearing(0.0, 0.0, 0.0, -1.0), ANGLE_TOLERANCE)
    }

    @Test
    fun targetPlusXIsLeftAndMinusXIsRightWhenLookingSouth() {
        // Yaw 0 faces +Z; the player's right hand points to -X.
        assertEquals(RelativeDirection.LEFT, FirePitEscape.relativeDirection(0f, FirePitEscape.bearing(0.0, 0.0, 1.0, 0.0)))
        assertEquals(RelativeDirection.RIGHT, FirePitEscape.relativeDirection(0f, FirePitEscape.bearing(0.0, 0.0, -1.0, 0.0)))
        assertEquals(RelativeDirection.FORWARD, FirePitEscape.relativeDirection(0f, FirePitEscape.bearing(0.0, 0.0, 0.0, 1.0)))
        assertEquals(RelativeDirection.BACK, FirePitEscape.relativeDirection(0f, FirePitEscape.bearing(0.0, 0.0, 0.0, -1.0)))
    }

    @Test
    fun relativeDirectionCoversAllEightSectors() {
        val expected = listOf(
            0f to RelativeDirection.FORWARD,
            45f to RelativeDirection.FORWARD_RIGHT,
            90f to RelativeDirection.RIGHT,
            135f to RelativeDirection.BACK_RIGHT,
            180f to RelativeDirection.BACK,
            -135f to RelativeDirection.BACK_LEFT,
            -90f to RelativeDirection.LEFT,
            -45f to RelativeDirection.FORWARD_LEFT,
        )
        for ((bearing, direction) in expected) {
            assertEquals(direction, FirePitEscape.relativeDirection(0f, bearing), "bearing $bearing")
        }
    }

    @Test
    fun relativeDirectionHandlesWrapAround() {
        assertEquals(RelativeDirection.FORWARD, FirePitEscape.relativeDirection(350f, 0f))
        assertEquals(RelativeDirection.BACK, FirePitEscape.relativeDirection(-190f, 0f))
        assertEquals(RelativeDirection.FORWARD_RIGHT, FirePitEscape.relativeDirection(720f, 45f))
        assertEquals(RelativeDirection.RIGHT, FirePitEscape.relativeDirection(-720f, 90f))
    }

    @Test
    fun movementKeysMatchEachDirection() {
        val expected = listOf(
            0f to KeyCombo(forward = true, back = false, left = false, right = false),
            45f to KeyCombo(forward = true, back = false, left = false, right = true),
            90f to KeyCombo(forward = false, back = false, left = false, right = true),
            135f to KeyCombo(forward = false, back = true, left = false, right = true),
            180f to KeyCombo(forward = false, back = true, left = false, right = false),
            -135f to KeyCombo(forward = false, back = true, left = true, right = false),
            -90f to KeyCombo(forward = false, back = false, left = true, right = false),
            -45f to KeyCombo(forward = true, back = false, left = true, right = false),
        )
        for ((bearing, keys) in expected) {
            assertEquals(keys, FirePitEscape.movementKeys(0f, bearing), "bearing $bearing")
        }
    }

    @Test
    fun movementKeysUseRelativeYaw() {
        // Facing west (yaw 90); a target 45 degrees to the player's right is forward-right.
        assertEquals(
            KeyCombo(forward = true, back = false, left = false, right = true),
            FirePitEscape.movementKeys(90f, 135f),
        )
        // Wrap-around input: yaw 765 is yaw 45, so bearing 0 is 45 degrees to the player's left (forward-left).
        assertEquals(
            KeyCombo(forward = true, back = false, left = true, right = false),
            FirePitEscape.movementKeys(765f, 0f),
        )
    }

    private companion object {
        const val ANGLE_TOLERANCE = 1e-3f
    }
}
