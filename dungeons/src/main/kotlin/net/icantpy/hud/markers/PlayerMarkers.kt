package net.icantpy.hud.markers

import net.icantpy.api.GlowHook
import java.util.function.IntUnaryOperator

/** Dungeon class used by class-based marker selectors. */
enum class MarkerClass {
    ARCHER, BERSERK, HEALER, MAGE, TANK;

    companion object {
        fun fromToken(raw: String): MarkerClass? = when (raw.trim().lowercase()) {
            "archer", "arch" -> ARCHER
            "berserk", "berserker", "bers" -> BERSERK
            "healer", "heal" -> HEALER
            "mage" -> MAGE
            "tank" -> TANK
            else -> null
        }
    }
}

/** Who a marker targets: a player name (case-insensitive) or a dungeon class resolved to a player. */
sealed interface MarkerSelector {
    data class ByName(val name: String) : MarkerSelector
    data class ByClass(val dungeonClass: MarkerClass) : MarkerSelector
}

enum class MarkerStyle { ARROW, BEAM, BOX }

data class ActiveMarker(
    val id: String,
    val selector: MarkerSelector,
    /** Player name the selector resolves to right now, or null when unresolved. */
    val targetName: String?,
    val style: MarkerStyle,
    val colorArgb: Int,
    val throughWalls: Boolean,
    val showName: Boolean,
    val expiresAtMs: Long,
    val opacityPercent: Int = MarkerLook.DEFAULT_OPACITY,
    val sizePercent: Int = MarkerLook.DEFAULT_SIZE,
    val heightBlocks: Int = MarkerLook.DEFAULT_HEIGHT,
    val pulse: Boolean = true,
    val playerGlow: Boolean = false,
    val markerGlow: Boolean = false,
    val shape: String = MarkerShapes.CLASSIC,
    val spin: Boolean = false,
    val bob: Boolean = true,
    val brightnessPercent: Int = MarkerLook.DEFAULT_BRIGHTNESS,
)

/**
 * Pure, thread-safe model of client-side player markers. No Minecraft types here so it can be unit tested.
 * The renderer reads [active] each frame; the integrator sets [classResolver] to map a class name
 * (for example "ARCHER") to a teammate's player name, or null when unknown.
 */
object PlayerMarkers {
    private class Entry(
        val id: String,
        val selector: MarkerSelector,
        val style: MarkerStyle,
        val colorArgb: Int,
        val throughWalls: Boolean,
        val showName: Boolean,
        val expiresAtMs: Long,
        val opacityPercent: Int,
        val sizePercent: Int,
        val heightBlocks: Int,
        val pulse: Boolean,
        val playerGlow: Boolean,
        val markerGlow: Boolean,
        val shape: String,
        val spin: Boolean,
        val bob: Boolean,
        val brightnessPercent: Int,
    )

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()

    /** Maps a [MarkerClass] name such as "ARCHER" to the teammate's player name, or null. */
    @Volatile
    var classResolver: (String) -> String? = { null }

    /** Entity id to glow RGB (0xRRGGBB), replaced wholesale by [refreshGlow]. Read without locks. */
    @Volatile
    private var glowColors: Map<Int, Int> = emptyMap()

    /** Set by [glowColorFor] (mixin side) and consumed by [noteGlowHookTick] (renderer side). */
    @Volatile
    private var hookCalledSinceTick = false

    /** Frame time of the last tick that saw the mixin call [glowColorFor]; [NEVER_SEEN] when never. */
    @Volatile
    private var hookLastSeenMs = NEVER_SEEN

    /**
     * Adds or replaces the marker with [id]. [durationMs] of 0 (or less) keeps it until [clear]/[clearAll].
     * Re-marking an existing id replaces it entirely, including its expiry.
     */
    fun mark(
        id: String,
        selector: MarkerSelector,
        style: MarkerStyle,
        colorArgb: Int,
        durationMs: Long,
        nowMs: Long,
        throughWalls: Boolean = false,
        showName: Boolean = true,
        opacityPercent: Int = MarkerLook.DEFAULT_OPACITY,
        sizePercent: Int = MarkerLook.DEFAULT_SIZE,
        heightBlocks: Int = MarkerLook.DEFAULT_HEIGHT,
        pulse: Boolean = true,
        playerGlow: Boolean = false,
        markerGlow: Boolean = false,
        shape: String = MarkerShapes.CLASSIC,
        spin: Boolean = false,
        bob: Boolean = true,
        brightnessPercent: Int = MarkerLook.DEFAULT_BRIGHTNESS,
    ) {
        val expires = if (durationMs > 0L) nowMs + durationMs else NEVER
        synchronized(lock) {
            entries.remove(id)
            entries[id] = Entry(
                id, selector, style, colorArgb, throughWalls, showName, expires,
                opacityPercent, sizePercent, heightBlocks, pulse, playerGlow, markerGlow, shape, spin, bob,
                MarkerLook.clampBrightness(brightnessPercent),
            )
        }
    }

    fun clear(id: String) {
        synchronized(lock) { entries.remove(id) }
    }

    fun clearAll() {
        synchronized(lock) { entries.clear() }
        glowColors = emptyMap()
        GlowHook.install(null)
    }

