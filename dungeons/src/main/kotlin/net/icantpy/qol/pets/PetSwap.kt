package net.icantpy.qol.pets

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.gui.McUi
import net.icantpy.qol.auto.AutoActionQueue
import net.icantpy.qol.auto.AutoTarget
import net.icantpy.qol.invsort.InvSortTiming
import net.minecraft.client.Minecraft

/**
 * One key per pet. A press queues a "Pet swap" step on [AutoActionQueue], which sends "/pets", clicks the
 * icon in the vanilla menu like a player and closes it. Keys are polled once per client tick (edge
 * detected), the same way Freelook reads its key, so no key event is consumed or altered.
 */
object PetSwap {
    @Volatile
    var config: PetSwapConfig = PetSwapConfig()
        private set

    private val heldLastTick = mutableSetOf<Int>()

    fun load() {
        config = PetSwapStore.load()
        heldLastTick.clear()
    }

    fun reset() {
        activePet = null
        heldLastTick.clear()
        hookWasOut = null
    }

    fun edit(change: (PetSwapConfig) -> PetSwapConfig) {
        val next = change(config)
        if (next == config) return
        config = next
        PetSwapStore.save(next)
    }

    /** Whether the player's hook was out on the previous tick; null until the first tick in a world. */
    private var hookWasOut: Boolean? = null

    /**
     * Reads the client's own fishing hook (no packets): a hook appearing after none is a cast. The first
     * tick only records the state, so joining a world with a hook already out does not count as a cast.
     */
    private fun tickCast(mc: Minecraft, current: PetSwapConfig) {
        val player = mc.player
        if (player == null) {
            hookWasOut = null
            return
        }
        val out = player.fishing != null
        val before = hookWasOut
        hookWasOut = out
        if (before != false || !out) return
        if (!current.enabled || !current.swapOnCast || current.castPet.isBlank()) return
        swapTo(current.castPet)
    }

    fun tick() {
        val current = config
        tickCast(Minecraft.getInstance(), current)
        val keys = current.pets.map { it.key }.filter { it > 0 }.distinct()
        val mc = Minecraft.getInstance()
        val held = keys.filterTo(mutableSetOf()) { InputConstants.isKeyDown(mc.window, it) }
        val pressed = keys.firstOrNull { it in held && it !in heldLastTick }
        heldLastTick.clear()
        heldLastTick += held
        if (pressed == null || !current.enabled) return
        if (McUi.currentScreen(mc) != null || mc.player == null) return
        val pet = current.pets.firstOrNull { it.key == pressed } ?: return
        swapTo(pet.name, force = true)
    }

    /** Timing for one swap, from this feature's own delay settings. */
    fun timing(): InvSortTiming {
        val c = config
        return InvSortTiming(c.delayMinMs, c.delayMaxMs, instant = c.instant)
    }

    /** The pet last reported out by chat (summon, despawn or Autopet), or null when none or unknown. */
    @Volatile
    var activePet: String? = null
        private set

    /** Tracks the pet from chat; a pet swap aimed at the pet that just came out is dropped. */
    fun onChat(message: String) {
        val change = PetChat.parse(message) ?: return
        activePet = change.pet
        val pet = change.pet ?: return
        if (AutoActionQueue.hasPetSwapFor(pet)) AutoActionQueue.cancelPetSwaps("$pet is already out")
    }

    /** Queues the swap to [name]. Returns false when the queue refused it, or when that pet is already out. */
    fun swapTo(name: String, force: Boolean = false): Boolean {
        if (!force && PetChat.isSamePet(activePet, name)) return false
        val target = AutoTarget.Pet(PetMenu.cleanTarget(name))
        return AutoActionQueue.requestBatch("pet:${target.name.lowercase()}", listOf(target), "pet key", bypassDedupe = true)
    }
}
