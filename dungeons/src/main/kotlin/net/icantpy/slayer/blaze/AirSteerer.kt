package net.icantpy.slayer.blaze

import net.icantpy.compat.McCompat
import net.minecraft.client.Minecraft

/**
 * Air strafe by pressing the vanilla movement key mappings, as a held key would, so the player accelerates through
 * normal input and the server sees ordinary input and movement. Only keys this object pressed are released, back to
 * the physical state. It never touches the camera, jump, sprint or packets.
 */
object AirSteerer {
    private const val KEY_COUNT = 4
    private val owned = BooleanArray(KEY_COUNT)

    /** The movement keys the player is physically holding right now. */
    fun physical(): KeyCombo {
        val mc = Minecraft.getInstance()
        val keys = EscapeWalker.movementKeys(mc)
        val down = BooleanArray(KEY_COUNT) { EscapeWalker.physicallyDown(mc, keys[it]) }
        return KeyCombo(down[0], down[1], down[2], down[3])
    }

    /** Holds [combo] on top of the physically held keys; null, or a screen opening, releases everything. */
    fun hold(combo: KeyCombo?) {
        val mc = Minecraft.getInstance()
        if (combo == null || mc.player == null || McCompat.currentScreen(mc) != null) return release()
        val keys = EscapeWalker.movementKeys(mc)
        val want = booleanArrayOf(combo.forward, combo.back, combo.left, combo.right)
        for (i in 0 until KEY_COUNT) {
            val physical = EscapeWalker.physicallyDown(mc, keys[i])
            if (want[i] && !physical) {
                keys[i].setDown(true)
                owned[i] = true
            } else if (owned[i]) {
                owned[i] = false
                keys[i].setDown(physical)
            }
        }
    }

    /** Releases the keys this object pressed. Safe to call when nothing is held. */
    fun release() {
        if (owned.none { it }) return
        val mc = Minecraft.getInstance()
        val keys = EscapeWalker.movementKeys(mc)
        for (i in 0 until KEY_COUNT) {
            if (!owned[i]) continue
            owned[i] = false
            keys[i].setDown(EscapeWalker.physicallyDown(mc, keys[i]))
        }
    }
}
