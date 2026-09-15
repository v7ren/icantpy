package net.icantpy.modules.impl.dungeon.leaporient

import net.minecraft.client.Minecraft

object LeapRoster {
    fun selfName(): String? = Minecraft.getInstance().player?.name?.string

    fun selfClass(settings: LeapOrientSettings): LeapDungeonClass {
        if (settings.selfClass.isReal()) return settings.selfClass
        LeapOrientState.debugSelfClass?.takeIf { it.isReal() }?.let { return it }
        val name = selfName() ?: return LeapDungeonClass.EMPTY
        return classOf(name, settings, allowSelfOverride = false)
    }

    fun classOf(
        name: String,
        settings: LeapOrientSettings,
        allowSelfOverride: Boolean = true,
    ): LeapDungeonClass {
        if (allowSelfOverride && isSelf(name) && settings.selfClass.isReal()) {
            return settings.selfClass
        }
        if (allowSelfOverride && isSelf(name)) {
            LeapOrientState.debugSelfClass?.takeIf { it.isReal() }?.let { return it }
        }
        DebugParty.members.firstOrNull { it.name.equals(name, ignoreCase = true) }?.clazz
            ?.takeIf { it.isReal() }
            ?.let { return it }
        OdinLeapBridge.readDungeonPlayers().firstOrNull { it.name.equals(name, ignoreCase = true) }?.clazz
            ?.takeIf { it.isReal() }
            ?.let { return it }
        return LeapDungeonClass.EMPTY
    }

    fun isSelf(name: String): Boolean {
        val self = selfName() ?: return false
        return self.equals(name, ignoreCase = true)
    }

    fun knownPlayers(): List<LeapPlayer> {
        val odin = OdinLeapBridge.readDungeonPlayers().filter { it.clazz.isReal() }
        val debug = if (odin.isEmpty() && LeapOrient.debugActive()) {
            DebugParty.leapPlayers(selfName(), selfClass(LeapOrient.settings()))
        } else {
            emptyList()
        }
        return preferLivePlayers(odin, debug)
    }

    internal fun preferLivePlayers(live: List<LeapPlayer>, debug: List<LeapPlayer>): List<LeapPlayer> =
        if (live.isNotEmpty()) live else debug

    fun playerNamed(name: String, teammates: List<LeapPlayer>, clazz: LeapDungeonClass): LeapPlayer =
        teammates.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: LeapPlayer(name, clazz.takeIf { it.isReal() } ?: classOf(name, LeapOrient.settings()))

    fun playerForClass(clazz: LeapDungeonClass, teammates: List<LeapPlayer>): LeapPlayer? {
        if (!clazz.isReal()) return null
        teammates.firstOrNull { it.clazz == clazz }?.let { return it }
        knownPlayers().firstOrNull { it.clazz == clazz }?.let { return it }
        if (LeapOrient.debugActive()) {
            DebugParty.members.firstOrNull { it.clazz == clazz }?.let { member ->
                return LeapPlayer(member.name, member.clazz)
            }
        }
        return null
    }

    fun resolveTarget(
        pending: PendingLeap,
        teammates: List<LeapPlayer>,
        settings: LeapOrientSettings,
    ): LeapPlayer? {
        val named = pending.targetName.trim().ifBlank { pending.sender.trim() }
        val clazz = pending.targetClass?.takeIf { it.isReal() }
            ?: pending.clazz.takeIf { it.isReal() }
            ?: named.takeIf { it.isNotEmpty() }?.let { classOf(it, settings) }?.takeIf { it.isReal() }

        if (named.isNotEmpty()) {
            teammates.firstOrNull { it.name.equals(named, ignoreCase = true) && it.clazz.isReal() }
                ?.let { return it }
        }
        if (clazz != null) {
            playerForClass(clazz, teammates)?.let { return it }
        }
        if (named.isNotEmpty()) {
            return playerNamed(named, teammates, clazz ?: LeapDungeonClass.EMPTY)
        }
        return clazz?.let { LeapPlayer(it.displayName, it) }
    }
}
