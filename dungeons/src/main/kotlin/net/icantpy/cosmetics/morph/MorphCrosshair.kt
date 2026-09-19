package net.icantpy.cosmetics.morph

import net.icantpy.api.IcantpyDispatchResult
import net.icantpy.api.IcantpyQueryResult
import net.icantpy.compat.McCompat
import net.minecraft.client.Minecraft

/** Only reads vanilla's already-computed hit result; never changes picking or player aim. */
internal object MorphCrosshair {
    fun query(width: Int, height: Int, partialTick: Float): IcantpyQueryResult {
        PlayerDisguise.onCrosshairHook()
        // PASS leaves vanilla's crosshair and attack indicator centered, without any HUD transform.
        if (!PlayerDisguise.cameraMode().movesCrosshair(PlayerDisguise.followEyeHeight())) return pass()
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return pass()
        if (PlayerDisguise.entityId() == null
            || !mc.options.getCameraType().isFirstPerson()) return pass()
        val camera = McCompat.mainCamera(mc)
        if (!camera.isInitialized() || camera.isDetached() || camera.entity() !== player) return pass()
        val target = mc.hitResult?.getLocation() ?: return pass()
        val relative = target.subtract(camera.position())
        val offset = MorphCrosshairProjection.offset(
            MorphViewProjection.matrix(mc, partialTick),
            relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat(), width, height,
        )
        // Do not draw a centered crosshair when the real target is behind the shifted camera.
        val value: Any = offset?.let { floatArrayOf(it.x, it.y) } ?: false
        return IcantpyQueryResult(IcantpyDispatchResult.HANDLED, value)
    }

    private fun pass() = IcantpyQueryResult(IcantpyDispatchResult.PASS)
}
