package net.icantpy.qol.invsort

import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import net.icantpy.api.IcantpyCommandPrefix
import net.icantpy.gui.McUi
import net.icantpy.qol.auto.AutoActionQueue

import net.icantpy.gui.config.icantpyKeyLabel
import net.minecraft.client.Minecraft
import java.nio.file.Path

/** Single source of truth for inventory sort settings. Every mutation saves immediately. */
object InvSortController {
    private const val FILE_NAME = "icantpy-invsort.json"
    private var state = InvSortSettings()
    private var configPath: Path? = null
    private var canPersist = true
    private var openNextTick = false
    private var pendingRun: PendingRun? = null

    /** [perPress] is true for hotkey runs, which honour "one swap per press". */
    private class PendingRun(val index: Int, val closeWhenDone: Boolean, val perPress: Boolean = false)

    /** Changes on unload so listeners from an earlier payload load stop acting. */
    private var generationToken = 0
    val generation: Int get() = generationToken

    val settings: InvSortSettings get() = state

    fun load() {
        val path = FabricLoader.getInstance().configDir.resolve(FILE_NAME)
        configPath = path
        canPersist = true
        state = try {
            InvSortStore.load(path) ?: InvSortSettings()
        } catch (e: Exception) {
            // Do not overwrite a file we could not read; saving is disabled for this session.
            canPersist = false
            Icantpy.LOGGER.warn("Failed to read inventory sort presets; original preserved", e)
            InvSortSettings()
        }
    }

    fun save() = persist()

    fun update(transform: (InvSortSettings) -> InvSortSettings) {
        state = transform(state)
        persist()
    }

    /** Captures the player's sortable inventory as a new preset. Returns an error or null. */
    fun saveCurrentAsPreset(name: String): String? {
        val cleaned = InvSortPreset.cleanName(name) ?: return "Preset names need 1 to 32 characters"
        if (state.presets.size >= InvSortLimits.MAX_PRESETS) return "Preset limit reached"
        val slots = capturePlayerSlots() ?: return "Not in a world"
        if (slots.isEmpty()) return "Your sortable inventory is empty"
        update { it.addPreset(InvSortPreset(cleaned, slots)) }
        return null
    }

    fun overwritePresetFromInventory(index: Int): String? {
        val preset = state.presets.getOrNull(index) ?: return "That preset no longer exists"
        val slots = capturePlayerSlots() ?: return "Not in a world"
        update { it.replacePreset(index, preset.copy(slots = slots)) }
        return null
    }

    /**
     * Reorders a preset from [from] to final index [to]. Any running sort is cancelled and a queued
     * hotkey/command run is dropped, since both refer to preset indices. Returns the moved preset's
     * new index, or null when nothing changed.
     */
    fun movePreset(from: Int, to: Int): Int? {
        val presets = state.presets
        if (from !in presets.indices || to !in presets.indices || from == to) return null
        InvSortRunner.cancel(reason = "presets were reordered")
        pendingRun = null
        update { it.movePreset(from, to) }
        return to
    }

    fun renamePreset(index: Int, name: String): String? {
        val cleaned = InvSortPreset.cleanName(name) ?: return "Preset names need 1 to 32 characters"
        if (state.presets.getOrNull(index) == null) return "That preset no longer exists"
        update { it.renamePreset(index, cleaned) }
        return null
    }

    /** Binds [key] (GLFW code, or [InvSortLimits.NO_HOTKEY]) to a preset. Returns an error or null. */
    fun setHotbarSwap(index: Int, on: Boolean) {
        if (state.presets.getOrNull(index) == null) return
        update { it.setHotbarSwap(index, on) }
    }

    fun setSwapOnePerPress(on: Boolean) = update { it.copy(swapOnePerPress = on) }

    fun setHotkey(index: Int, key: Int): String? {
        if (state.presets.getOrNull(index) == null) return "That preset no longer exists"
        val owner = state.hotkeyOwner(key, exceptIndex = index)
        if (owner != null) {
            return "${icantpyKeyLabel(key)} is already used by ${state.presets[owner].name}"
        }
        update { it.setHotkey(index, key) }
        return null
    }

