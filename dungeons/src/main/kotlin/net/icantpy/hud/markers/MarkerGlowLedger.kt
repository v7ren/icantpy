package net.icantpy.hud.markers

/**
 * Bookkeeping for the client-side glow outline. Remembers each entity's glowing flag from before we touched it,
 * re-applies the flag every frame while a marker wants it, and restores the original flag once no marker does.
 * Pure: the caller supplies the apply callback, so tests can observe calls without Minecraft.
 */
class MarkerGlowLedger {
    private val original = LinkedHashMap<Int, Boolean>()

    val glowingIds: Set<Int> get() = original.keys.toSet()

    /**
     * [wanted] maps entity id to its current glowing flag, for every entity a marker wants glowing this frame.
     * Entities newly wanted record their current flag first. Entities no longer wanted are restored to the
     * recorded flag. Every wanted entity gets `apply(id, true)`; restored ones get `apply(id, original)`.
     */
    fun reconcile(wanted: Map<Int, Boolean>, apply: (id: Int, glowing: Boolean) -> Unit) {
        for ((id, current) in wanted) {
            if (id !in original) original[id] = current
            apply(id, true)
        }
        val released = original.keys.filter { it !in wanted }
        for (id in released) {
            val restore = original.remove(id) ?: continue
            apply(id, restore)
        }
    }

    /** Restores every entity we changed and forgets them. Used on unload and when the world changes. */
    fun releaseAll(apply: (id: Int, glowing: Boolean) -> Unit) {
        reconcile(emptyMap(), apply)
    }
}
