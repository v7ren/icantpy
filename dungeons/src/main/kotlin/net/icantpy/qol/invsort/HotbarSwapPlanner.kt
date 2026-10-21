package net.icantpy.qol.invsort

/**
 * One container click: SWAP [slot] with hotbar index [hotbarIndex] (0..7).
 * [slot] is an InventoryMenu index: main inventory 9..35, hotbar 36..44.
 */
data class HotbarSwap(val slot: Int, val hotbarIndex: Int)

/**
 * Plans hotbar number-key swaps that move items into their target hotbar slots.
 * Pure logic: no Minecraft types. Slot keys are opaque item identities.
 */
object HotbarSwapPlanner {
    const val MAIN_FIRST = 9
    const val MAIN_LAST = 35
    const val HOTBAR_FIRST = 36

    /** Hotbar slot 44 (hotbar index 8) is the SkyBlock menu star and is never touched. */
    const val HOTBAR_LAST_SWAPPABLE = 43

    /**
     * Returns the swaps that bring the hotbar in line with [target]. Hotbar slots are fixed in
     * ascending order; each mismatch is fixed with one swap from a source that is not already settled.
     * Target keys that are not present in any source are skipped.
     */
    fun plan(current: Map<Int, String?>, target: Map<Int, String>): List<HotbarSwap> {
        val state = HashMap<Int, String?>()
        for (slot in MAIN_FIRST..HOTBAR_LAST_SWAPPABLE) state[slot] = current[slot]
        val swaps = mutableListOf<HotbarSwap>()
        for (hotbarSlot in HOTBAR_FIRST..HOTBAR_LAST_SWAPPABLE) {
            val wanted = target[hotbarSlot] ?: continue
            if (state[hotbarSlot] == wanted) continue
            val source = findSource(state, target, wanted, hotbarSlot) ?: continue
            swapContents(state, source, hotbarSlot)
            swaps += HotbarSwap(source, hotbarSlot - HOTBAR_FIRST)
        }
        return swaps
    }

    /** Applies [swaps] to [current] and returns the resulting slot contents for slots 9..44. */
    fun simulate(current: Map<Int, String?>, swaps: List<HotbarSwap>): Map<Int, String?> {
        val state = HashMap<Int, String?>()
        for (slot in MAIN_FIRST..44) state[slot] = current[slot]
        for (swap in swaps) swapContents(state, swap.slot, HOTBAR_FIRST + swap.hotbarIndex)
        return state
    }

    /** Number of swap clicks still needed to satisfy [target]. */
    fun stepsRemaining(current: Map<Int, String?>, target: Map<Int, String>): Int =
        plan(current, target).size

    /**
     * Prefers main inventory, then other hotbar slots. A hotbar source must not already hold its
     * own target, since moving it would undo a correct slot.
     */
    private fun findSource(
        state: Map<Int, String?>,
        target: Map<Int, String>,
        wanted: String,
        destination: Int,
    ): Int? {
        for (slot in MAIN_FIRST..MAIN_LAST) {
            if (state[slot] == wanted) return slot
        }
        for (slot in HOTBAR_FIRST..HOTBAR_LAST_SWAPPABLE) {
            if (slot == destination || state[slot] != wanted) continue
            if (target[slot] == state[slot]) continue
            return slot
        }
        return null
    }

    private fun swapContents(state: MutableMap<Int, String?>, a: Int, b: Int) {
        val held = state[a]
        state[a] = state[b]
        state[b] = held
    }
}
