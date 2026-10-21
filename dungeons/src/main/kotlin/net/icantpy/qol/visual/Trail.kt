package net.icantpy.qol.visual

import net.minecraft.client.Minecraft
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.Vec3

/**
 * Fixed-capacity ring of 3D points. Preallocated arrays, so [add] never allocates. Index 0 is the oldest point and
 * [size] - 1 the newest.
 */
class TrailRing(val capacity: Int) {
    private val xs = DoubleArray(capacity)
    private val ys = DoubleArray(capacity)
    private val zs = DoubleArray(capacity)
    private var head = 0
    var size = 0
        private set

    fun add(x: Double, y: Double, z: Double) {
        if (capacity == 0) return
        xs[head] = x
        ys[head] = y
        zs[head] = z
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun x(index: Int): Double = xs[slot(index)]
    fun y(index: Int): Double = ys[slot(index)]
    fun z(index: Int): Double = zs[slot(index)]

    fun clear() {
        head = 0
        size = 0
    }

    private fun slot(index: Int): Int {
        require(index in 0 until size) { "index $index out of 0 until $size" }
        return (head - size + index + capacity) % capacity
    }
}

/**
 * Movement trail behind the local player. Points are recorded in [tick] while the player moves. [render] draws a
 * line ribbon, while the particle styles spawn one client-only particle per tick at the player's feet.
 */
object Trail {
    private const val MIN_CAPACITY = 2
    private const val MAX_CAPACITY = 256
    private const val MOVE_EPSILON_SQ = 1.0e-6
    private const val FEET_LIFT = 0.1
    private const val MIN_TAIL_ALPHA = 24

    private var ring = TrailRing(0)

    /** Records the player's position and spawns a particle when the trail is on. Runs on the client tick. */
    fun tick(config: VisualsConfig) {
        if (!config.trail) {
            if (ring.size > 0) ring.clear()
            return
        }
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (player.isSpectator || player.isInvisible) {
            ring.clear()
            return
        }
        val capacity = config.trailLength.coerceIn(MIN_CAPACITY, MAX_CAPACITY)
        if (ring.capacity != capacity) ring = TrailRing(capacity)

        val v = player.deltaMovement
        if (v.x * v.x + v.z * v.z + v.y * v.y <= MOVE_EPSILON_SQ) return

        val x = player.x
        val y = player.y
        val z = player.z
        if (config.trailStyle == TrailStyle.LINE) {
            ring.add(x, y, z)
        } else {
            val options = particleFor(config) ?: return
            level.addParticle(options, x, y + 0.1, z, 0.0, 0.0, 0.0)
        }
    }

    /** Drops every recorded point. Called on unload and whenever the trail stops. */
    fun clear() {
        ring.clear()
    }

    /** Draws the line ribbon. Cheap early return unless the trail is on, the style is LINE and points exist. */
    fun render(config: VisualsConfig) {
        if (!config.trail || config.trailStyle != TrailStyle.LINE) return
        val n = ring.size
        if (n < 2) return
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        val lift = FEET_LIFT
        val time = System.currentTimeMillis() / 1000.0
        val segments = n - 1
        for (i in 0 until segments) {
            val t = (i + 1).toFloat() / segments
            val rgb = if (config.trailRainbow) {
                CosmeticColors.rainbow(time, i.toFloat() / ring.capacity)
            } else {
                config.trailColor
            }
            val argb = CosmeticColors.withAlpha(rgb, CosmeticColors.fadeAlpha(t, 255, MIN_TAIL_ALPHA))
            val a = Vec3(ring.x(i), ring.y(i) + lift, ring.z(i))
            val b = Vec3(ring.x(i + 1), ring.y(i + 1) + lift, ring.z(i + 1))
            Gizmos.line(a, b, argb, config.trailWidth.coerceIn(0.1f, 16f))
        }
    }

    private fun particleFor(config: VisualsConfig): ParticleOptions? = when (config.trailStyle) {
        TrailStyle.LINE -> null
        TrailStyle.DUST -> {
            val rgb = if (config.trailRainbow) {
                CosmeticColors.rainbow(System.currentTimeMillis() / 1000.0)
            } else {
                config.trailColor
            }
            DustParticleOptions(rgb, 1.0f)
        }
        TrailStyle.HEART -> ParticleTypes.HEART
        TrailStyle.NOTE -> ParticleTypes.NOTE
        TrailStyle.CRIT -> ParticleTypes.CRIT
        TrailStyle.END_ROD -> ParticleTypes.END_ROD
    }
}
