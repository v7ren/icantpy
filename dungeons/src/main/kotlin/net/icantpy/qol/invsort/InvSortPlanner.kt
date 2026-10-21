package net.icantpy.qol.invsort

/**
 * Pure sorting logic. Produces left-click (PICKUP) sequences over the preset slots so that the
 * inventory ends up holding the target keys. Stacks are identified by key only.
 *
 * Armor targets are planned first. An armor slot only ever receives the key recorded for that same
 * slot, and its source is always an inventory slot. Worn armor is never a source, so a piece the
 * preset does not mention stays on the player. Inventory targets are planned afterwards.
 */
object InvSortPlanner {
    /**
     * @param current key per preset slot (null when empty)
     * @param target desired key per slot; slots outside [InvSortSlots.PRESET_SLOTS] are ignored
     * @return ordered menu slot indices to click
     */
    fun plan(current: Map<Int, String?>, target: Map<Int, String>): List<Int> {
        val wanted = target.filterKeys { InvSortSlots.isPresetSlot(it) }
        val state = InvSortSlots.PRESET_SLOTS.associateWith { current[it] }.toMutableMap()
        val clicks = mutableListOf<Int>()
        planArmor(state, clicks, wanted)
        planInventory(state, clicks, wanted)
        return clicks
    }

    /** Applies PICKUP clicks to a copy of [current] and returns the resulting key per slot. */
    fun simulate(current: Map<Int, String?>, clicks: List<Int>): Map<Int, String?> {
        var slots = current
        var carried: String? = null
        for (slot in clicks) {
            val result = applyPickup(slots, carried, slot)
            slots = result.slots
            carried = result.carried
        }
        return slots
    }

    /** Slot keys and the key held on the cursor after one click. */
    data class PickupResult(val slots: Map<Int, String?>, val carried: String?)

    /** Applies a single vanilla left click (PICKUP) on [slot] to the given state. */
    fun applyPickup(slots: Map<Int, String?>, carried: String?, slot: Int): PickupResult {
        val onSlot = slots[slot]
        return when {
            carried == null -> PickupResult(slots + (slot to null), onSlot)
            onSlot == null || onSlot == carried -> PickupResult(slots + (slot to carried), null)
            else -> PickupResult(slots + (slot to carried), onSlot)
        }
    }

    private fun planArmor(state: MutableMap<Int, String?>, clicks: MutableList<Int>, wanted: Map<Int, String>) {
        for (armor in InvSortSlots.ARMOR) {
            val key = wanted[armor] ?: continue
            if (state[armor] == key) continue
            val source = findInventorySource(state, wanted, key) ?: continue
            moveInto(state, clicks, source, armor)
        }
    }

    private fun planInventory(state: MutableMap<Int, String?>, clicks: MutableList<Int>, wanted: Map<Int, String>) {
        val finalized = mutableSetOf<Int>()
        for (slot in wanted.keys.filter { InvSortSlots.isSortable(it) }.sorted()) {
            val key = wanted.getValue(slot)
            if (state[slot] == key) {
                finalized += slot
                continue
            }
            val source = findInventorySource(state, wanted, key, destination = slot, finalized = finalized) ?: continue
            moveInto(state, clicks, source, slot)
            finalized += slot
        }
    }

    /**
     * Inventory slot holding [key] that its own target does not already use. Slots no target
     * mentions are preferred so that a stack is not taken from a slot another target still needs.
     */
    private fun findInventorySource(
        state: Map<Int, String?>,
        wanted: Map<Int, String>,
        key: String,
        destination: Int? = null,
        finalized: Set<Int> = emptySet(),
    ): Int? = InvSortSlots.SORTABLE
        .filter { candidate ->
            candidate != destination &&
                candidate !in finalized &&
                state[candidate] == key &&
                !isSatisfied(state, wanted, candidate)
        }
        .minByOrNull { candidate -> if (wanted[candidate] == null) 0 else 1 }

    private fun isSatisfied(state: Map<Int, String?>, wanted: Map<Int, String>, slot: Int): Boolean =
        wanted[slot] != null && state[slot] == wanted[slot]

    private fun moveInto(state: MutableMap<Int, String?>, clicks: MutableList<Int>, source: Int, destination: Int) {
        val destinationKey = state[destination]
        if (destinationKey == null) {
            clicks += listOf(source, destination)
            state[destination] = state[source]
            state[source] = null
        } else {
            clicks += listOf(source, destination, source)
            state[destination] = state[source]
            state[source] = destinationKey
        }
    }
}
