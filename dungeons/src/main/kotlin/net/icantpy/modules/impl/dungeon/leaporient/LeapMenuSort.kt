package net.icantpy.modules.impl.dungeon.leaporient

object LeapMenuSort {
    private val empty = LeapPlayer("Empty", LeapDungeonClass.EMPTY)

    fun sort(players: List<LeapPlayer>, mode: LeapSortMode): List<LeapPlayer> {
        val living = players.filter { it.clazz != LeapDungeonClass.EMPTY }
        return when (mode) {
            LeapSortMode.ODIN -> odinSorting(living)
            LeapSortMode.CLASS -> pad(living.sortedBy { it.clazz.displayName })
            LeapSortMode.NAME -> pad(living.sortedBy { it.name.lowercase() })
            LeapSortMode.NONE -> pad(living)
        }
    }

    fun odinSorting(players: List<LeapPlayer>): List<LeapPlayer> {
        val result = Array(4) { empty }
        val secondRound = ArrayList<LeapPlayer>()
        for (player in players.sortedBy { it.clazz.priority }) {
            when {
                result[player.clazz.defaultQuadrant].clazz == LeapDungeonClass.EMPTY ->
                    result[player.clazz.defaultQuadrant] = player
                else -> secondRound.add(player)
            }
        }
        if (secondRound.isEmpty()) return result.toList()
        for (index in result.indices) {
            if (result[index].clazz == LeapDungeonClass.EMPTY && secondRound.isNotEmpty()) {
                result[index] = secondRound.removeAt(0)
            }
        }
        return result.toList()
    }

    private fun pad(players: List<LeapPlayer>): List<LeapPlayer> {
        if (players.size >= 4) return players.take(4)
        return players + List(4 - players.size) { empty }
    }
}
