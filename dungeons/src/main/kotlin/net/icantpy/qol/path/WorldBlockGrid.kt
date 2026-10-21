package net.icantpy.qol.path

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.BannerBlock
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CactusBlock
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.CarpetBlock
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.DoublePlantBlock
import net.minecraft.world.level.block.FlowerBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.MagmaBlock
import net.minecraft.world.level.block.PowderSnowBlock
import net.minecraft.world.level.block.RedStoneWireBlock
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.level.block.SignBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.SugarCaneBlock
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.level.block.TorchBlock
import net.minecraft.world.level.block.TripWireBlock
import net.minecraft.world.level.block.TripWireHookBlock
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.WallBannerBlock
import net.minecraft.world.level.block.WallSignBlock
import net.minecraft.world.level.block.WitherRoseBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids

/**
 * [BlockGrid] backed by the client level. Rules are ported from senura's WorldCache:
 * - passable: no collision shape (open doors and gates, carpets, flowers, ladders, air), not damaging;
 *   water is passable here (no collision) and the search decides whether to use it via [water];
 * - solid floor: slabs, stairs, farmland and dirt path; carpet and snow layers when the block below is not air;
 *   other non-decorative blocks whose collision top reaches about y+1 (fences, walls, full blocks);
 * - water: flowing or source water.
 * Blocks in chunks that are not loaded come from [WorldSnapshot] when that chunk was captured earlier. A chunk that
 * was never captured is never passable, never a floor and never water. The rules are shared with the snapshot
 * capture, so both paths classify blocks identically. Reads the live level, so call it from the client thread only.
 */
object WorldBlockGrid : BlockGrid {
    /** Top face height treated as a full floor for generic blocks (senura accepts any non-decorative block). */
    internal const val FLOOR_TOP_MIN = 0.9

    override fun passable(x: Int, y: Int, z: Int): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val pos = BlockPos(x, y, z)
        val state = loadedState(level, pos) ?: return cachedHas(x, y, z, ChunkStore.PASSABLE)
        return passableRule(level, pos, state)
    }

    override fun solidFloor(x: Int, y: Int, z: Int): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val pos = BlockPos(x, y, z)
        val state = loadedState(level, pos) ?: return cachedHas(x, y, z, ChunkStore.SOLID_FLOOR)
        return solidFloorRule(level, pos, state)
    }

    override fun water(x: Int, y: Int, z: Int): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val pos = BlockPos(x, y, z)
        val state = loadedState(level, pos) ?: return cachedHas(x, y, z, ChunkStore.WATER)
        return isWater(state.fluidState)
    }

    /** Flag bits for a block in a loaded chunk, as stored by [WorldSnapshot]. */
    internal fun cellFlags(level: ClientLevel, pos: BlockPos, state: BlockState): Int {
        if (state.isAir) return ChunkStore.PASSABLE
        var flags = 0
        if (passableRule(level, pos, state)) flags = flags or ChunkStore.PASSABLE
        if (solidFloorRule(level, pos, state)) flags = flags or ChunkStore.SOLID_FLOOR
        if (isWater(state.fluidState)) flags = flags or ChunkStore.WATER
        return flags
    }

    internal fun passableRule(level: ClientLevel, pos: BlockPos, state: BlockState): Boolean {
        if (isDamaging(state)) return false
        return state.getCollisionShape(level, pos).isEmpty
    }

    internal fun solidFloorRule(level: ClientLevel, pos: BlockPos, state: BlockState): Boolean {
        if (state.isAir || !state.fluidState.isEmpty || isDamaging(state)) return false
        val block = state.block
        if (block is SlabBlock || block is StairBlock || block == Blocks.FARMLAND || block == Blocks.DIRT_PATH) {
            return true
        }
        if (block is CarpetBlock || block is SnowLayerBlock) {
            return supportsDecor(level, pos.below())
        }
        if (isDecorative(block)) return false
        val shape = state.getCollisionShape(level, pos)
        return !shape.isEmpty && shape.max(Direction.Axis.Y) >= FLOOR_TOP_MIN
    }

    /** Block state at [pos], or null when its chunk is not loaded on the client. */
    internal fun loadedState(level: ClientLevel, pos: BlockPos): BlockState? {
        if (!level.hasChunk(pos.x shr 4, pos.z shr 4)) return null
        return level.getBlockState(pos)
    }

    /** Carpets and snow layers are floors only when they sit on a non-air, non-fluid block (senura rule). */
    private fun supportsDecor(level: ClientLevel, below: BlockPos): Boolean {
        val state = loadedState(level, below) ?: return false
        return !state.isAir && state.fluidState.isEmpty
    }

    /** Whether the snapshot has [bit] set for (x, y, z). Blocks with no cached data have no bits. */
    private fun cachedHas(x: Int, y: Int, z: Int, bit: Int): Boolean {
        val flags = WorldSnapshot.cachedFlags(x, y, z) ?: return false
        return flags and bit != 0
    }

    /** Fire, magma, cactus, berry bushes, powder snow, wither roses, lit campfires and lava hurt the player. */
    internal fun isDamaging(state: BlockState): Boolean {
        val block = state.block
        if (block is BaseFireBlock || block is MagmaBlock || block is CactusBlock) return true
        if (block is SweetBerryBushBlock || block is PowderSnowBlock || block is WitherRoseBlock) return true
        if (block is CampfireBlock && state.getValue(CampfireBlock.LIT)) return true
        return isLava(state.fluidState)
    }

    internal fun isLava(fluid: FluidState): Boolean =
        fluid.type == Fluids.LAVA || fluid.type == Fluids.FLOWING_LAVA

    internal fun isWater(fluid: FluidState): Boolean =
        fluid.type == Fluids.WATER || fluid.type == Fluids.FLOWING_WATER

    /** Senura's decorative passable list: blocks the player walks through or that never act as a floor. */
    internal fun isDecorative(block: Block): Boolean =
        block is FlowerBlock ||
            block is BushBlock ||
            block is DoublePlantBlock ||
            block is CropBlock ||
            block is SaplingBlock ||
            block is SweetBerryBushBlock ||
            block is SugarCaneBlock ||
            block is TorchBlock ||
            block is RedStoneWireBlock ||
            block is LeverBlock ||
            block is ButtonBlock ||
            block is SignBlock ||
            block is WallSignBlock ||
            block is BannerBlock ||
            block is WallBannerBlock ||
            block is TripWireBlock ||
            block is TripWireHookBlock ||
            block is CarpetBlock ||
            block is LadderBlock ||
            block is VineBlock ||
            block == Blocks.SHORT_GRASS ||
            block == Blocks.TALL_GRASS ||
            block == Blocks.SEAGRASS ||
            block == Blocks.TALL_SEAGRASS ||
            block == Blocks.FERN ||
            block == Blocks.LARGE_FERN ||
            block == Blocks.DEAD_BUSH ||
            block == Blocks.LILY_PAD ||
            block == Blocks.HANGING_ROOTS ||
            block == Blocks.NETHER_SPROUTS
}