    /** Returns markers that have not expired at [nowMs], dropping expired ones as a side effect. */
    fun active(nowMs: Long): List<ActiveMarker> = synchronized(lock) {
        entries.values.removeAll { it.expiresAtMs != NEVER && nowMs >= it.expiresAtMs }
        entries.values.map { e ->
            ActiveMarker(
                id = e.id,
                selector = e.selector,
                targetName = resolveName(e.selector),
                style = e.style,
                colorArgb = e.colorArgb,
                throughWalls = e.throughWalls,
                showName = e.showName,
                expiresAtMs = e.expiresAtMs,
                opacityPercent = e.opacityPercent,
                sizePercent = e.sizePercent,
                heightBlocks = e.heightBlocks,
                pulse = e.pulse,
                playerGlow = e.playerGlow,
                markerGlow = e.markerGlow,
                shape = e.shape,
                spin = e.spin,
                bob = e.bob,
                brightnessPercent = e.brightnessPercent,
            )
        }
    }

    /**
     * Computes the active markers at [nowMs] and publishes the glow map from them. [entityIdOf] maps a marker
     * to the entity id of its resolved player, or null when that player is not loaded or must not glow.
     * Returns the active markers so the caller can draw them without a second lookup.
     */
    fun refreshGlow(nowMs: Long, entityIdOf: (ActiveMarker) -> Int?): List<ActiveMarker> {
        val markers = active(nowMs)
        glowColors = buildGlowColors(markers, entityIdOf)
        syncGlowHook()
        return markers
    }

    /**
     * Routes the loader's [GlowHook] to [glowColorFor] while any glow is wanted, so the mixin asks the payload
     * per entity (and that call keeps [glowHookActive] true). Disabled otherwise, which keeps the mixin inert.
     */
    private fun syncGlowHook() {
        GlowHook.install(if (glowColors.isEmpty()) null else glowHookProvider)
    }

    private val glowHookProvider = IntUnaryOperator { id -> glowColorFor(id) ?: GlowHook.NO_GLOW }

    /** Entity id to RGB for every marker with player glow. The first marker wins when several target one entity. */
    fun buildGlowColors(markers: List<ActiveMarker>, entityIdOf: (ActiveMarker) -> Int?): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for (marker in markers) {
            if (!marker.playerGlow) continue
            val id = entityIdOf(marker) ?: continue
            out.putIfAbsent(id, MarkerLook.brightenRgb(marker.colorArgb, marker.brightnessPercent))
        }
        return out
    }

    /**
     * RGB (0xRRGGBB, no alpha) of the active marker that has player glow for the entity [entityId], or null.
     * Constant time: reads the map published by the last [refreshGlow]. Also records that the loader hook is live.
     */
    /** True when a marker currently highlights [entityId]. Unlike [glowColorFor] this does not mark the hook as live. */
    fun isHighlighted(entityId: Int): Boolean = glowColors.containsKey(entityId)

    fun glowColorFor(entityId: Int): Int? {
        if (!hookCalledSinceTick) hookCalledSinceTick = true
        return glowColors[entityId]
    }

    /**
     * Call once per renderer tick before applying the fallback. Moves the "hook called" flag into the window
     * timestamp so [glowHookActive] can answer without reading the clock on every entity.
     */
    fun noteGlowHookTick(nowMs: Long) {
        if (hookCalledSinceTick) {
            hookLastSeenMs = nowMs
            hookCalledSinceTick = false
        }
    }

    /** True when [glowColorFor] was called within [HOOK_WINDOW_MS] before [nowMs], i.e. the loader mixin is active. */
    fun glowHookActive(nowMs: Long): Boolean {
        val seen = hookLastSeenMs
        return seen != NEVER_SEEN && nowMs - seen in 0L..HOOK_WINDOW_MS
    }

    fun resolveName(selector: MarkerSelector): String? = when (selector) {
        is MarkerSelector.ByName -> selector.name.takeIf { it.isNotBlank() }
        is MarkerSelector.ByClass -> classResolver(selector.dungeonClass.name)?.takeIf { it.isNotBlank() }
    }

    /** Floating label text: "Name | Class | 12m". [distanceBlocks] may be null when unknown. */
    fun labelText(playerName: String, dungeonClass: MarkerClass?, distanceBlocks: Double?): String {
        val parts = mutableListOf(playerName)
        dungeonClass?.let { parts += it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        distanceBlocks?.let { parts += "${it.toInt()}m" }
        return parts.joinToString(" | ")
    }

    /** Pulse factor in [0.7, 1.0] for a given time, used to modulate marker alpha. */
    fun pulse(nowMs: Long): Float {
        val phase = (nowMs % PULSE_PERIOD_MS).toFloat() / PULSE_PERIOD_MS
        val wave = 0.5f - 0.5f * kotlin.math.cos(phase * 2f * Math.PI.toFloat())
        return 0.7f + 0.3f * wave
    }

    private const val NEVER = Long.MAX_VALUE
    private const val NEVER_SEEN = Long.MIN_VALUE
    private const val PULSE_PERIOD_MS = 1500L

    /** How long after the last hook call the loader hook still counts as live. */
    const val HOOK_WINDOW_MS = 1000L
}
