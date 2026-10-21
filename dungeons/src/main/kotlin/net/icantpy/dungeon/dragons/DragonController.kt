package net.icantpy.dungeon.dragons

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import java.nio.file.Path
import java.util.Locale

data class DragonTitle(val text: String, val colorArgb: Int, val untilMs: Long)

/**
 * Glue between the pure [DragonTracker] and Minecraft. Integrators forward events here; this object owns
 * settings, personal bests, local messages and the title. Everything stays local: no chat sends, no packets.
 *
 * Call order: [load] once, [bindPriorityInputs] once, then per run [onP5Start], forwarding calls, and [onRunEnd].
 */
object DragonController {
    private const val OUTBOX_LIMIT = 256
    private const val TITLE_MS = 3000L

    private var settings = DragonSettings()
    private val personalBests = mutableMapOf<WitherDragon, Long>()
    private var configPath: Path? = null
    private var loaded = false
    private var p5Active = false
    private var title: DragonTitle? = null
    private val outbox = ArrayDeque<DragonEvent>()

    private var classProvider: () -> String? = { null }
    private var powerProvider: () -> Int = { 0 }
    private var timeProvider: () -> Boolean = { false }

    private val tracker = DragonTracker(priorityResolver = ::resolvePriority)

    /** Reads the settings file once. Later calls are ignored. A malformed file is moved aside by the store. */
    fun load(path: Path) {
        if (loaded) return
        loaded = true
        configPath = path
        try {
            DragonSettingsStore.load(path)?.let { file ->
                settings = file.settings
                personalBests.clear()
                personalBests.putAll(file.personalBestsMs)
            }
        } catch (e: Exception) {
            sendLocal("§cDragon settings could not be loaded: ${e.message}")
        }
    }

    /** Suppliers read live player state when a priority is decided. They are only called at that moment. */
    fun bindPriorityInputs(playerClass: () -> String?, powerBlessing: () -> Int, timeBlessing: () -> Boolean) {
        classProvider = playerClass
        powerProvider = powerBlessing
        timeProvider = timeBlessing
    }

    fun onP5Start() {
        p5Active = true
        tracker.reset()
        title = null
    }

    fun onRunEnd() = endRun()

    fun onDisconnect() = endRun()

    fun onUnload() {
        endRun()
        saveNow()
    }

    fun onParticleEvent(
        type: String,
        x: Double,
        y: Double,
        z: Double,
        count: Int,
        xDist: Float,
        yDist: Float,
        zDist: Float,
        maxSpeed: Float,
    ) {
        if (!isActive()) return
        tracker.onParticle(type, x, y, z, count, xDist, yDist, zDist, maxSpeed)
        collect()
    }

    /** Call once per server tick. The countdown runs even when the feature is off so state stays consistent. */
    fun onServerTick() {
        tracker.onServerTick()
        if (isActive()) collect()
    }

    /** Call every client tick. Scans loaded dragon entities only in P5 or while a dragon is spawning or alive. */
    fun tick(mc: Minecraft) {
        if (!settings.enabled) return
        val anyLive = tracker.states().any { it.phase != DragonPhase.DEAD }
        if (!p5Active && !anyLive) return
        val level = mc.level ?: return
        val samples = ArrayList<DragonEntitySample>()
        for (entity in level.entitiesForRendering()) {
            if (entity is EnderDragon) {
                samples += DragonEntitySample(entity.id, entity.x, entity.y, entity.z, entity.health)
            }
        }
        tracker.onEntities(samples)
        collect()
    }

    fun onChat(line: String) {
        if (!isActive()) return
        tracker.onChat(line)
        collect()
    }

    /** Returns and clears events queued since the last call, so the leap trigger system can map them. */
    fun events(): List<DragonEvent> {
        val drained = outbox.toList()
        outbox.clear()
        return drained
    }

    fun settings(): DragonSettings = settings

    fun updateSettings(transform: (DragonSettings) -> DragonSettings) {
        settings = transform(settings).clamped()
        saveNow()
    }

    fun personalBestMs(dragon: WitherDragon): Long? = personalBests[dragon]

    /** Clears one dragon's personal best and saves at once. */
    fun resetPersonalBest(dragon: WitherDragon) {
        if (personalBests.remove(dragon) != null) saveNow()
    }

    val currentPriority: WitherDragon?
        get() = tracker.priority

    fun states(): List<DragonState> = tracker.states()

    fun currentTick(): Long = tracker.tick

    fun boxes(): List<DragonBoxDraw> = DragonWorldRender.boxes(tracker.states(), settings)

    /** Current title, or null once it has expired. */
    fun activeTitle(nowMs: Long = System.currentTimeMillis()): DragonTitle? {
        val current = title ?: return null
        if (nowMs <= current.untilMs) return current
        title = null
        return null
    }

    private fun isActive(): Boolean = settings.enabled && p5Active

    /** True while the HUD should show: the feature is on and an M7 phase 5 run is in progress. */
    fun hudVisible(): Boolean = isActive()

    private fun endRun() {
        p5Active = false
        tracker.reset()
        title = null
    }

    private fun resolvePriority(spawning: Set<WitherDragon>): WitherDragon =
        DragonPriority.decide(
            spawning = spawning,
            playerClass = classProvider(),
            powerBlessing = powerProvider(),
            paul = settings.paulBuff,
            timeBlessing = timeProvider(),
            settings = settings,
        )

    private fun collect() {
        for (event in tracker.drainEvents()) {
            outbox.addLast(event)
            while (outbox.size > OUTBOX_LIMIT) outbox.removeFirst()
            announce(event)
        }
    }

    private fun announce(event: DragonEvent) {
        when (event) {
            is DragonEvent.PriorityDecided -> if (settings.showSpawnTitle) showSpawnTitle(event.dragon)
            is DragonEvent.Killed -> reportKill(event)
            is DragonEvent.Spawning, is DragonEvent.Spawned, is DragonEvent.Lost -> Unit
        }
    }

    private fun showSpawnTitle(dragon: WitherDragon) {
        title = DragonTitle(
            text = "${dragon.displayName.uppercase(Locale.ROOT)} DRAGON SPAWNING",
            colorArgb = dragon.colorArgb,
            untilMs = System.currentTimeMillis() + TITLE_MS,
        )
    }

    private fun reportKill(event: DragonEvent.Killed) {
        val update = DragonPersonalBests.record(personalBests.toMap(), event.dragon, event.aliveMs, event.realHealthKill)
        if (update.improved) {
            personalBests.clear()
            personalBests.putAll(update.bests)
            saveNow()
        }
        if (!settings.showKillMessages) return
        val seconds = String.format(Locale.ROOT, "%.1f", event.aliveMs / 1000.0)
        val estimate = if (event.realHealthKill) "" else " §7(estimated)"
        val best = if (update.improved) " §6new best" else ""
        sendLocal("§${event.dragon.colorCode}${event.dragon.displayName} dragon killed in §f${seconds}s$estimate$best")
    }

    private fun saveNow() {
        val path = configPath ?: return
        try {
            DragonSettingsStore.save(path, DragonFile(settings, personalBests.toMap()))
        } catch (e: Exception) {
            sendLocal("§cDragon settings could not be saved: ${e.message}")
        }
    }

    private fun sendLocal(text: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(text))
    }
}
