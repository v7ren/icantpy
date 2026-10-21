package net.icantpy.compat

import net.minecraft.client.Minecraft

/** 26.1.2 rebuilds level chunks through LevelRenderer. */
fun refreshLevelAfterVideoChange() {
    Minecraft.getInstance().levelRenderer.allChanged()
}
