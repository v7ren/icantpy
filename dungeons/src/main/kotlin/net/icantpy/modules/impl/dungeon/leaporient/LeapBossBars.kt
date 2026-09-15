package net.icantpy.modules.impl.dungeon.leaporient

import net.icantpy.compat.McCompat
import net.icantpy.mixin.BossHealthOverlayAccessor
import net.minecraft.client.Minecraft

object LeapBossBars {
    fun read(): List<LeapBossBar> {
        val overlay = McCompat.bossOverlay(Minecraft.getInstance()) as? BossHealthOverlayAccessor
            ?: return emptyList()
        return overlay.`icantpy$events`().values.map { LeapBossBar(it.name.string, it.progress) }
    }
}
