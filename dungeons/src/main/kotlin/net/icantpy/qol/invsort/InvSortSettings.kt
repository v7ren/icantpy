package net.icantpy.qol.invsort

import net.icantpy.qol.auto.CursorGuard

/**
 * Persisted inventory sort options. Delays are in milliseconds; each click waits a random delay in
 * [clickDelayMinMs]..[clickDelayMaxMs] so the clicks look human rather than metronomic.
 */
data class InvSortSettings(
    val enabled: Boolean = true,
    val presets: List<InvSortPreset> = emptyList(),
    val clickDelayMinMs: Int = DEFAULT_CLICK_MIN_MS,
    val clickDelayMaxMs: Int = DEFAULT_CLICK_MAX_MS,
    val openDelayMs: Int = DEFAULT_OPEN_DELAY_MS,
    /** Closes the inventory after a sort started from a preset button. Auto and hotkey sorts always close. */
    val closeWhenDone: Boolean = false,
    val moveCursor: Boolean = true,
    /** Same clicks with every delay at zero; all clicks of a run happen within one client tick. */
    val instant: Boolean = false,
    /** How strictly each sort click waits for the server before the next one. */
    val safetyLevel: CursorGuard.SafetyLevel = CursorGuard.SafetyLevel.BALANCED,
    /**
     * While an auto action runs, clicks, releases, scrolls and key presses on its screen are ignored;
     * Esc pressed twice quickly cancels. When off, a click or Esc cancels the run as it always did.
     */
    val lockInput: Boolean = true,
    val buttonOffsetX: Int = 4,
    val buttonOffsetY: Int = 4,
    /** When true, each button or hotkey press performs only one swap (one input = one click). */
    val swapOnePerPress: Boolean = false,
) {
    fun addPreset(preset: InvSortPreset): InvSortSettings = copy(presets = presets + preset)

    fun removePreset(index: Int): InvSortSettings {
        if (index !in presets.indices) return this
        return copy(presets = presets.filterIndexed { current, _ -> current != index })
    }

    /**
     * Moves the preset at [from] so it ends up at index [to]; the presets in between shift by one.
     * Out-of-range indices or from == to leave the settings unchanged.
     */
    fun movePreset(from: Int, to: Int): InvSortSettings {
        if (from !in presets.indices || to !in presets.indices || from == to) return this
        val reordered = presets.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return copy(presets = reordered)
    }

    fun renamePreset(index: Int, name: String): InvSortSettings =
        transformPreset(index) { it.copy(name = name) }

    fun replacePreset(index: Int, preset: InvSortPreset): InvSortSettings =
        transformPreset(index) { preset }

    fun clearSlot(index: Int, slot: Int): InvSortSettings =
        transformPreset(index) { it.copy(slots = it.slots - slot) }

    fun clearArmor(index: Int): InvSortSettings =
        transformPreset(index) { preset -> preset.copy(slots = preset.slots.filterKeys { !InvSortSlots.isArmor(it) }) }

    fun setShowButton(index: Int, show: Boolean): InvSortSettings =
        transformPreset(index) { it.copy(showButton = show) }

    fun setHotkey(index: Int, key: Int): InvSortSettings =
        transformPreset(index) { it.copy(hotkey = key) }

    fun setHotbarSwap(index: Int, on: Boolean): InvSortSettings =
        transformPreset(index) { it.copy(hotbarSwap = on) }

    /** Index of the preset bound to [key], ignoring [exceptIndex], or null when the key is free. */
    fun hotkeyOwner(key: Int, exceptIndex: Int = -1): Int? {
        if (key < 0) return null
        return presets.indexOfFirst { it.hotkey == key }.takeIf { it >= 0 && it != exceptIndex }
    }

    private fun transformPreset(index: Int, transform: (InvSortPreset) -> InvSortPreset): InvSortSettings {
        val current = presets.getOrNull(index) ?: return this
        return copy(presets = presets.mapIndexed { position, preset -> if (position == index) transform(current) else preset })
    }

    companion object {
        const val DEFAULT_CLICK_MIN_MS = 90
        const val DEFAULT_CLICK_MAX_MS = 170
        const val DEFAULT_OPEN_DELAY_MS = 220

        /** Clamps both bounds to the allowed range and orders them so that min <= max. */
        fun normalizeDelayRange(minMs: Int, maxMs: Int): Pair<Int, Int> {
            val low = minMs.coerceIn(InvSortLimits.MIN_CLICK_DELAY_MS, InvSortLimits.MAX_CLICK_DELAY_MS)
            val high = maxMs.coerceIn(InvSortLimits.MIN_CLICK_DELAY_MS, InvSortLimits.MAX_CLICK_DELAY_MS)
            return if (low <= high) low to high else high to low
        }

        /** Keeps the first preset that uses each hotkey; later duplicates become unbound. */
        fun withUniqueHotkeys(presets: List<InvSortPreset>): List<InvSortPreset> {
            val seen = mutableSetOf<Int>()
            return presets.map { preset ->
                when {
                    preset.hotkey < 0 -> preset
                    seen.add(preset.hotkey) -> preset
                    else -> preset.copy(hotkey = InvSortLimits.NO_HOTKEY)
                }
            }
        }
    }
}
