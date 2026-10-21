package net.icantpy.api

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.BundlePacket
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.BossEvent
import java.util.UUID

/**
 * Pure filter for M7 dragon spawn particles: flame particles, 20 per burst, at one of the two
 * dragon spawn heights. Free of Minecraft types so it can be unit tested.
 */
fun isDragonFlame(isFlame: Boolean, count: Int, y: Double): Boolean =
    isFlame && count == 20 && (y in 18.5..19.5 || y in 26.5..27.5)

private const val DRAGON_FLAME_EVENT = "world.particle.flame"

private fun isDragonFlamePacket(packet: ClientboundLevelParticlesPacket): Boolean =
    isDragonFlame(packet.particle.type == ParticleTypes.FLAME, packet.count, packet.y)

/** Observe before other mods cancel/rewrite bundled packets, but only mutate on the client thread. */
object IcantpyInboundPackets {
    private val bars = IcantpyBossBarState()
    @Volatile private var generation = 0L

    fun capture(packet: Packet<*>) {
        if (packet !is BundlePacket<*> && packet !is ClientboundBossEventPacket &&
            packet !is ClientboundSystemChatPacket && packet !is ClientboundSetTitleTextPacket &&
            packet !is ClientboundSetSubtitleTextPacket && packet !is ClientboundPingPacket &&
            !(packet is ClientboundLevelParticlesPacket && isDragonFlamePacket(packet))) return
        val mc = Minecraft.getInstance()
        val connection = mc.connection ?: return
        val capturedGeneration = generation
        mc.execute {
            // Discard work queued for a connection that has since disconnected/reconnected.
            if (mc.connection === connection && generation == capturedGeneration) observe(packet)
        }
    }

    fun invalidate() { generation++ }

    fun clear() {
        invalidate()
        bars.clear()
    }

    private fun observe(packet: Packet<*>) {
        when (packet) {
            is BundlePacket<*> -> packet.subPackets().forEach(::observe)
            is ClientboundSystemChatPacket -> if (!packet.overlay()) IcantpyBridge.onIncomingChat(packet.content().string)
            is ClientboundSetTitleTextPacket -> IcantpyBridge.onIncomingChat(packet.text.string)
            is ClientboundSetSubtitleTextPacket -> IcantpyBridge.onIncomingChat(packet.text.string)
            is ClientboundPingPacket -> if (packet.id != 0) IcantpyBridge.onServerTick()
            is ClientboundBossEventPacket -> packet.dispatch(bossHandler)
            is ClientboundLevelParticlesPacket -> if (isDragonFlamePacket(packet)) emitDragonFlame(packet)
        }
    }

    private fun emitDragonFlame(packet: ClientboundLevelParticlesPacket) {
        IcantpyBridge.dispatch(
            IcantpyRuntimeEvent(
                DRAGON_FLAME_EVENT,
                context = mapOf(
                    "x" to packet.x,
                    "y" to packet.y,
                    "z" to packet.z,
                    "xDist" to packet.xDist,
                    "yDist" to packet.yDist,
                    "zDist" to packet.zDist,
                    "maxSpeed" to packet.maxSpeed,
                    "count" to packet.count,
                ),
            ),
        )
    }

    private fun emit(bar: IcantpyBossBar?) {
        bar?.let { IcantpyBridge.onBossBar(it.name, it.progress) }
    }

    private val bossHandler = object : ClientboundBossEventPacket.Handler {
        override fun add(id: UUID, name: Component, progress: Float, color: BossEvent.BossBarColor,
            overlay: BossEvent.BossBarOverlay, darkenSky: Boolean, playMusic: Boolean, createFog: Boolean) {
            emit(bars.add(id, name.string, progress))
        }
        override fun updateProgress(id: UUID, progress: Float) { emit(bars.progress(id, progress)) }
        override fun updateName(id: UUID, name: Component) { emit(bars.name(id, name.string)) }
        override fun remove(id: UUID) { bars.remove(id) }
    }
}
