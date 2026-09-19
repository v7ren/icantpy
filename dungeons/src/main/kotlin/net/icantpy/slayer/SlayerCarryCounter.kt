package net.icantpy.slayer

import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.compat.McCompat
import net.icantpy.dungeon.timer.AlertSounds
import net.icantpy.dungeon.timer.TickTimers
import net.icantpy.util.Text
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import org.lwjgl.glfw.GLFW

/**
 * Slayer carrier counter. Tracks a list of carried players, each with an independent
 * count and optional target. When an owned slayer boss dies near the local player the
 * owner is read from the boss's "Spawned by" hologram and that player's count is
 * incremented; hitting a target shows a notification.
 */
object SlayerCarryCounter {
    private const val SCAN_INTERVAL_TICKS = 4
    private const val OWNER_TAG_RANGE = 6.0
    private const val COUNT_RANGE = 30.0
    private const val NEAR_RANGE = 24.0
    private const val COUNTED_LIMIT = 512
    private const val NOTIFY_TICKS = 100
    private val OWNER_TAG = Regex("Spawned by:\\s*([A-Za-z0-9_]+)")

    private data class TrackedBoss(val owner: String, var distanceSq: Double)

    private val tracked = HashMap<Int, TrackedBoss>()
    private val counted = HashSet<Int>()
    private var scanClock = 0

    fun reset() {
        tracked.clear()
        counted.clear()
        scanClock = 0
    }

