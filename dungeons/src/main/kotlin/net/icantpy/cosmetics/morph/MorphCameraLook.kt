package net.icantpy.cosmetics.morph

import net.icantpy.api.IcantpyDispatchResult
import net.icantpy.api.IcantpyQueryResult
import net.icantpy.compat.McCompat
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft

/** Follows vanilla facing at a fixed reference distance, never the pointed-at block. */
internal object MorphCameraLook {
    fun query(camera: Camera): IcantpyQueryResult {
        PlayerDisguise.onCameraLookHook()
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return pass()
        if (!PlayerDisguise.usesLookDirection() || PlayerDisguise.entityId() == null
            || !mc.options.getCameraType().isFirstPerson()
            || camera !== McCompat.mainCamera(mc) || camera.entity() !== player
            || !camera.isInitialized() || camera.isDetached() || camera.isPanoramicMode()) return pass()
        val partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)
        val eyeHeightDifference = player.getEyePosition(partialTick).y - camera.position().y
        val angles = MorphLookDirection.fromVanillaLook(
            player.getViewYRot(partialTick), player.getViewXRot(partialTick), eyeHeightDifference,
        )
            ?: return pass()
        return IcantpyQueryResult(IcantpyDispatchResult.HANDLED, floatArrayOf(angles.yaw, angles.pitch))
    }

    private fun pass() = IcantpyQueryResult(IcantpyDispatchResult.PASS)
}
