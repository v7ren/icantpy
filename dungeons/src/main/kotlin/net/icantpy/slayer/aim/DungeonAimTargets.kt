package net.icantpy.slayer.aim

import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.DungeonListener
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.LivingEntity

/**
 * Livid target source for the dungeon aim option. It only read what the client already renders (entities and one
 * block); the camera is then turned by [BossAim] like for any other target.
 */
object DungeonAimTargets {
    /** Ceiling wool above the F5/M5 Livid room. Its colour names the real Livid. */
    private val LIVID_WOOL = BlockPos(5, 108, 40)

    private val LIVIDS = mapOf(
        "white_wool" to "Vendetta Livid",
        "magenta_wool" to "Crossed Livid",
        "yellow_wool" to "Arcade Livid",
        "lime_wool" to "Smile Livid",
        "gray_wool" to "Doctor Livid",
        "purple_wool" to "Purple Livid",
        "green_wool" to "Frog Livid",
        "blue_wool" to "Scream Livid",
        "red_wool" to "Hockey Livid",
    )

    /** Livid boss name for a ceiling wool block id path such as "green_wool", or null for any other block. */
    fun lividNameFor(blockId: String): String? = LIVIDS[blockId]

    /** The real Livid while in the F5/M5 boss room, as a one-element list; empty otherwise. */
    fun livid(level: ClientLevel): List<LivingEntity> {
        val state = DungeonListener.state
        val floor = state.floor
        if (!state.inDungeon || !state.inBoss || (floor != DungeonFloor.F5 && floor != DungeonFloor.M5)) return emptyList()
        val name = lividNameFor(BuiltInRegistries.BLOCK.getKey(level.getBlockState(LIVID_WOOL).block).path) ?: return emptyList()
        val livid = level.entitiesForRendering().asSequence()
            .filterIsInstance<AbstractClientPlayer>()
            .firstOrNull { it.gameProfile.name == name && it.isAlive } ?: return emptyList()
        return listOf(livid)
    }
}
