package net.icantpy.cosmetics.items

import net.icantpy.api.AppearanceOwnerHook
import net.icantpy.api.IcantpyRuntimeEvent
import net.icantpy.cosmetics.morph.PlayerDisguise
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.util.ArrayDeque
import java.util.UUID

enum class AppearanceOwnerKind {
    LOCAL,
    UNKNOWN,
    REMOTE_PLAYER,
    OTHER,
}

/** Mixin-driven owner stack so UUID-less animated frames can still be scoped to a player. */
object AppearanceOwnerScope {
    private val stack = ThreadLocal.withInitial { ArrayDeque<Any?>() }

    /** Begin calls skipped while no appearance feature was active; their matching ends must not pop. */
    private val skippedDepth = ThreadLocal.withInitial { IntArray(1) }

    /**
     * Owner tracking is only read by item appearance resolution, so it is skipped while no appearance
     * feature (item customize, remote shared cosmetics, morph) can apply. Pairs stay balanced because
     * skipped begins are counted and their ends decrement the counter instead of popping.
     */
    fun isNeeded(): Boolean =
        CustomRename.enabled() || CustomCosmeticsShare.hasRemoteAppearances() || PlayerDisguise.activeEntityId() != null

    /** Publishes [isNeeded] to the shared hook so the bridge can skip begin/end events while nothing reads them. */
    fun syncWanted() {
        AppearanceOwnerHook.wanted = isNeeded()
    }

    fun handle(event: IcantpyRuntimeEvent) {
        when (event.context["phase"] as? String) {
            "begin" -> {
                if (!isNeeded()) {
                    skippedDepth.get()[0]++
                    return
                }
                stack.get().addLast(event.context["entity"])
            }
            "end" -> {
                val skipped = skippedDepth.get()
                if (skipped[0] > 0) {
                    skipped[0]--
                    return
                }
                val owners = stack.get()
                if (owners.isNotEmpty()) owners.removeLast()
            }
        }
    }

    fun current(): Any? = stack.get().lastOrNull()

    fun kind(owner: Any? = current()): AppearanceOwnerKind {
        if (owner == null) return AppearanceOwnerKind.UNKNOWN
        val entity = owner as? Entity ?: return AppearanceOwnerKind.OTHER
        val player = Minecraft.getInstance().player
        if (player != null && (entity === player || entity.uuid == player.uuid || PlayerDisguise.isLocalProxy(entity))) {
            return AppearanceOwnerKind.LOCAL
        }
        if (PlayerDisguise.isRemoteProxy(entity)) return AppearanceOwnerKind.REMOTE_PLAYER
        return if (entity is Player) AppearanceOwnerKind.REMOTE_PLAYER else AppearanceOwnerKind.OTHER
    }

    fun playerUuid(owner: Any? = current()): UUID? {
        val entity = owner as? Entity ?: return null
        if (entity is Player) return entity.uuid
        return PlayerDisguise.ownerUuid(entity)
    }

    fun clear() {
        stack.get().clear()
        skippedDepth.get()[0] = 0
    }
}