    fun deletePreset(index: Int) = update { it.removePreset(index) }

    fun clearSlot(presetIndex: Int, slot: Int) {
        if (!InvSortSlots.isPresetSlot(slot)) return
        update { it.clearSlot(presetIndex, slot) }
    }

    fun clearArmor(presetIndex: Int) {
        if (state.presets.getOrNull(presetIndex) == null) return
        update { it.clearArmor(presetIndex) }
    }

    fun setShowButton(index: Int, show: Boolean) {
        if (state.presets.getOrNull(index) == null) return
        update { it.setShowButton(index, show) }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setMoveCursor(enabled: Boolean) = update { it.copy(moveCursor = enabled) }

    fun setCloseWhenDone(enabled: Boolean) = update { it.copy(closeWhenDone = enabled) }

    fun setInstant(enabled: Boolean) = update { it.copy(instant = enabled) }

    fun cycleSafetyLevel() = update { it.copy(safetyLevel = it.safetyLevel.next()) }

    fun setLockInput(enabled: Boolean) = update { it.copy(lockInput = enabled) }

    /** Drops a hotkey or command run that has not started yet. */
    fun cancelPendingRun() {
        pendingRun = null
    }

    /** Handles "/icantpy sort" (opens the editor) and "/icantpy sort <preset>" (runs a preset). */
    fun handleCommand(message: String): String? {
        val body = IcantpyCommandPrefix.body(message)?.trim() ?: return null
        if (!body.equals("sort", ignoreCase = true) && !body.startsWith("sort ", ignoreCase = true)) return null
        val presetName = body.substring(4).trim()
        if (presetName.isEmpty()) {
            openNextTick = true
            return ""
        }
        val index = state.presets.indexOfFirst { it.name.equals(presetName, ignoreCase = true) }
        if (index < 0) InvSortItems.say("No inventory sort preset named \"$presetName\"")
        else pendingRun = PendingRun(index, closeWhenDone = true)
        return ""
    }

    /** True while a sort is running or waiting to start; trigger-started sorts do not stack on it. */
    fun isSortBusy(): Boolean = InvSortRunner.isRunning || pendingRun != null

    /** Starts [index] on the next tick, once a screen opened this tick is the current screen. */
    fun queueRun(index: Int, closeWhenDone: Boolean, perPress: Boolean = false) {
        pendingRun = PendingRun(index, closeWhenDone, perPress)
    }

    fun tick() {
        startPendingRun()
        AutoActionQueue.tick()
        InvSortRunner.tick()
        AutoActionQueue.chainAfterSort()
        InvSortHotkeys.tick()
        InvSortScreenEvents.attachIfInventory(McUi.currentScreen(Minecraft.getInstance()), generationToken)
        if (!openNextTick) return
        openNextTick = false
        // Opened from tick so the outgoing chat message cannot replace the screen.
        val mc = Minecraft.getInstance()
        McUi.setScreen(mc, InvSortEditorScreen(McUi.currentScreen(mc)))
    }

    fun onDisconnect() {
        InvSortRunner.cancel(reason = null)
        InvSortHotkeys.reset()
        AutoActionQueue.clear()
        openNextTick = false
        pendingRun = null
    }

    private fun startPendingRun() {
        val run = pendingRun ?: return
        pendingRun = null
        InvSortRunner.start(run.index, run.closeWhenDone, run.perPress)?.let(InvSortItems::say)
    }

    fun onUnload() {
        generationToken++
        onDisconnect()
        InvSortEditorScreen.closeIfOpen()
        save()
    }

    private fun capturePlayerSlots(): Map<Int, InvSortSlot>? {
        val player = Minecraft.getInstance().player ?: return null
        return InvSortItems.capture(player.inventoryMenu)
    }

    private fun persist() {
        val path = configPath ?: return
        if (!canPersist) return
        try {
            InvSortStore.save(path, state)
        } catch (e: Exception) {
            Icantpy.LOGGER.warn("Failed to save inventory sort presets", e)
        }
    }
}
