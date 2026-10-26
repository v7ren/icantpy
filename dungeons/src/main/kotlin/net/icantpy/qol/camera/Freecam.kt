package net.icantpy.qol.camera

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.Icantpy
import net.icantpy.api.IcantpyDispatchResult
import net.icantpy.api.IcantpyQueryResult
import net.icantpy.compat.McCompat
import net.minecraft.client.Camera
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import java.lang.reflect.Method

/**
 * Client-only free camera. Nothing is sent that a standing player would not send: the local player gets an empty
 * input while it is on, so it stands where it was (the server sees an idle player), and only the rendered camera
 * moves. The camera position is written through the same camera query Freelook uses, so no new mixin is needed.
 *
 * Limits that come from the client being the only thing involved: you see only the chunks the server already sent,
 * and entities the server stopped sending stay unloaded.
 */
object Freecam {
    @Volatile
    var config: FreecamConfig = FreecamConfig()
        private set

    @Volatile
    var active: Boolean = false
        private set

    private var keyWasDown = false
    private var boundPlayer: LocalPlayer? = null
    private var savedInput: ClientInput? = null
    private var savedCameraType: CameraType? = null
    private var pos = FreecamPos(0.0, 0.0, 0.0)
    private var yaw = 0f
    private var pitch = 0f
    private var lastFrameNanos = 0L
    private var setPositionMethod: Method? = null

    fun load() {
        stop()
        config = FreecamStore.load()
    }

    fun onUnload() = stop()

    fun edit(change: (FreecamConfig) -> FreecamConfig) {
        val next = change(config)
        if (next == config) return
        config = next
        FreecamStore.save(next)
        if (!next.enabled) stop()
    }

    /** True while attacks and item uses must be swallowed. */
    fun blocksClicks(): Boolean = active && config.blockClicks

    fun toggle() {
        if (active) stop() else start()
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        pollKey(mc)
        if (!active) return
        val player = mc.player
        if (player == null || player !== boundPlayer || mc.level == null || !config.enabled ||
            !player.isAlive || (config.stopOnHurt && player.hurtTime > 0)
        ) {
            stop()
        }
    }

    /** Mouse turn while flying turns the camera; the player keeps its facing. Returns true when consumed. */
    fun consumeTurn(deltaYaw: Any?, deltaPitch: Any?): Boolean {
        if (!active) return false
        val dy = (deltaYaw as? Number)?.toDouble() ?: return false
        val dp = (deltaPitch as? Number)?.toDouble() ?: return false
        if (!dy.isFinite() || !dp.isFinite()) return false
        val next = FreelookMath.applyTurn(yaw, pitch, dy, dp)
        yaw = next.yaw
        pitch = next.pitch
        return true
    }

    /** Called from the camera look query on every camera update; moves and places the camera. */
    fun queryLook(camera: Camera): IcantpyQueryResult? {
        if (!active) return null
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        if (camera.entity() !== player || camera.isPanoramicMode()) return null
        advance(mc)
        if (!place(camera)) {
            stop()
            return null
        }
        return IcantpyQueryResult(IcantpyDispatchResult.HANDLED, floatArrayOf(yaw, pitch))
    }

    private fun advance(mc: Minecraft) {
        val now = System.nanoTime()
        val seconds = if (lastFrameNanos == 0L) 0.0 else (now - lastFrameNanos) / 1e9
        lastFrameNanos = now
        if (McCompat.currentScreen(mc) != null) return
        val o = mc.options
        val keys = FreecamKeys(
            forward = o.keyUp.isDown,
            back = o.keyDown.isDown,
            left = o.keyLeft.isDown,
            right = o.keyRight.isDown,
            up = o.keyJump.isDown,
            down = o.keyShift.isDown,
            boost = o.keySprint.isDown,
        )
        pos = FreecamMath.step(pos, yaw, pitch, keys, config.speed.toDouble(), seconds)
    }

    private fun place(camera: Camera): Boolean {
        val method = setPositionMethod ?: try {
            Camera::class.java.getDeclaredMethod(
                "setPosition", Double::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType,
            ).also {
                it.isAccessible = true
                setPositionMethod = it
            }
        } catch (error: Throwable) {
            Icantpy.LOGGER.warn("freecam: cannot reach Camera.setPosition; turning freecam off", error)
            return false
        }
        return try {
            method.invoke(camera, pos.x, pos.y, pos.z)
            true
        } catch (error: Throwable) {
            Icantpy.LOGGER.warn("freecam: moving the camera failed; turning freecam off", error)
            false
        }
    }

    private fun start() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!config.enabled || mc.level == null || McCompat.currentScreen(mc) != null) return
        val eye = player.eyePosition
        pos = FreecamPos(eye.x, eye.y, eye.z)
        yaw = player.yRot
        pitch = player.xRot
        lastFrameNanos = 0L
        boundPlayer = player
        savedInput = player.input
        // An empty input: the body stands still and sends what an idle player sends. WASD drive the camera instead.
        player.input = ClientInput()
        val current = mc.options.cameraType
        if (savedCameraType == null) savedCameraType = current
        mc.options.setCameraType(FreelookCamera.whileActive(current))
        active = true
    }

    private fun stop() {
        active = false
        val player = boundPlayer
        val input = savedInput
        boundPlayer = null
        savedInput = null
        lastFrameNanos = 0L
        if (player != null && input != null && Minecraft.getInstance().player === player) player.input = input
        val saved = savedCameraType
        savedCameraType = null
        if (saved != null) Minecraft.getInstance().options.setCameraType(saved)
    }

    private fun pollKey(mc: Minecraft) {
        val key = config.key
        if (key <= 0 || !config.enabled) {
            keyWasDown = false
            return
        }
        val down = InputConstants.isKeyDown(mc.window, key)
        val pressed = down && !keyWasDown
        keyWasDown = down
        if (pressed && McCompat.currentScreen(mc) == null && mc.player != null) toggle()
    }
}
