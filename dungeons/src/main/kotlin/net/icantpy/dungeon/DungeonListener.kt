package net.icantpy.dungeon

import net.icantpy.dungeon.timer.TickTimers
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam

object DungeonListener {
    private const val LEAVE_GRACE_TICKS = 40
    private val mc: Minecraft get() = Minecraft.getInstance()
    private var missTicks = 0

    var state: DungeonState = DungeonState()
        private set

    val floor: DungeonFloor
        get() = state.floor

    fun markBoss() {
        state = state.copy(inBoss = true, inDungeon = true)
    }

    fun sidebarLines(): List<String> = sidebar()

    internal fun shouldKeepDebugState(debug: Boolean, lines: List<String>): Boolean =
        debug && !ChatClocks.inDungeon(lines) && ChatClocks.floorFromLines(lines) == DungeonFloor.NONE

    fun tick() {
        val lines = sidebar()
        if (shouldKeepDebugState(TickTimers.settings.debugMode, lines)) {
            missTicks = 0
            return
        }
        val player = mc.player
        val level = mc.level
        if (player == null || level == null) {
            if (state.inDungeon && !TickTimers.active) reset()
            return
        }
        val parsedFloor = ChatClocks.floorFromLines(lines)
        val floor = if (parsedFloor != DungeonFloor.NONE) parsedFloor else state.floor
        val inBossBox = BossRooms.contains(floor, player.x, player.z)
        val onBoard = ChatClocks.inDungeon(lines)
        val elsewhere = ChatClocks.elsewhere(lines)
        if (elsewhere && !onBoard && !inBossBox) {
            missTicks += 1
            if (missTicks >= LEAVE_GRACE_TICKS) reset()
            return
        }
        if (onBoard || inBossBox || TickTimers.bossActive || state.inBoss) {
            missTicks = 0
            state = state.copy(
                inDungeon = true,
                inBoss = state.inBoss || inBossBox || TickTimers.bossActive,
                floor = floor,
                percentCleared = ChatClocks.percentCleared(lines) ?: state.percentCleared,
            )
            return
        }
        if (state.inDungeon) {
            missTicks += 1
            if (missTicks >= LEAVE_GRACE_TICKS) {
                reset()
            }
        }
    }

    fun reset() {
        missTicks = 0
        state = DungeonState()
        TickTimers.reset()
    }

    private fun sidebar(): List<String> {
        val lines = ArrayList<String>()
        val level = mc.level ?: return lines
        val scoreboard = level.scoreboard
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
        if (objective != null) {
            lines += objective.displayName.string
            scoreboard.listPlayerScores(objective)
                .filter { !it.isHidden }
                .forEach { entry ->
                    lines += PlayerTeam.formatNameForTeam(
                        scoreboard.getPlayersTeam(entry.owner()),
                        entry.ownerName(),
                    ).string
                }
        }
        return lines
    }
}
