package net.icantpy.qol.path

/**
 * Shared types for the ground pathfinder ported from senura. The search ([PathFinder]) is pure and only sees the
 * world through [BlockGrid]; the Minecraft side supplies a grid backed by the client level.
 */

/** A block position: the block the player's feet occupy when standing on this node. */
data class PathNode(val x: Int, val y: Int, val z: Int)

/** Read-only view of the world for the search. Coordinates are block coordinates. */
interface BlockGrid {
    /** The player's body may occupy this block: no collision and not a damaging block (fire, lava, cactus...). */
    fun passable(x: Int, y: Int, z: Int): Boolean

    /** The player can stand on top of this block (it has a solid top face and is not damaging). */
    fun solidFloor(x: Int, y: Int, z: Int): Boolean

    /** The block is water (the player can wade or swim through it). */
    fun water(x: Int, y: Int, z: Int): Boolean
}
