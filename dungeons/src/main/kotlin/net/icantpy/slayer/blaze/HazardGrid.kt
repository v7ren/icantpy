package net.icantpy.slayer.blaze

import net.icantpy.qol.path.BlockGrid

/**
 * The world as the escape pathfinder should see it: [base] with every hazard column (pits forming, fire and its
 * avoid radius) made impassable and unwalkable at every height, so routes go around pits instead of through them.
 * [blocked] must be a snapshot, not a set that is rebuilt every tick.
 */
class HazardGrid(private val base: BlockGrid, private val blocked: Set<Long>) : BlockGrid {
    override fun passable(x: Int, y: Int, z: Int): Boolean = packXZ(x, z) !in blocked && base.passable(x, y, z)

    override fun solidFloor(x: Int, y: Int, z: Int): Boolean = packXZ(x, z) !in blocked && base.solidFloor(x, y, z)

    override fun water(x: Int, y: Int, z: Int): Boolean = packXZ(x, z) !in blocked && base.water(x, y, z)
}
