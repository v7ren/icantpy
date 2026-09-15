package net.icantpy.modules.impl.dungeon.leaporient

data class DebugMember(
    val name: String,
    val clazz: LeapDungeonClass,
    val spot: OrientSpot,
)

object DebugParty {
    val members: MutableList<DebugMember> = mutableListOf(
        DebugMember("dbgArcher", LeapDungeonClass.ARCHER, OrientSpot.EE2),
        DebugMember("dbgBers", LeapDungeonClass.BERSERK, OrientSpot.GOLDOR_S4),
        DebugMember("dbgHealer", LeapDungeonClass.HEALER, OrientSpot.GOLDOR_S1),
        DebugMember("dbgTank", LeapDungeonClass.TANK, OrientSpot.GOLDOR_S3),
        DebugMember("dbgMage", LeapDungeonClass.MAGE, OrientSpot.GOLDOR_S2),
    )

    fun leapPlayers(
        excludeName: String? = null,
        excludeClass: LeapDungeonClass = LeapDungeonClass.EMPTY,
    ): List<LeapPlayer> {
        val others = members
            .filter { member ->
                !member.name.equals(excludeName, ignoreCase = true) &&
                    !(excludeClass.isReal() && member.clazz == excludeClass)
            }
            .map { LeapPlayer(it.name, it.clazz) }
        return LeapMenuSort.odinSorting(others)
    }

    fun seedSenderSpots() {
        LeapOrientState.debugSenderSpots.clear()
        members.forEach { member ->
            LeapOrientState.debugSenderSpots[member.name.lowercase()] = member.spot
        }
    }

    fun parseTeammates(tokens: List<String>): String {
        if (tokens.isEmpty()) {
            return members.joinToString { "${it.name}:${it.clazz.name.lowercase()}" }
        }
        members.clear()
        tokens.forEach { token ->
            val name = token.substringBefore(':')
            val clazz = LeapDungeonClass.fromToken(token.substringAfter(':', "mage")) ?: LeapDungeonClass.MAGE
            val spot = when (clazz) {
                LeapDungeonClass.ARCHER -> OrientSpot.EE2
                LeapDungeonClass.BERSERK -> OrientSpot.GOLDOR_S4
                LeapDungeonClass.HEALER -> OrientSpot.GOLDOR_S1
                LeapDungeonClass.TANK -> OrientSpot.GOLDOR_S3
                LeapDungeonClass.MAGE -> OrientSpot.GOLDOR_S2
                LeapDungeonClass.EMPTY -> OrientSpot.ANY
            }
            members += DebugMember(name, clazz, spot)
        }
        if (members.size < 5) {
            val defaults = listOf(
                DebugMember("dbgArcher", LeapDungeonClass.ARCHER, OrientSpot.EE2),
                DebugMember("dbgBers", LeapDungeonClass.BERSERK, OrientSpot.GOLDOR_S4),
                DebugMember("dbgHealer", LeapDungeonClass.HEALER, OrientSpot.GOLDOR_S1),
                DebugMember("dbgTank", LeapDungeonClass.TANK, OrientSpot.GOLDOR_S3),
                DebugMember("dbgMage", LeapDungeonClass.MAGE, OrientSpot.GOLDOR_S2),
            )
            for (fallback in defaults) {
                if (members.none { it.name.equals(fallback.name, ignoreCase = true) }) {
                    members += fallback
                }
                if (members.size >= 5) break
            }
        }
        seedSenderSpots()
        return members.joinToString { "${it.name}:${it.clazz.name.lowercase()}" }
    }
}
