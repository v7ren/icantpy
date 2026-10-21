package net.icantpy.qol.loadout

import net.icantpy.util.Text

/**
 * Free of Minecraft types. Recognizes the loadout number on an icon (name or first lore line), whether an
 * icon is marked as equipped, and remembers the equipped loadout from Hypixel chat.
 */
object LoadoutEquipped {
    /** "Loadout 4", "Loadout #4", "Loadout 4 (Equipped)", "Loadout 4 (Selected)". Other trailing text is rejected. */
    private val NAME = Regex(
        "^loadout\\s*#?\\s*(\\d+)(?:\\s*\\((?:equipped|selected)\\))?$",
        RegexOption.IGNORE_CASE,
    )
    private val CHAT_EQUIPPED = Regex("^You equipped Loadout (\\d+)!$", RegexOption.IGNORE_CASE)
    private val CHAT_ALREADY = Regex("^Loadout (\\d+) is already equipped!$", RegexOption.IGNORE_CASE)
    private val CHAT_NAMED = Regex("^You equipped (.+)!$", RegexOption.IGNORE_CASE)
    private val CHAT_ALREADY_NAMED = Regex("^(.+) is already equipped!$", RegexOption.IGNORE_CASE)

    /** How long a memory of the equipped loadout may skip a switch without checking the menu. */
    const val MEMORY_FRESH_MS = 5 * 60_000L

    private val EQUIPPED_MARKERS = listOf(
        "currently equipped",
        "equipped!",
        "click to unequip",
        "selected",
        "(equipped)",
    )

    /** Loadout number from an icon name such as "Loadout 4", or null. */
    fun numberFromName(name: String): Int? =
        NAME.matchEntire(Text.strip(name).trim())?.groupValues?.get(1)?.toIntOrNull()

    /** Loadout number from the first lore line that names one, or null. */
    fun numberFromLore(lore: List<String>): Int? =
        lore.firstOrNull()?.let { numberFromName(it) }

    /** Name first, then lore, so a renamed icon still resolves when its lore names the loadout. */
    fun numberOf(name: String, lore: List<String>): Int? = numberFromName(name) ?: numberFromLore(lore)

    /**
     * True when the icon is marked equipped. Explicit markers count. The real Hypixel menu marks the equipped loadout
     * only by omission: a configured loadout (it has "Right-click to edit") with no "Left-click/Right-click to equip!"
     * line. Empty loadouts say "You must customize this loadout" and are never equipped.
     */
    fun isEquippedLore(lore: List<String>): Boolean {
        val clean = lore.map { Text.strip(it).lowercase() }
        if (clean.any { line -> EQUIPPED_MARKERS.any { line.contains(it) } }) return true
        val editable = clean.any { it.contains("click to edit") }
        val equippable = clean.any { it.contains("to equip") }
        val empty = clean.any { it.contains("must customize") }
        return editable && !equippable && !empty
    }

    /** How long an icon-less menu is given to finish refreshing before it counts as "not found". */
    const val EMPTY_WAIT_MS = 1_000L

    /** True while an icon-less menu should still be waited on, given how long it has been empty. */
    fun shouldWaitForIcons(emptyForMs: Long): Boolean = emptyForMs < EMPTY_WAIT_MS

    /** Loadout number from a chat line that reports the equipped loadout, or null. */
    fun numberFromChat(line: String): Int? {
        val clean = Text.strip(line).trim()
        return (CHAT_EQUIPPED.matchEntire(clean) ?: CHAT_ALREADY.matchEntire(clean))
            ?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Icon name from a chat line such as "You equipped lcm!", when the line is not a "Loadout N" line; else null. */
    fun nameFromChat(line: String): String? {
        val clean = Text.strip(line).trim()
        if (CHAT_EQUIPPED.matchEntire(clean) != null || CHAT_ALREADY.matchEntire(clean) != null) return null
        return CHAT_NAMED.matchEntire(clean)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Icon name from "<name> is already equipped!" when it is not a "Loadout N" line, or null. */
    fun alreadyEquippedNameFromChat(line: String): String? {
        val clean = Text.strip(line).trim()
        if (CHAT_ALREADY.matchEntire(clean) != null) return null
        return CHAT_ALREADY_NAMED.matchEntire(clean)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** Session memory of the equipped loadout, learned from chat and from icon lore. */
class LoadoutMemory {
    var equipped: Int? = null
        private set

    /** When [equipped] was last set, in epoch millis. */
    private var equippedAtMs: Long = 0L

    /** Icon name (lower case) to loadout number, from the menus seen this session. */
    private val numberByName = HashMap<String, Int>()

    /**
     * Updates the memory from a chat line. "Loadout N" and "<name> is already equipped!" with a known icon name set
     * the number. An unmapped "You equipped <name>!" clears it, so an old number is never kept. Returns true when
     * the line named a loadout or a known renamed icon.
     */
    fun onChat(line: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        LoadoutEquipped.numberFromChat(line)?.let { number ->
            set(number, nowMs)
            return true
        }
        LoadoutEquipped.nameFromChat(line)?.let { name ->
            val number = numberByName[name.lowercase()]
            if (number == null) forget() else set(number, nowMs)
            return number != null
        }
        val alreadyName = LoadoutEquipped.alreadyEquippedNameFromChat(line) ?: return false
        val number = numberByName[alreadyName.lowercase()] ?: return false
        set(number, nowMs)
        return true
    }

    /** Remembers the icon names seen in a menu with their numbers, so "You equipped <name>!" can be mapped. */
    fun rememberNames(pairs: List<Pair<String, Int>>) {
        for ((name, number) in pairs) numberByName[name.trim().lowercase()] = number
    }

    fun learn(number: Int, nowMs: Long = System.currentTimeMillis()) {
        set(number, nowMs)
    }

    /** Clears the equipped number when the menu or chat shows it is no longer known. */
    fun forget() {
        equipped = null
        equippedAtMs = 0L
    }

    /** True when [number] is known to be equipped, regardless of age. */
    fun isKnownEquipped(number: Int): Boolean = equipped == number

    /** True only when [number] was set recently ([LoadoutEquipped.MEMORY_FRESH_MS]); the only memory that may skip a switch. */
    fun isFreshlyKnown(number: Int, nowMs: Long): Boolean =
        equipped == number && nowMs - equippedAtMs in 0..LoadoutEquipped.MEMORY_FRESH_MS

    fun reset() {
        forget()
        numberByName.clear()
    }

    private fun set(number: Int, nowMs: Long) {
        equipped = number
        equippedAtMs = nowMs
    }
}
