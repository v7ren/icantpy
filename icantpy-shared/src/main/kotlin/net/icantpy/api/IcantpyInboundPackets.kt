package net.icantpy.api

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.BundlePacket
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.world.BossEvent
import java.util.UUID

/** Observe before other mods cancel/rewrite bundled packets, but only mutate on the client thread. */
object IcantpyInboundPackets {
    private val bars = IcantpyBossBarState()
    @Volatile private var generation = 0L

    fun capture(packet: Packet<*>) {
        if (packet !is BundlePacket<*> && packet !is ClientboundBossEventPacket &&
            packet !is ClientboundSystemChatPacket && packet !is ClientboundSetTitleTextPacket &&
            packet !is ClientboundSetSubtitleTextPacket && packet !is ClientboundPingPacket) return
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
        }
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
