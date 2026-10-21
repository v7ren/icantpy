package net.icantpy.hud.markers

import net.icantpy.Icantpy
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Method

/**
 * Draws [PlayerMarkers] in the world using gizmos: cuboids and lines for shapes, billboard text for names.
 * Through walls uses [net.minecraft.gizmos.GizmoProperties.setAlwaysOnTop].
 *
 * Player glow has two paths. When the loader mixin calls [PlayerMarkers.glowColorFor] (see
 * [PlayerMarkers.glowHookActive]) the vanilla outline is colored by the mixin and nothing is touched here.
 * Otherwise this renderer sets the entity's shared glowing flag by reflection, tracked by [glowLedger] so the
 * original state is restored when no marker wants it.
 */
object PlayerMarkerRenderer {
    private const val BOB_AMPLITUDE = 0.25
    private const val BOB_PERIOD_MS = 2000.0
    private const val SPIN_PERIOD_MS = 4000L
    private const val OUTLINE_WIDTH = 1.8f
    private const val LINE_WIDTH = 1.8f
    private const val HALO_WIDTH = 6.0f
    private const val HALO_SCALE = 1.35
    private const val HALO_GROW = 0.15
    private const val OUTER_HALO_WIDTH = 10.0f
    private const val OUTER_HALO_SCALE = 1.8
    private const val OUTER_HALO_GROW = 0.35
    private const val LABEL_LIFT = 0.45
    private const val ARROW_GAP_BASE = 3.0

    private val glowLedger = MarkerGlowLedger()
    private val glowEntities = HashMap<Int, Entity>()

    fun render(nowMs: Long = System.currentTimeMillis()) {
        val mc = Minecraft.getInstance()
        val level = mc.level
        PlayerMarkers.noteGlowHookTick(nowMs)
        if (level == null) {
            PlayerMarkers.refreshGlow(nowMs) { null }
            releaseGlow()
            return
        }
        val self = mc.player
        val players = level.players()
        val markers = PlayerMarkers.refreshGlow(nowMs) { marker -> targetOf(marker, players, self)?.id }
        if (markers.isEmpty()) {
            releaseGlow()
            return
        }
        val pulse = PlayerMarkers.pulse(nowMs)
        val glowTargets = HashMap<Int, Entity>()

        for (marker in markers) {
            val entity = targetOf(marker, players, self) ?: continue
            if (marker.playerGlow) glowTargets[entity.id] = entity

            val box = entity.boundingBox
            val cx = (box.minX + box.maxX) / 2.0
            val cz = (box.minZ + box.maxZ) / 2.0
            val alpha = MarkerLook.boostAlpha(MarkerLook.alpha(255, marker.opacityPercent, marker.pulse, pulse), marker.brightnessPercent)
            val haloAlpha = MarkerLook.boostAlpha(MarkerLook.alpha(150, marker.opacityPercent, marker.pulse, pulse), marker.brightnessPercent)
            val outerAlpha = MarkerLook.boostAlpha(MarkerLook.alpha(70, marker.opacityPercent, marker.pulse, pulse), marker.brightnessPercent)
            val height = marker.heightBlocks.coerceAtLeast(0).toDouble()
            // Brightness applies to the marker color itself; marker glow adds a lightened core and two halo layers.
            val base = MarkerLook.brightenRgb(marker.colorArgb, marker.brightnessPercent)
            val coreColor = withAlpha(if (marker.markerGlow) lighten(base) else base, alpha)
            val coreStyle = GizmoStyle.strokeAndFill(coreColor, OUTLINE_WIDTH, withAlpha(base, alpha / 4))
            val haloStyle = GizmoStyle.stroke(withAlpha(lighten(base), haloAlpha), HALO_WIDTH)
            val outerStyle = GizmoStyle.stroke(withAlpha(lighten(base), outerAlpha), OUTER_HALO_WIDTH)
            val topY: Double

            when (marker.style) {
                MarkerStyle.ARROW -> {
                    val bob = if (marker.bob) BOB_AMPLITUDE * kotlin.math.sin(2.0 * Math.PI * nowMs / BOB_PERIOD_MS) else 0.0
                    val tipY = box.maxY + ARROW_GAP_BASE * MarkerLook.scale(marker.sizePercent) + bob + height
                    val spin = if (marker.spin) 2.0 * Math.PI * (nowMs % SPIN_PERIOD_MS) / SPIN_PERIOD_MS else 0.0
                    if (marker.markerGlow) {
                        drawParts(
                            MarkerShapes.parts(marker.shape, marker.sizePercent, cx, tipY, cz, spin, OUTER_HALO_SCALE),
                            outerStyle, marker.throughWalls, OUTER_HALO_WIDTH,
                        )
                        drawParts(
                            MarkerShapes.parts(marker.shape, marker.sizePercent, cx, tipY, cz, spin, HALO_SCALE),
                            haloStyle, marker.throughWalls, HALO_WIDTH,
                        )
                    }
                    val parts = MarkerShapes.parts(marker.shape, marker.sizePercent, cx, tipY, cz, spin)
                    drawParts(parts, coreStyle, marker.throughWalls, LINE_WIDTH)
                    topY = parts.maxOfOrNull { partMaxY(it) } ?: tipY
                }
                MarkerStyle.BEAM -> {
                    val hw = MarkerLook.beamHalfWidth(marker.sizePercent)
                    val base0 = box.maxY + height
                    val beam = AABB(cx - hw, base0, cz - hw, cx + hw, base0 + MarkerLook.beamHeight(marker.sizePercent), cz + hw)
                    if (marker.markerGlow) {
                        cuboid(beam.inflate(OUTER_HALO_GROW), outerStyle, marker.throughWalls)
                        cuboid(beam.inflate(HALO_GROW), haloStyle, marker.throughWalls)
                    }
                    cuboid(beam, coreStyle, marker.throughWalls)
                    topY = beam.maxY
                }
                MarkerStyle.BOX -> {
                    val grow = 0.05 + MarkerLook.boxGrow(marker.sizePercent)
                    val moved = box.inflate(grow).move(0.0, height, 0.0)
                    if (marker.markerGlow) {
                        cuboid(moved.inflate(OUTER_HALO_GROW), outerStyle, marker.throughWalls)
                        cuboid(moved.inflate(HALO_GROW), haloStyle, marker.throughWalls)
                    }
                    cuboid(moved, coreStyle, marker.throughWalls)
                    topY = moved.maxY
                }
            }

            if (marker.showName) {
                val cls = (marker.selector as? MarkerSelector.ByClass)?.dungeonClass
                val distance = self?.position()?.distanceTo(entity.position())
                val text = PlayerMarkers.labelText(entity.name.string, cls, distance)
                val at = Vec3(cx, topY + LABEL_LIFT, cz)
                Gizmos.billboardText(text, at, TextGizmo.Style.forColorAndCentered(coreColor))
                    .also { if (marker.throughWalls) it.setAlwaysOnTop() }
            }
        }

        applyPlayerGlow(glowTargets, nowMs)
    }

