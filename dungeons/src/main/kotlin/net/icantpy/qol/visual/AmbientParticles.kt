package net.icantpy.qol.visual

import net.minecraft.client.Minecraft
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import java.util.SplittableRandom
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Client-only particles drifting around the local player. Each tick adds density/20 to an accumulator and spawns the
 * whole-number part, so any density works out to the right rate per second.
 */
object AmbientParticles {
    /** Upper bound on spawns in one tick, so a huge density setting cannot flood the particle engine. */
    private const val MAX_PER_TICK = 16
    private const val MAX_RADIUS = 16.0

    private val random = SplittableRandom()
    private var carry = 0.0

    /** Spawns this tick's share of particles. Runs on the client tick. */
    fun tick(config: VisualsConfig) {
        if (!config.ambientParticles) {
            carry = 0.0
            return
        }
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (player.isSpectator) return

        carry += config.ambientDensity.coerceAtLeast(0) / 20.0
        val count = carry.toInt().coerceAtMost(MAX_PER_TICK)
        carry -= carry.toInt()
        if (count <= 0) return

        val radius = config.ambientRadius.coerceIn(0.5f, MAX_RADIUS.toFloat()).toDouble()
        val height = player.bbHeight + 1.0
        val cx = player.x
        val cy = player.y
        val cz = player.z
        val style = config.ambientStyle
        val time = System.currentTimeMillis() / 1000.0
        repeat(count) {
            // Uniform over the disc: sqrt keeps points from clustering at the centre.
            val angle = random.nextDouble() * 2.0 * Math.PI
            val r = sqrt(random.nextDouble()) * radius
            val x = cx + cos(angle) * r
            val y = cy + random.nextDouble() * height
            val z = cz + sin(angle) * r
            val vx = (random.nextDouble() - 0.5) * 0.02
            val vy = 0.01 + random.nextDouble() * 0.02
            val vz = (random.nextDouble() - 0.5) * 0.02
            level.addParticle(optionsFor(style, time), x, y, z, vx, vy, vz)
        }
    }

    /** Client-only particle for [style]. Dust uses a random hue each spawn so the cloud shifts colour. */
    fun optionsFor(style: AmbientStyle, timeSeconds: Double): ParticleOptions = when (style) {
        AmbientStyle.STARS -> ParticleTypes.END_ROD
        AmbientStyle.HEARTS -> ParticleTypes.HEART
        AmbientStyle.SNOW -> ParticleTypes.SNOWFLAKE
        AmbientStyle.FLAME -> ParticleTypes.FLAME
        AmbientStyle.GLOW -> ParticleTypes.GLOW
        AmbientStyle.CHERRY -> ParticleTypes.CHERRY_LEAVES
        AmbientStyle.FIREFLY -> ParticleTypes.FIREFLY
        AmbientStyle.NOTES -> ParticleTypes.NOTE
        AmbientStyle.DUST -> DustParticleOptions(CosmeticColors.rainbow(timeSeconds, random.nextDouble().toFloat()), 1.0f)
    }
}
