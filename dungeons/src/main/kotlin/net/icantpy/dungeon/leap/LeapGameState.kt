package net.icantpy.dungeon.leap

import net.icantpy.dungeon.DungeonFloor
import net.icantpy.dungeon.DungeonListener
import net.icantpy.dungeon.timer.TickTimers
import net.minecraft.client.Minecraft

enum class LeapBossPhase {
    UNKNOWN,
    MAXOR,
    STORM,
    GOLDOR,
    NECRON,
}

object LeapGameState {
    var debugOverride: String? = null

    fun currentId(): String = debugOverride?.lowercase() ?: snapshotId()

    fun snapshotId(): String {
        val floor = DungeonListener.floor
        val floorKey = when (floor) {
            DungeonFloor.M7 -> "m7"
            DungeonFloor.F7 -> "f7"
            else -> if (floor == DungeonFloor.NONE) "m7" else floor.name.lowercase()
        }
        val section = goldorSection()
        return when (leapPhase()) {
            LeapPhase.P1 -> "${floorKey}p1"
            LeapPhase.P2 -> "${floorKey}p2"
            LeapPhase.P3 -> if (section in 1..4) "${floorKey}p3s$section" else "${floorKey}p3"
            LeapPhase.P4 -> "${floorKey}p4"
            LeapPhase.P5 -> "${floorKey}p5"
            else -> "unknown"
        }
    }

    fun leapFloor(): LeapFloor = LeapFloor.fromDungeon(DungeonListener.floor)

    fun leapPhase(): LeapPhase {
        debugOverride?.let { id ->
            return LeapOrientEngine.contextFromStateId(id).phase
        }
        if (DungeonListener.state.inBoss && LeapOrient.bossTracker.phase != LeapPhase.UNKNOWN) {
            return LeapOrient.bossTracker.phase
        }
        if (DungeonListener.floor == DungeonFloor.M7 &&
            playerY() <= 45.0 &&
            TickTimers.clocks.necron < 0 &&
            DungeonListener.state.inBoss &&
            phase() != LeapBossPhase.GOLDOR &&
            phase() != LeapBossPhase.STORM
        ) {
            return LeapPhase.P5
        }
        return when (phase()) {
            LeapBossPhase.MAXOR -> LeapPhase.P1
            LeapBossPhase.STORM -> LeapPhase.P2
            LeapBossPhase.GOLDOR -> LeapPhase.P3
            LeapBossPhase.NECRON -> LeapPhase.P4
            LeapBossPhase.UNKNOWN -> LeapPhase.UNKNOWN
        }
    }

    fun leapSection(): Int = goldorSection()

    private fun playerY(): Double = Minecraft.getInstance().player?.y ?: 200.0

    fun phase(): LeapBossPhase {
        val clocks = TickTimers.clocks
        return when {
            clocks.necron >= 0 -> LeapBossPhase.NECRON
            clocks.goldorTick >= 0 || clocks.goldorStart >= 0 -> LeapBossPhase.GOLDOR
            clocks.stormTick >= 0 || clocks.pad >= 0 || clocks.lightning >= 0 || clocks.py >= 0 -> LeapBossPhase.STORM
            DungeonListener.state.inBoss -> LeapBossPhase.MAXOR
            else -> LeapBossPhase.UNKNOWN
        }
    }

    fun goldorSection(): Int {
        val player = Minecraft.getInstance().player ?: return LeapOrientState.debugSelfSpot?.p3Section ?: 0
        return LeapOrientSpots.selfSection(player.x, player.y, player.z, LeapOrientState.debugSelfSpot)
    }

    fun simulate(token: String): String {
        val parsed = parse(token) ?: return "unknown state: $token"
        debugOverride = parsed
        return "game state -> $parsed"
    }

    fun clearSimulate() {
        debugOverride = null
    }

    fun parse(raw: String): String? {
        val token = raw.lowercase().replace(" ", "")
        return when (token) {
            "clear", "off", "none" -> null
            "p1", "maxor", "m7p1", "f7p1" -> "m7p1"
            "p2", "storm", "m7p2", "f7p2" -> "m7p2"
            "p3", "goldor", "m7p3", "f7p3" -> "m7p3"
            "s1", "sss", "ss", "p3s1", "m7p3s1", "f7p3s1" -> "m7p3s1"
            "s2", "p3s2", "m7p3s2", "f7p3s2", "ee2" -> "m7p3s2"
            "s3", "p3s3", "m7p3s3", "f7p3s3", "ee3" -> "m7p3s3"
            "s4", "p3s4", "m7p3s4", "f7p3s4" -> "m7p3s4"
            "core" -> "m7p3s4"
            "p4", "necron", "m7p4", "f7p4" -> "m7p4"
            "p5", "m7p5" -> "m7p5"
            else -> if (token.matches(Regex("""[fm]7p[1-5](?:s[1-4])?"""))) token else null
        }
    }

    fun matchesWhen(currentId: String, whenState: String): Boolean {
        val want = whenState.lowercase().trim()
        if (want.isBlank() || want == "any") return true
        val id = currentId.lowercase()
        val parsedWant = parse(want) ?: want
        val parsedId = parse(id) ?: id
        if (parsedId == parsedWant || id == want || id.contains(want)) return true
        return when (want) {
            "p2", "storm" -> parsedId.contains("p2")
            "p3", "goldor" -> parsedId.contains("p3")
            "p4", "necron" -> parsedId.contains("p4")
            "p1", "maxor" -> parsedId.contains("p1")
            else -> false
        }
    }
}