    /** Restores every entity we made glow and clears the bookkeeping. Safe to call repeatedly. */
    fun releaseGlow() {
        glowLedger.releaseAll { id, glowing -> setGlowing(id, glowing) }
        forgetGlowEntities()
    }

    /** Fallback only: applies the shared glowing flag to [targets] while the loader hook is not live. */
    private fun applyPlayerGlow(targets: Map<Int, Entity>, nowMs: Long) {
        if (targets.isEmpty() || PlayerMarkers.glowHookActive(nowMs)) {
            releaseGlow()
            return
        }
        glowEntities.putAll(targets)
        val wanted = HashMap<Int, Boolean>()
        for ((id, entity) in targets) wanted[id] = hasGlowEffect(entity)
        glowLedger.reconcile(wanted) { id, glowing -> setGlowing(id, glowing) }
        forgetGlowEntities()
    }

    /** Whether the entity's glow comes from a real status effect, so restoring does not clear it. */
    private fun hasGlowEffect(entity: Entity): Boolean =
        (entity as? LivingEntity)?.hasEffect(MobEffects.GLOWING) == true

    private fun targetOf(marker: ActiveMarker, players: List<Player>, self: Player?): Player? {
        val target = marker.targetName ?: return null
        val entity = players.firstOrNull { it.name.string.equals(target, ignoreCase = true) } ?: return null
        // Never mark the local player unless it was targeted explicitly by name.
        if (entity === self && marker.selector !is MarkerSelector.ByName) return null
        return entity
    }

    private fun setGlowing(id: Int, glowing: Boolean) {
        glowEntities[id]?.let { SharedGlowFlag.set(it, glowing) }
    }

    private fun forgetGlowEntities() {
        glowEntities.keys.retainAll(glowLedger.glowingIds)
    }

    private fun cuboid(box: AABB, style: GizmoStyle, throughWalls: Boolean) {
        val props = Gizmos.cuboid(box, style)
        if (throughWalls) props.setAlwaysOnTop()
    }

    /** Boxes become cuboids in [style]; segments become lines of [width] in the style's stroke color. */
    private fun drawParts(parts: List<MarkerPart>, style: GizmoStyle, throughWalls: Boolean, width: Float) {
        for (part in parts) {
            when (part) {
                is MarkerPart.Box -> cuboid(AABB(part.minX, part.minY, part.minZ, part.maxX, part.maxY, part.maxZ), style, throughWalls)
                is MarkerPart.Seg -> {
                    val props = Gizmos.line(
                        Vec3(part.x1, part.y1, part.z1),
                        Vec3(part.x2, part.y2, part.z2),
                        style.stroke(),
                        width,
                    )
                    if (throughWalls) props.setAlwaysOnTop()
                }
            }
        }
    }

    private fun partMaxY(part: MarkerPart): Double = when (part) {
        is MarkerPart.Box -> part.maxY
        is MarkerPart.Seg -> maxOf(part.y1, part.y2)
    }

    private fun withAlpha(argb: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (argb and 0x00FFFFFF)

    /** Halfway toward white, used for the emissive core and halo of a marker glow. */
    private fun lighten(argb: Int): Int {
        fun mix(c: Int) = c + (255 - c) / 2
        val r = mix(argb shr 16 and 0xFF)
        val g = mix(argb shr 8 and 0xFF)
        val b = mix(argb and 0xFF)
        return (argb and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Sets Entity.setSharedFlag(FLAG_GLOWING, value) by reflection. The method is protected and unobfuscated on
     * 26.x. If it cannot be found, one warning is logged and the fallback is disabled for the session.
     */
    private object SharedGlowFlag {
        private const val FLAG_GLOWING = 6
        private var method: Method? = null
        private var failed = false

        fun set(entity: Entity, glowing: Boolean) {
            if (failed) return
            try {
                val m = method ?: Entity::class.java
                    .getDeclaredMethod("setSharedFlag", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    .also { it.isAccessible = true; method = it }
                m.invoke(entity, FLAG_GLOWING, glowing)
            } catch (t: Throwable) {
                failed = true
                Icantpy.LOGGER.warn("Marker player glow fallback disabled: Entity.setSharedFlag unavailable", t)
            }
        }
    }
}
