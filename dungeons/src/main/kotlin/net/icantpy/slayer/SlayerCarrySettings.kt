package net.icantpy.slayer

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** One carried player with their own progress toward an optional target. */
data class CarriedPlayer(
    val name: String = "",
    val count: Int = 0,
    val target: Int = 0,
) {
    val hasTarget: Boolean get() = target > 0

    fun adjusted(delta: Int): CarriedPlayer = withCount(count + delta)

    fun withCount(value: Int): CarriedPlayer = copy(count = value.coerceIn(0, MAX_COUNT))

    fun withTarget(value: Int): CarriedPlayer = copy(target = value.coerceIn(0, MAX_COUNT))

    fun reachedTarget(): Boolean = hasTarget && count >= target

    /** "12/20" with a target, otherwise just "12". */
    fun progress(): String = if (hasTarget) "$count/$target" else "$count"

    companion object {
        const val MAX_COUNT: Int = 100_000
    }
}

/**
 * Persistent state for the slayer carrier counter. Tracks a list of carried players,
 * each with an independent count and target, plus the captured +1/-1 keys.
 */
data class SlayerCarrySettings(
    val enabled: Boolean = false,
    val autoCount: Boolean = true,
    val notifyOnTarget: Boolean = true,
    val plusKey: Int = UNBOUND,
    val minusKey: Int = UNBOUND,
    val cycleNextKey: Int = UNBOUND,
    val cyclePrevKey: Int = UNBOUND,
    val selected: String? = null,
    val players: List<CarriedPlayer> = emptyList(),
) {
    fun player(name: String?): CarriedPlayer? {
        if (name.isNullOrBlank()) return null
        val trimmed = name.trim()
        return players.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    }

    fun selectedPlayer(): CarriedPlayer? = player(selected) ?: players.firstOrNull()

    /** Case-insensitive membership test; blank or null names never match. */
    fun isCarried(name: String?): Boolean = player(name) != null

    fun addPlayer(rawName: String, target: Int = 0): SlayerCarrySettings {
        val name = rawName.trim()
        if (name.isEmpty()) return this
        val existing = player(name)
        if (existing != null) {
            return copy(
                players = players.map { if (it === existing) it.withTarget(target) else it },
                selected = existing.name,
            )
        }
        return copy(
            players = players + CarriedPlayer(name, 0, target.coerceAtLeast(0)),
            selected = name,
        )
    }

    fun removePlayer(rawName: String): SlayerCarrySettings {
        val existing = player(rawName) ?: return this
        val next = players.filterNot { it === existing }
        val nextSelected = if (selected.equals(existing.name, ignoreCase = true)) {
            next.firstOrNull()?.name
        } else {
            selected
        }
        return copy(players = next, selected = nextSelected)
    }

    fun clearPlayers(): SlayerCarrySettings = copy(players = emptyList(), selected = null)

    fun select(name: String?): SlayerCarrySettings {
        val existing = player(name) ?: return this
        return copy(selected = existing.name)
    }

    /** Moves the selection by [delta] positions, wrapping around the player list. */
    fun cycleSelection(delta: Int): SlayerCarrySettings {
        if (players.isEmpty() || delta == 0) return this
        val index = players.indexOfFirst { it.name.equals(selected, ignoreCase = true) }
        val current = if (index >= 0) index else 0
        val size = players.size
        val next = ((current + delta) % size + size) % size
        return copy(selected = players[next].name)
    }

    fun updatePlayer(name: String?, transform: (CarriedPlayer) -> CarriedPlayer): SlayerCarrySettings {
        val existing = player(name) ?: return this
        return copy(players = players.map { if (it === existing) transform(it) else it })
    }

    fun adjustPlayer(name: String?, delta: Int): SlayerCarrySettings = updatePlayer(name) { it.adjusted(delta) }

    fun toJson(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("enabled", enabled)
        obj.addProperty("autoCount", autoCount)
        obj.addProperty("notifyOnTarget", notifyOnTarget)
        obj.addProperty("plusKey", plusKey)
        obj.addProperty("minusKey", minusKey)
        obj.addProperty("cycleNextKey", cycleNextKey)
        obj.addProperty("cyclePrevKey", cyclePrevKey)
        selected?.let { obj.addProperty("selected", it) }
        val array = JsonArray()
        players.forEach { player ->
            val item = JsonObject()
            item.addProperty("name", player.name)
            item.addProperty("count", player.count)
            item.addProperty("target", player.target)
            array.add(item)
        }
        obj.add("players", array)
        return obj
    }

    companion object {
        /** Matches Minecraft's GLFW_KEY_UNKNOWN used by the other captured binds. */
        const val UNBOUND: Int = -1

        fun fromJson(obj: JsonObject): SlayerCarrySettings {
            val defaults = SlayerCarrySettings()
            val players = players(obj)
            val selected = string(obj, "selected")?.takeIf { name -> players.any { it.name.equals(name, true) } }
                ?: players.firstOrNull()?.name
            return SlayerCarrySettings(
                enabled = bool(obj, "enabled", defaults.enabled),
                autoCount = bool(obj, "autoCount", defaults.autoCount),
                notifyOnTarget = bool(obj, "notifyOnTarget", defaults.notifyOnTarget),
                plusKey = int(obj, "plusKey", defaults.plusKey),
                minusKey = int(obj, "minusKey", defaults.minusKey),
                cycleNextKey = int(obj, "cycleNextKey", defaults.cycleNextKey),
                cyclePrevKey = int(obj, "cyclePrevKey", defaults.cyclePrevKey),
                selected = selected,
                players = players,
            )
        }

        private fun players(obj: JsonObject): List<CarriedPlayer> {
            if (obj.has("players") && obj.get("players").isJsonArray) {
                val result = LinkedHashMap<String, CarriedPlayer>()
                obj.getAsJsonArray("players").forEach { element ->
                    val item = runCatching { element.asJsonObject }.getOrNull() ?: return@forEach
                    val name = string(item, "name")?.trim() ?: return@forEach
                    if (name.isEmpty()) return@forEach
                    result.putIfAbsent(
                        name.lowercase(),
                        CarriedPlayer(
                            name = name,
                            count = int(item, "count", 0).coerceIn(0, CarriedPlayer.MAX_COUNT),
                            target = int(item, "target", 0).coerceIn(0, CarriedPlayer.MAX_COUNT),
                        ),
                    )
                }
                return result.values.toList()
            }
            return legacyPlayers(obj)
        }

        /** Migrates the 1.0.3.72 shape: carriedPlayers names plus one global count. */
        private fun legacyPlayers(obj: JsonObject): List<CarriedPlayer> {
            if (!obj.has("carriedPlayers") || !obj.get("carriedPlayers").isJsonArray) return emptyList()
            val legacyCount = int(obj, "count", 0).coerceIn(0, CarriedPlayer.MAX_COUNT)
            val result = LinkedHashMap<String, CarriedPlayer>()
            obj.getAsJsonArray("carriedPlayers").forEachIndexed { index, element ->
                val name = runCatching { element.asString.trim() }.getOrNull() ?: return@forEachIndexed
                if (name.isEmpty()) return@forEachIndexed
                result.putIfAbsent(
                    name.lowercase(),
                    CarriedPlayer(name = name, count = if (index == 0) legacyCount else 0),
                )
            }
            return result.values.toList()
        }

        private fun string(obj: JsonObject, key: String): String? =
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asString else null

        private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean =
            if (obj.has(key)) obj.get(key).asBoolean else default

        private fun int(obj: JsonObject, key: String, default: Int): Int =
            if (obj.has(key)) obj.get(key).asInt else default
    }
}
