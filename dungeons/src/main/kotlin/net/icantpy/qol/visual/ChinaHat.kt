package net.icantpy.qol.visual

import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/** A cone worn above the local player's head. Drawn only in third person. */
object ChinaHat {
    private const val SEGMENTS = 24
    private const val HEAD_GAP = 0.05
    private const val RIM_WIDTH = 1.8f
    private const val FILL_ALPHA = 0x80
    private const val RIM_ALPHA = 0xFF

    /** Unit circle directions, precomputed once. */
    private val cosTable = DoubleArray(SEGMENTS) { cos(2.0 * Math.PI * it / SEGMENTS) }
    private val sinTable = DoubleArray(SEGMENTS) { sin(2.0 * Math.PI * it / SEGMENTS) }

    /** Draws the hat at the player's interpolated position. Early return when off or in first person. */
    fun render(config: VisualsConfig, partialTick: Float) {
        if (!config.chinaHat) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.options.cameraType.isFirstPerson()) return
        if (player.isSpectator || player.isInvisible) return

        val feet = player.getPosition(partialTick)
        val radius = config.chinaHatSize.coerceIn(0.05f, 4f).toDouble()
        val height = config.chinaHatHeight.coerceIn(0.05f, 4f).toDouble()
        val baseY = feet.y + player.bbHeight + HEAD_GAP
        val apex = Vec3(feet.x, baseY + height, feet.z)
        val time = System.currentTimeMillis() / 1000.0

        // Segment k spans rim point k to rim point k+1 (wrapping), so the rim closes on itself.
        for (k in 0 until SEGMENTS) {
            val a = Vec3(feet.x + radius * cosTable[k], baseY, feet.z + radius * sinTable[k])
            val next = (k + 1) % SEGMENTS
            val b = Vec3(feet.x + radius * cosTable[next], baseY, feet.z + radius * sinTable[next])
            val rgb = if (config.chinaHatRainbow) {
                CosmeticColors.rainbow(time, k.toFloat() / SEGMENTS)
            } else {
                config.chinaHatColor
            }
            // Degenerate quad (apex listed twice) draws the filled triangle of one side of the cone.
            Gizmos.rect(apex, apex, a, b, GizmoStyle.fill(CosmeticColors.withAlpha(rgb, FILL_ALPHA)))
            Gizmos.line(a, b, CosmeticColors.withAlpha(rgb, RIM_ALPHA), RIM_WIDTH)
        }
    }
}
