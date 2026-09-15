package net.icantpy.modules.impl.timer

import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.gui.configUI.GuiLookSettings
import net.icantpy.gui.configUI.HudOverlaySettings
import net.icantpy.modules.impl.dungeon.DungeonListener
import net.icantpy.modules.impl.dungeon.leaporient.LeapOrient
import net.icantpy.modules.impl.dungeon.leaporient.LeapOrientSettings
import net.icantpy.modules.impl.stats.StatsArmorSettings
import net.icantpy.util.Text
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

object TickTimers {
    var settings: TimerSettings = TimerSettings()
        private set
    var clocks: TickClocks = TickClocks()
        private set

    @Volatile
    private var pingedThisClientTick: Boolean = false
    private val arm = TriggerArm()
    var notifications: List<ActiveNotification> = emptyList()
        private set

    val active: Boolean
        get() = bossActive || settings.debugMode

    val bossActive: Boolean
        get() = clocks.necron >= 0 ||
            clocks.goldorTick >= 0 ||
            clocks.goldorStart >= 0 ||
            clocks.pad >= 0 ||
            clocks.lightning >= 0 ||
            clocks.py >= 0 ||
            clocks.stormTick >= 0

    fun load() {
        settings = IcantpySettingsStore.load()
    }

    fun persist() {
        IcantpySettingsStore.save(settings)
    }

    fun toggle(key: String) {
        settings = settings.toggle(key)
        persist()
    }

    fun setHudPosition(hud: TimerHud, x: Int, y: Int, persist: Boolean = false) {
        settings = settings.withHudPosition(hud, x, y)
        if (persist) persist()
    }

    fun resetHudPosition(hud: TimerHud) {
        settings = settings.resetHudPosition(hud)
        persist()
    }

    fun addTrigger() {
        settings = settings.upsertTrigger(TimerTrigger.create())
        persist()
    }

    fun updateTrigger(trigger: TimerTrigger) {
        settings = settings.upsertTrigger(trigger)
        persist()
    }

    fun removeTrigger(id: String) {
        settings = settings.removeTrigger(id)
        persist()
    }

    fun replaceSettings(next: TimerSettings) {
        settings = next
        persist()
    }

    fun updateLook(transform: (GuiLookSettings) -> GuiLookSettings) {
        settings = settings.copy(look = transform(settings.look))
        persist()
    }

    fun updateHud(persist: Boolean = true, transform: (HudOverlaySettings) -> HudOverlaySettings) {
        settings = settings.copy(hud = transform(settings.hud))
        if (persist) persist()
    }

    fun updateLeap(transform: (LeapOrientSettings) -> LeapOrientSettings) {
        settings = settings.copy(leap = transform(settings.leap))
        persist()
    }

    fun updateStatsArmor(transform: (StatsArmorSettings) -> StatsArmorSettings) {
        settings = settings.copy(statsArmor = transform(settings.statsArmor))
        persist()
    }

    fun onChat(raw: String) {
        val line = Text.strip(raw)
        if (line.isBlank()) return
        val payload = bossPayload(line)
        if (payload.startsWith("[BOSS]") && !payload.startsWith("[BOSS] The Watcher:")) {
            DungeonListener.markBoss()
        }
        clocks = clocks.onChat(payload)
    }

    fun handleCommand(raw: String): String? {
        val body = IcantpyCommandPrefix.body(raw) ?: return null
        val lower = body.lowercase()
        if (lower != "debug" && !lower.startsWith("debug ")) return null
        if (lower == "debug") {
            toggle("debugMode")
            return if (settings.debugMode) "debug on" else "debug off"
        }
        val action = lower.substringAfter("debug ").trim()
        if (!settings.debugMode && action != "on") {
            toggle("debugMode")
        }
        when (action) {
            "on" -> if (!settings.debugMode) toggle("debugMode")
            "off" -> if (settings.debugMode) toggle("debugMode")
            "storm" -> onChat("[BOSS] Storm: Pathetic Maxor, just like expected.")
            "py" -> onChat("[BOSS] Storm: ENERGY HEED MY CALL!")
            "stormend" -> onChat("[BOSS] Storm: I should have known that I stood no chance.")
            "goldor" -> onChat("[BOSS] Goldor: Who dares trespass into my domain?")
            "core" -> onChat("The Core entrance is opening!")
            "necron" -> onChat("[BOSS] Necron: I'm afraid, your journey ends now.")
            "mort" -> onChat("[NPC] Mort: Here, I found this map when I first entered the dungeon.")
            else -> return "debug storm|py|stormend|goldor|core|necron|mort"
        }
        return "debug $action"
    }

    @Suppress("UNUSED_PARAMETER")
    fun onBlock(pos: BlockPos, state: BlockState) {
    }

    fun onServerTick() {
        pingedThisClientTick = true
        tick()
    }

    fun onClientTick() {
        expireNotifications()
        if (settings.debugMode && !pingedThisClientTick) {
            tick()
        }
        pingedThisClientTick = false
    }

    fun tick() {
        val dungeon = DungeonListener.state
        val debug = settings.debugMode
        val inDungeons = debug || dungeon.inDungeon
        val inBoss = debug || dungeon.inBoss
        if (!inDungeons && !active) return
        clocks = clocks.tick(
            inDungeons = inDungeons,
            inBoss = inBoss,
            loopPad = settings.padHud,
            loopGoldor = settings.goldorHud,
        )
        fireNotifications()
    }

