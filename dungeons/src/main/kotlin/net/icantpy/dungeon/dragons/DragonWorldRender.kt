package net.icantpy.dungeon.dragons

data class DragonBoxDraw(val box: DragonBox, val colorArgb: Int)

/** Boxes for the integrator to draw with the existing world-box renderer. Nothing is drawn here. */
object DragonWorldRender {
    fun boxes(states: List<DragonState>, settings: DragonSettings): List<DragonBoxDraw> {
        if (!settings.enabled || !settings.showBoxes) return emptyList()
        return states
            .filter { it.phase != DragonPhase.DEAD }
            .map { DragonBoxDraw(it.dragon.box, it.dragon.colorArgb) }
    }
}
