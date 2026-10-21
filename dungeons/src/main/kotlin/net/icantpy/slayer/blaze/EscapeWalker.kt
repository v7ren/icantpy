package net.icantpy.slayer.blaze

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.compat.McCompat
import net.icantpy.mixin.KeyMappingAccessor
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * Walks the player toward a safe point by pressing the vanilla movement key mappings, as a held key would.
 * Only keys this walker pressed are released on stop. Any physically held movement key stops the walk at once,
 * and the walker never touches camera, sprint, jump or packets.
 */
object EscapeWalker {
    private const val ARRIVE_DISTANCE_SQ = 0.35 * 0.35
    private const val KEY_COUNT = 4
    private const val FORWARD = 0
    private const val BACK = 1
    private const val LEFT = 2
    private const val RIGHT = 3

    var active: Boolean = false
        private set

    private var targetX = 0.0
    private var targetZ = 0.0
    private var deadlineMs = 0L

    /** Keys this walker has pressed and not yet released, indexed by FORWARD..RIGHT. */
    private val owned = BooleanArray(KEY_COUNT)

    fun start(targetX: Double, targetZ: Double, timeoutMs: Int) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || McCompat.currentScreen(mc) != null) return
        this.targetX = targetX
        this.targetZ = targetZ
        deadlineMs = System.currentTimeMillis() + timeoutMs
        active = true
    }

    /** Called once per client tick. Does nothing unless a walk is active. */
    fun tick() {
        if (!active) return
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player == null || !player.isAlive || McCompat.currentScreen(mc) != null) return stop()
        if (System.currentTimeMillis() >= deadlineMs) return stop()
        val dx = targetX - player.x
        val dz = targetZ - player.z
        if (dx * dx + dz * dz < ARRIVE_DISTANCE_SQ) return stop()
        val keys = movementKeys(mc)
        if (keys.any { physicallyDown(mc, it) }) return stop()
        val bearing = FirePitEscape.bearing(player.x, player.z, targetX, targetZ)
        val combo = FirePitEscape.movementKeys(player.yRot, bearing)
        press(keys, FORWARD, combo.forward)
        press(keys, BACK, combo.back)
        press(keys, LEFT, combo.left)
        press(keys, RIGHT, combo.right)
    }

    /** Ends the walk and releases the keys it pressed. Safe to call when inactive. */
    fun stop() {
        active = false
        if (owned.none { it }) return
        val mc = Minecraft.getInstance()
        val keys = movementKeys(mc)
        for (index in 0 until KEY_COUNT) {
            if (!owned[index]) continue
            owned[index] = false
            keys[index].setDown(physicallyDown(mc, keys[index]))
        }
    }

    private fun press(keys: Array<KeyMapping>, index: Int, wanted: Boolean) {
        if (wanted) {
            keys[index].setDown(true)
            owned[index] = true
        } else if (owned[index]) {
            keys[index].setDown(false)
            owned[index] = false
        }
    }

    /** Order matches the FORWARD..RIGHT indices: forward, back, left, right. */
    private fun movementKeys(mc: Minecraft): Array<KeyMapping> {
        val options = mc.options
        return arrayOf(options.keyUp, options.keyDown, options.keyLeft, options.keyRight)
    }

    /** Reads the hardware state, not KeyMapping.isDown, because the walker itself sets that flag. */
    private fun physicallyDown(mc: Minecraft, mapping: KeyMapping): Boolean {
        if (mapping.isUnbound) return false
        val bound = boundKey(mapping) ?: return false
        val handle = mc.window.handle()
        return when (bound.type) {
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(handle, bound.value) == GLFW.GLFW_PRESS
            InputConstants.Type.KEYSYM -> GLFW.glfwGetKey(handle, bound.value) == GLFW.GLFW_PRESS
            else -> false
        }
    }

    private fun boundKey(mapping: KeyMapping): InputConstants.Key? = try {
        (mapping as KeyMappingAccessor).icantpyBoundKey()
    } catch (_: Throwable) {
        null
    }
}
