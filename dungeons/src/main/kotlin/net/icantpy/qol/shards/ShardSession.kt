package net.icantpy.qol.shards

/** Session-only observations. A missing observation is deliberately never represented as zero. */
class ShardSession {
    private var observations: Map<String, ShardProgress> = emptyMap()
    private var profile: String? = null

    fun merge(observation: ShardObservation) {
        val key = ShardChecklistCodec.normalizeName(observation.name)
        val old = observations[key]
        val level = observation.level ?: old?.level
        val syphoned = when {
            observation.syphoned != null -> observation.syphoned
            observation.level != null && observation.level != old?.level -> null
            else -> old?.syphoned
        }
        observations = observations + (key to ShardProgress(level, observation.owned ?: old?.owned, syphoned))
    }

    fun get(name: String): ShardProgress? = observations[ShardChecklistCodec.normalizeName(name)]
    fun snapshot(): Map<String, ShardProgress> = observations.toMap()
    fun clear() {
        observations = emptyMap()
        profile = null
    }

    fun resetObservations() {
        observations = emptyMap()
    }

    fun shouldResetForChat(message: String): Boolean {
        val normalized = message.replace(Regex("§."), "").trim().lowercase()
        val marker = when {
            normalized.startsWith("you are playing on profile:") -> "you are playing on profile:"
            normalized.startsWith("your profile was changed to:") -> "your profile was changed to:"
            normalized.startsWith("switched to profile") -> "switched to profile"
            else -> return false
        }
        val current = normalized.removePrefix(marker).trim()
        if (current.isEmpty() || current == profile) return false
        profile = current
        observations = emptyMap()
        return true
    }
}
