package net.icantpy.qol.pets

import net.icantpy.util.Text

/** What a SkyBlock chat line says about the pet that is out. [pet] is null when the pet was put away. */
data class PetChange(val pet: String?)

/** Reads pet lines from chat: manual summons, despawns and Autopet rules. */
object PetChat {
    private val SUMMONED = Regex("^You summoned your (.+?)!?$")
    private val DESPAWNED = Regex("^You despawned your (.+?)!?$")
    private val AUTOPET = Regex("^Autopet equipped your (?:\\[Lvl\\s*\\d+]\\s*)?(.+?)!(?:\\s*VIEW RULE)?$")

    fun parse(message: String): PetChange? {
        val line = Text.strip(message).trim()
        AUTOPET.matchEntire(line)?.let { return PetChange(clean(it.groupValues[1])) }
        SUMMONED.matchEntire(line)?.let { return PetChange(clean(it.groupValues[1])) }
        DESPAWNED.matchEntire(line)?.let { return PetChange(null) }
        return null
    }

    /** True when the pet that is out is the one asked for (same matching as the menu: exact name, ignoring case). */
    fun isSamePet(out: String?, wanted: String): Boolean {
        val have = out?.let(::clean)?.lowercase() ?: return false
        val want = PetMenu.cleanTarget(wanted).lowercase()
        return want.isNotEmpty() && have == want
    }

    private fun clean(raw: String): String = PetMenu.cleanTarget(raw.trim().trimEnd('!', '.', ' '))
}
