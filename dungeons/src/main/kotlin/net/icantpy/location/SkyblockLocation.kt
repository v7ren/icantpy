package net.icantpy.location

import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.compat.McCompat
import net.icantpy.dungeon.DungeonListener
import net.minecraft.client.Minecraft

/** Result of one location sample: the reading plus the changes since the previous sample. */
data class LocationSample(val snapshot: LocationSnapshot, val changes: List<LocationChange>)

/**
 * Client-side location state. Reads the tab list and sidebar every [SAMPLE_TICKS] ticks; no packets are sent.
 */
object SkyblockLocation {
    const val SAMPLE_TICKS = 10
    private var ticks = 0
    private val tracker = SkyblockLocationTracker()

    /** Latest reading. Stays [LocationSnapshot.EMPTY] until the first sample with a client connected. */
    var snapshot: LocationSnapshot = LocationSnapshot.EMPTY
        private set

    /** Samples when due and returns the result, or null on ticks in between. */
    fun tick(nowMs: Long = System.currentTimeMillis()): LocationSample? {
        ticks += 1
        if (ticks < SAMPLE_TICKS) return null
        ticks = 0
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) return null
        val next = SkyblockLocationParser.parse(McCompat.tabLines(mc), DungeonListener.sidebarLines())
        snapshot = next
        return LocationSample(next, tracker.update(next, nowMs))
    }

    /** Handles "/icantpy where"; returns the local chat reply, or null when the command is not this one. */
    fun handleCommand(raw: String): String? {
        val body = IcantpyCommandPrefix.body(raw)?.trim()?.lowercase() ?: return null
        if (body != "where") return null
        return snapshot.summary()
    }

    fun reset() {
        ticks = 0
        snapshot = LocationSnapshot.EMPTY
        tracker.reset()
    }
}
