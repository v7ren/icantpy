package net.icantpy.dungeon.leap

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand

/** A relic placement found this tick, with the relic color as its captured name. */
data class RelicPlacedEvent(val event: LeapTriggerEvent, val color: String)

/**
 * Feeds the self and any-player relic trackers from the game each tick during P5. Only the
 * armor-stand scan is throttled; the self check reads the inventory every tick.
 */
object LeapRelicPlacementWatcher {
    private const val STAND_SCAN_INTERVAL_TICKS = 2

    private val selfTracker = SelfRelicPlacementTracker()
    private val anyTracker = AnyRelicPlacementTracker()
    private var scanTick = 0

    fun tick(inP5: Boolean): List<RelicPlacedEvent> {
        if (!inP5) {
            reset()
            return emptyList()
        }
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return emptyList()
        val events = mutableListOf<RelicPlacedEvent>()
        val placedSelf = selfTracker.update(LeapRelicState.heldRelicColor(), player.x, player.z)
        placedSelf?.let { events += RelicPlacedEvent(LeapTriggerEvent.RELIC_PLACED_SELF, it) }

        scanTick += 1
        if (scanTick % STAND_SCAN_INTERVAL_TICKS == 0) {
            val level = mc.level
            if (level != null) {
                anyTracker.scan(standSamples(level.entitiesForRendering()))
                    .forEach { events += RelicPlacedEvent(LeapTriggerEvent.RELIC_PLACED_ANY, it) }
            }
        }
        return events
    }

    /** Clears per-run state. Called on run end, disconnect and P5 exit. */
    fun reset() {
        selfTracker.reset()
        anyTracker.reset()
        scanTick = 0
    }

    private fun standSamples(entities: Iterable<net.minecraft.world.entity.Entity>): List<RelicStandSample> =
        entities.asSequence()
            .filterIsInstance<ArmorStand>()
            .map { stand ->
                RelicStandSample(
                    x = stand.x,
                    z = stand.z,
                    color = LeapRelicState.relicColor(stand.getItemBySlot(EquipmentSlot.HEAD)),
                )
            }
            .toList()
}
