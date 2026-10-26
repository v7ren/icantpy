package net.icantpy.hud

import net.icantpy.dungeon.dragons.DragonController
import net.icantpy.hud.markers.PlayerMarkerRenderer
import net.icantpy.qol.waypoint.CommandWaypoints
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.AABB

object WorldEsp {
    private const val BOX_STROKE_WIDTH = 1.8f
    private const val BOX_FILL_ALPHA = 0x22000000

    fun render() {
        CommandWaypoints.render()
        renderDragonBoxes()
        PlayerMarkerRenderer.render()
        net.icantpy.qol.visual.Visuals.renderWorld()
        net.icantpy.slayer.blaze.FirePit.renderWorld()
        net.icantpy.slayer.aim.BossAim.renderWorld()
        net.icantpy.qol.path.Navigator.renderWorld()
        net.icantpy.qol.lava.LavaEsp.renderWorld()
    }

    /** Outlines of living or spawning wither dragons. The tracker's boxes are empty outside P5. */
    private fun renderDragonBoxes() {
        for (draw in DragonController.boxes()) {
            val box = draw.box
            val outline = draw.colorArgb
            val fill = (outline and 0x00FFFFFF) or BOX_FILL_ALPHA
            val aabb = AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
            Gizmos.cuboid(aabb, GizmoStyle.strokeAndFill(outline, BOX_STROKE_WIDTH, fill))
        }
    }
}
