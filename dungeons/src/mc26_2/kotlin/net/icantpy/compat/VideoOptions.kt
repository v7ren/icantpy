package net.icantpy.compat

import net.minecraft.client.Minecraft

/** 26.2 moved level chunk rebuilding from LevelRenderer to LevelExtractor. */
fun refreshLevelAfterVideoChange() {
    Minecraft.getInstance().levelExtractor.allChanged()
}