    fun reset(force: Boolean = false) {
        if (!force && settings.debugMode) return
        clocks = TickClocks()
        arm.reset()
        notifications = emptyList()
        LeapOrient.resetClocks()
    }

    fun hudPieces(preview: Boolean = false): List<HudPiece> {
        val dungeon = DungeonListener.state
        val debug = settings.debugMode
        val pieces = ArrayList<HudPiece>(7)
        addPiece(pieces, TimerHud.NECRON, clocks.necron, TickClocks.NECRON_TICKS, "§4Necron dropping in", preview, 35)
        if (settings.goldorHud) {
            val useStart = !preview && settings.startTimer && clocks.goldorStart >= 0
            val time = when {
                useStart -> clocks.goldorStart
                clocks.goldorTick >= 0 -> clocks.goldorTick
                preview -> 35
                else -> TickClocks.INACTIVE
            }
            val prefix = if (useStart) "§aStart:" else "§7Tick:"
            val max = if (useStart) TickClocks.GOLDOR_START_MAX else TickClocks.GOLDOR_TICKS
            if (time >= 0) {
                val (x, y) = settings.position(TimerHud.GOLDOR)
                pieces += HudPiece(
                    TimerHud.GOLDOR,
                    TickClocks.format(prefix, time, max, settings.displayInTicks, settings),
                    x,
                    y,
                )
            }
        }
        addPiece(pieces, TimerHud.PAD, clocks.pad, TickClocks.PAD_TICKS, "§bPad:", preview, 15)
        addPiece(pieces, TimerHud.LIGHTNING, clocks.lightning, TickClocks.LIGHTNING_TICKS, "§bLightning:", preview, TickClocks.LIGHTNING_TICKS)
        addPiece(pieces, TimerHud.PY, clocks.py, TickClocks.PY_TICKS, "§bPY:", preview, TickClocks.PY_TICKS)
        if (settings.stormTickHud) {
            val time = if (clocks.stormTick >= 0) clocks.stormTick else if (preview) 200 else TickClocks.INACTIVE
            if (time >= 0) {
                val (x, y) = settings.position(TimerHud.STORM_TICK)
                val override = if (preview && clocks.stormTick < 0) "§a" else null
                pieces += HudPiece(
                    TimerHud.STORM_TICK,
                    TickClocks.format("§bStorm:", time, TickClocks.STORM_MAX, settings.displayInTicks, settings, override),
                    x,
                    y,
                )
            }
        }
        if (settings.secretsHud) {
            val showLive = debug || (dungeon.percentCleared > 0 && !dungeon.inBoss)
            if (preview && !showLive) {
                val (x, y) = settings.position(TimerHud.SECRETS)
                pieces += HudPiece(
                    TimerHud.SECRETS,
                    TickClocks.format("§7Secret:", 15, TickClocks.SECRET_TICKS, settings.displayInTicks, settings, "§c"),
                    x,
                    y,
                )
            } else if (showLive) {
                val time = clocks.secretRemaining()
                val (x, y) = settings.position(TimerHud.SECRETS)
                pieces += HudPiece(
                    TimerHud.SECRETS,
                    TickClocks.format(
                        "§7Secret:",
                        time,
                        TickClocks.SECRET_TICKS,
                        settings.displayInTicks,
                        settings,
                        TickClocks.secretColor(time),
                    ),
                    x,
                    y,
                )
            }
        }
        return pieces
    }

    private fun fireNotifications() {
        val fired = arm.evaluate(ClockSnapshot.from(clocks), settings.triggers)
        if (fired.isEmpty()) return
        val next = notifications.toMutableList()
        for (trigger in fired) {
            val text = trigger.overlayText()
            next.removeAll { it.text == text }
            next += ActiveNotification(text, trigger.durationTicks.coerceAtLeast(1))
            val sound = trigger.sound.ifBlank { settings.hud.alertSound }
            AlertSounds.play(sound)
        }
        notifications = next
    }

    private fun expireNotifications() {
        if (notifications.isEmpty()) return
        notifications = notifications.map { it.copy(ticksLeft = it.ticksLeft - 1) }.filter { it.ticksLeft > 0 }
    }

    private fun addPiece(
        pieces: MutableList<HudPiece>,
        hud: TimerHud,
        time: Int,
        max: Int,
        prefix: String,
        preview: Boolean,
        previewTime: Int,
    ) {
        if (!settings.enabled(hud)) return
        val shown = if (time >= 0) time else if (preview) previewTime else return
        val (x, y) = settings.position(hud)
        pieces += HudPiece(hud, TickClocks.format(prefix, shown, max, settings.displayInTicks, settings), x, y)
    }

    private fun bossPayload(line: String): String {
        val marker = line.indexOf("[BOSS]")
        if (marker > 0) return line.substring(marker)
        val mort = line.indexOf("[NPC] Mort:")
        if (mort > 0) return line.substring(mort)
        val core = line.indexOf("The Core entrance is opening!")
        if (core > 0) return line.substring(core)
        return line
    }
}
