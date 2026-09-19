package net.icantpy.qol.camera

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.api.IcantpyDispatchResult
import net.icantpy.api.IcantpyKeyBindings
import net.icantpy.api.IcantpyQueryResult
import net.icantpy.compat.McCompat
import net.icantpy.mixin.KeyMappingAccessor
import net.minecraft.client.Camera
import net.minecraft.client.CameraType
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * Client-only camera look. Mouse deltas update private angles; the local player's
 * yaw/pitch and movement packets stay on the facing they had when freelook started.
 */
object Freelook {
    @Volatile
    var config: FreelookConfig = FreelookConfig()
        private set

    private var keyState = FreelookKeyState()
    private var cameraYaw = 0f
    private var cameraPitch = 0f
    private var savedCameraType: CameraType? = null

    fun load() {
        config = FreelookStore.load()
        resetSession()
    }

    fun onUnload() {
        resetSession()
    }

    fun enabled(): Boolean = config.enabled

    fun setEnabled(enabled: Boolean) = commit(config.copy(enabled = enabled))

    fun setKey(code: Int) = commit(config.copy(key = code))

    fun cycleMode() = commit(config.copy(mode = config.mode.next()))

    fun tick() {
        processKey()
    }

    fun consumeTurn(yaw: Any?, pitch: Any?): Boolean {
        processKey()
        if (!keyState.active) return false
        val deltaYaw = (yaw as? Number)?.toDouble() ?: return false
        val deltaPitch = (pitch as? Number)?.toDouble() ?: return false
        if (!deltaYaw.isFinite() || !deltaPitch.isFinite()) return false
        val next = FreelookMath.applyTurn(cameraYaw, cameraPitch, deltaYaw, deltaPitch)
        cameraYaw = next.yaw
        cameraPitch = next.pitch
        return true
    }

    fun queryLook(camera: Camera): IcantpyQueryResult {
        if (!keyState.active) return pass()
        val player = Minecraft.getInstance().player ?: return pass()
        if (camera.entity() !== player) return pass()
        if (camera.isPanoramicMode()) return pass()
        if (!cameraYaw.isFinite() || !cameraPitch.isFinite()) return pass()
        return IcantpyQueryResult(IcantpyDispatchResult.HANDLED, floatArrayOf(cameraYaw, cameraPitch))
    }

    private fun processKey() {
        val mc = Minecraft.getInstance()
        if (!config.enabled || mc.player == null || McCompat.currentScreen(mc) != null) {
            if (keyState.active) stop()
            keyState = keyState.copy(lastDown = false)
            return
        }
        if (keyState.active && mc.options.getCameraType().isFirstPerson()) {
            stop()
        }
        val down = keyDown(config.key)
        val step = FreelookKeys.step(keyState, config.mode, down, System.currentTimeMillis())
        keyState = step.state
        when (step.pulse) {
            FreelookPulse.START -> start(mc)
            FreelookPulse.STOP -> restoreCamera()
            FreelookPulse.NONE -> {}
        }
    }

    private fun start(mc: Minecraft) {
        val player = mc.player ?: return
        cameraYaw = player.getYRot()
        cameraPitch = player.getXRot()
        val current = mc.options.getCameraType()
        if (savedCameraType == null) savedCameraType = current
        mc.options.setCameraType(FreelookCamera.whileActive(current))
    }

    private fun stop() {
        keyState = keyState.copy(active = false, latched = false)
        restoreCamera()
    }

    private fun restoreCamera() {
        val saved = savedCameraType ?: return
        savedCameraType = null
        Minecraft.getInstance().options.setCameraType(saved)
    }

    private fun resetSession() {
        restoreCamera()
        keyState = FreelookKeyState()
        cameraYaw = 0f
        cameraPitch = 0f
    }

    private fun commit(next: FreelookConfig) {
        config = next
        FreelookStore.save(next)
        if (!next.enabled) resetSession()
    }

    private fun keyDown(code: Int): Boolean {
        if (IcantpyKeyBindings.isDown(IcantpyKeyBindings.FREELOOK)) return true
        val mc = Minecraft.getInstance()
        val window = mc.window
        val mapping = IcantpyKeyBindings.mapping(IcantpyKeyBindings.FREELOOK)
        if (mapping != null && !mapping.isUnbound && boundKeyDown(window.handle(), mapping)) return true
        if (code != FreelookConfig.UNBOUND && InputConstants.isKeyDown(window, code)) return true
        return false
    }

    private fun boundKeyDown(handle: Long, mapping: KeyMapping): Boolean {
        val bound = boundKey(mapping) ?: return false
        return when (bound.type) {
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(handle, bound.value) == GLFW.GLFW_PRESS
            InputConstants.Type.KEYSYM -> GLFW.glfwGetKey(handle, bound.value) == GLFW.GLFW_PRESS
            else -> false
        }
    }

    private fun boundKey(mapping: KeyMapping): InputConstants.Key? {
        return try {
            (mapping as KeyMappingAccessor).icantpyBoundKey()
        } catch (_: Throwable) {
            null
        }
    }

    private fun pass() = IcantpyQueryResult(IcantpyDispatchResult.PASS)
}