    fun tick() {
        val settings = TickTimers.settings.slayer
        if (!settings.enabled || !settings.autoCount || settings.players.isEmpty()) {
            reset()
            return
        }
        scanClock += 1
        if (scanClock % SCAN_INTERVAL_TICKS != 0) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return

        // Boss holograms carry the owner; the nearest living mob under a hologram is the boss.
        val ownerStands = ArrayList<Pair<Entity, String>>()
        val mobs = ArrayList<LivingEntity>()
        for (entity in level.entitiesForRendering()) {
            when {
                entity is ArmorStand -> ownerFromTag(entity.name.string)?.let { ownerStands += entity to it }
                entity is LivingEntity && entity !is Player && entity.isAlive -> mobs += entity
            }
        }
        val ownerRangeSq = OWNER_TAG_RANGE * OWNER_TAG_RANGE
        for ((stand, owner) in ownerStands) {
            val boss = mobs.minByOrNull { it.distanceToSqr(stand) } ?: continue
            if (boss.distanceToSqr(stand) > ownerRangeSq) continue
            tracked[boss.id] = TrackedBoss(owner, boss.distanceToSqr(player))
        }

        val countRangeSq = COUNT_RANGE * COUNT_RANGE
        val nearRangeSq = NEAR_RANGE * NEAR_RANGE
        val iterator = tracked.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val entity = level.getEntity(entry.key) as? LivingEntity
            if (entity != null) {
                entry.value.distanceSq = entity.distanceToSqr(player)
                if (entity.isDeadOrDying && entry.key !in counted) {
                    counted += entry.key
                    if (entry.value.distanceSq <= countRangeSq) countBoss(entry.value.owner)
                }
                continue
            }
            // The entity is gone. A near disappearance is a kill; a distant one is an unload.
            if (entry.key !in counted && entry.value.distanceSq <= nearRangeSq) {
                counted += entry.key
                countBoss(entry.value.owner)
            }
            iterator.remove()
        }
        if (counted.size > COUNTED_LIMIT) counted.clear()
    }

    /** Handles the captured +1/-1 keys from the raw keyboard event. */
    fun handleKey(event: KeyEvent, action: Int): Boolean {
        val settings = TickTimers.settings.slayer
        if (!settings.enabled) return false
        if (action == GLFW.GLFW_RELEASE) return false
        val mc = Minecraft.getInstance()
        if (mc.player == null) return false
        if (McCompat.currentScreen(mc) != null) return false
        val key = event.key()
        return when {
            settings.plusKey != SlayerCarrySettings.UNBOUND && key == settings.plusKey -> {
                tell(adjustSelected(1))
                true
            }
            settings.minusKey != SlayerCarrySettings.UNBOUND && key == settings.minusKey -> {
                tell(adjustSelected(-1))
                true
            }
            settings.cycleNextKey != SlayerCarrySettings.UNBOUND && key == settings.cycleNextKey -> {
                tell(cycleSelected(1))
                true
            }
            settings.cyclePrevKey != SlayerCarrySettings.UNBOUND && key == settings.cyclePrevKey -> {
                tell(cycleSelected(-1))
                true
            }
            else -> false
        }
    }

    fun handleCommand(raw: String): String? {
        val body = IcantpyCommandPrefix.body(raw) ?: return null
        val lower = body.lowercase()
        val isCarry = lower == "carry" || lower.startsWith("carry ") ||
            lower == "carries" || lower.startsWith("carries ")
        if (!isCarry) return null
        return execute(body.substringAfter(' ', "").trim())
    }

    /** Adjusts the selected player, used by the +1/-1 keys and commands. */
    fun adjustSelected(delta: Int): String {
        val player = TickTimers.settings.slayer.selectedPlayer() ?: return "no carried player selected"
        return adjustPlayer(player.name, delta)
    }

    /** Adjusts one named player, used by the per-player GUI buttons. */
    fun adjustPlayer(name: String, delta: Int): String {
        val settings = TickTimers.settings.slayer
        val player = settings.player(name) ?: return "unknown player: $name"
        val before = player.count
        TickTimers.updateSlayer { it.adjustPlayer(player.name, delta) }
        val after = TickTimers.settings.slayer.player(player.name) ?: return status()
        return completeOrProgress(before, after)
    }

    /** Cycles the selection by [delta] players and reports the new selection. */
    fun cycleSelected(delta: Int): String {
        TickTimers.updateSlayer { it.cycleSelection(delta) }
        val player = TickTimers.settings.slayer.selectedPlayer() ?: return "carries: none"
        return "selected §f${player.name} §7(${player.progress()})"
    }

    internal fun ownerFromTag(rawName: String): String? =
        OWNER_TAG.find(Text.strip(rawName))?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun countBoss(owner: String) {
        val settings = TickTimers.settings.slayer
        val player = settings.player(owner) ?: return
        val before = player.count
        TickTimers.updateSlayer { it.adjustPlayer(player.name, 1) }
        val after = TickTimers.settings.slayer.player(player.name) ?: return
        if (before < after.target && after.reachedTarget()) {
            notifyTarget(after)
        } else {
            tell("§aCarry +1 §7(${after.name}) → §f${after.progress()}")
        }
    }

    private fun completeOrProgress(before: Int, after: CarriedPlayer): String {
        if (before < after.target && after.reachedTarget()) {
            notifyTarget(after)
            return status()
        }
        return "§f${after.name}: ${after.progress()}"
    }

    private fun notifyTarget(player: CarriedPlayer) {
        val text = "§a${player.name} §f${player.progress()} §a— carry complete"
        tell(text)
        if (!TickTimers.settings.slayer.notifyOnTarget) return
        TickTimers.pushNotification(text, NOTIFY_TICKS)
        AlertSounds.play(TickTimers.settings.hud.alertSound.ifBlank { "pling" })
    }

    private fun execute(rest: String): String {
        val args = rest.split(' ').filter { it.isNotBlank() }
        if (args.isEmpty()) return status()
        val skipKeyword = args[0].equals("player", true) || args[0].equals("players", true)
        val index = if (skipKeyword) 1 else 0
        val action = args.getOrNull(index)?.lowercase() ?: return status()
        val tail = args.drop(index + 1).joinToString(" ")
        return when {
            action == "list" || action == "info" || action == "status" -> status()
            action == "add" -> addPlayers(tail)
            action == "remove" || action == "del" || action == "delete" -> removePlayers(tail)
            action == "select" -> selectPlayer(tail)
            action == "next" -> cycleSelected(1)
            action == "prev" || action == "previous" -> cycleSelected(-1)
            action == "set" -> setCount(tail)
            action == "target" -> setTarget(tail)
            action == "reset" -> resetCounts(tail)
            action == "clear" -> {
                TickTimers.updateSlayer { it.clearPlayers() }
                "carry players cleared"
            }
            action == "on" -> {
                TickTimers.updateSlayer { it.copy(enabled = true) }
                "carry counter on"
            }
            action == "off" -> {
                TickTimers.updateSlayer { it.copy(enabled = false) }
                "carry counter off"
            }
            action.toIntOrNull() != null -> setCount(action)
            action.startsWith("+") -> adjustSelected(action.drop(1).toIntOrNull() ?: 1)
            action.startsWith("-") -> adjustSelected(-(action.drop(1).toIntOrNull() ?: 1))
            else -> help()
        }
    }

    private fun selectPlayer(raw: String): String {
        val settings = TickTimers.settings.slayer
        val player = settings.player(raw) ?: return "unknown player: ${raw.ifBlank { "?" }}"
        TickTimers.updateSlayer { it.select(player.name) }
        return "selected ${player.name}"
    }

    private fun setCount(raw: String): String {
        val value = raw.trim().toIntOrNull() ?: return "usage: /icantpy carry set <number>"
        val settings = TickTimers.settings.slayer
        val player = settings.selectedPlayer() ?: return "no carried player selected"
        TickTimers.updateSlayer { it.updatePlayer(player.name) { current -> current.withCount(value) } }
        return status()
    }

    private fun setTarget(raw: String): String {
        val value = raw.trim().toIntOrNull() ?: return "usage: /icantpy carry target <number> (0 clears)"
        val settings = TickTimers.settings.slayer
        val player = settings.selectedPlayer() ?: return "no carried player selected"
        TickTimers.updateSlayer { it.updatePlayer(player.name) { current -> current.withTarget(value) } }
        return status()
    }

    private fun resetCounts(raw: String): String {
        val all = raw.trim().equals("all", ignoreCase = true)
        TickTimers.updateSlayer { settings ->
            if (all) settings.copy(players = settings.players.map { it.withCount(0) })
            else {
                val player = settings.selectedPlayer() ?: return@updateSlayer settings
                settings.updatePlayer(player.name) { it.withCount(0) }
            }
        }
        return if (all) "all carry counts reset" else "carry count reset"
    }

    private fun addPlayers(raw: String): String {
        val tokens = splitNames(raw)
        if (tokens.isEmpty()) return "usage: /icantpy carry add <player[, player]> [target]"
        val target = tokens.last().toIntOrNull()
        val names = if (target != null) tokens.dropLast(1) else tokens
        if (names.isEmpty()) return "usage: /icantpy carry add <player[, player]> [target]"
        TickTimers.updateSlayer { settings ->
            names.fold(settings) { acc, name -> acc.addPlayer(name, target ?: 0) }
        }
        return status()
    }

    private fun removePlayers(raw: String): String {
        val names = splitNames(raw)
        if (names.isEmpty()) return "usage: /icantpy carry remove <player[, player]>"
        TickTimers.updateSlayer { settings -> names.fold(settings) { acc, name -> acc.removePlayer(name) } }
        return status()
    }

    private fun splitNames(raw: String): List<String> =
        raw.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    private fun status(): String {
        val settings = TickTimers.settings.slayer
        val auto = if (settings.autoCount) "on" else "off"
        if (settings.players.isEmpty()) return "carries: none · auto $auto"
        val list = settings.players.joinToString(", ") { player ->
            val marker = if (player === settings.selectedPlayer()) "*" else ""
            "$marker${player.name}: ${player.progress()}"
        }
        return "carries · auto $auto · $list"
    }

    private fun help(): String =
        "carry add <player[, player]> [target] | remove <player> | select <player> | +n | -n | set <n> | " +
            "target <n> | reset [all] | clear | list | on | off"

    private fun tell(text: String) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(Component.literal(text))
    }
}
