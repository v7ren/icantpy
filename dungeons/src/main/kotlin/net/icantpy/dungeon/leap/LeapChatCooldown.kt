package net.icantpy.dungeon.leap

/** Per-card cooldown for chat-message triggers. Pure, so it can be tested without Minecraft. */
class LeapChatCooldown {
    private val lastFiredAt = HashMap<String, Long>()

    /** True when [id] may fire at [now]; a true result starts its cooldown of [cooldownMs]. */
    fun ready(id: String, now: Long, cooldownMs: Int): Boolean {
        val last = lastFiredAt[id]
        if (last != null && now - last < cooldownMs) return false
        lastFiredAt[id] = now
        return true
    }

    fun clear() = lastFiredAt.clear()

    companion object {
        /** Choices offered by the card's Cooldown row, in milliseconds. */
        val CHOICES_MS: List<Int> = listOf(500, 1000, 2000, 5000, 10000)
    }
}
