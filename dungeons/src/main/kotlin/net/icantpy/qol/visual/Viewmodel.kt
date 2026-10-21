package net.icantpy.qol.visual

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.icantpy.api.VisualHook

/**
 * First-person item offset, rotation, scale and swing replacement. Only PoseStack calls that are identical on
 * 26.2 and 26.1.2 are used here, so this file needs no McCompat adapter.
 */
object Viewmodel : VisualHook.ViewmodelProvider {
    override fun transformItem(
        poseStack: Any?,
        mainHand: Boolean,
        rightArm: Boolean,
        swingProgress: Float,
        equipProgress: Float,
    ) {
        // Offhand items keep vanilla placement; the offsets are for the item the player holds in the main hand.
        if (!mainHand) return
        val pose = poseStack as? PoseStack ?: return
        val c = Visuals.config()
        val mirror = !rightArm
        val base = SwingPose(
            tx = c.offsetX,
            ty = c.offsetY,
            tz = c.offsetZ,
            rx = c.rotPitch,
            ry = c.rotYaw,
            rz = c.rotRoll,
        )
        val placed = if (mirror) base.mirrored() else base
        applyPose(pose, placed)
        pose.scale(c.scale, c.scale, c.scale)
    }

    override fun transformSwing(poseStack: Any?, rightArm: Boolean, swingProgress: Float): Boolean {
        val c = Visuals.config()
        if (c.swingMode == SwingMode.VANILLA) return false
        val pose = poseStack as? PoseStack ?: return false
        val swing = ViewmodelMath.swingPose(c.swingMode, swingProgress)
        applyPose(pose, if (rightArm) swing else swing.mirrored())
        return true
    }

    /** Translate, then rotate about X, Y and Z in that order. */
    private fun applyPose(pose: PoseStack, p: SwingPose) {
        pose.translate(p.tx, p.ty, p.tz)
        if (p.rx != 0f) pose.mulPose(Axis.XP.rotationDegrees(p.rx))
        if (p.ry != 0f) pose.mulPose(Axis.YP.rotationDegrees(p.ry))
        if (p.rz != 0f) pose.mulPose(Axis.ZP.rotationDegrees(p.rz))
    }
}
