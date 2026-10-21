package net.icantpy.qol.visual

import com.mojang.blaze3d.platform.InputConstants
import net.icantpy.api.VisualHook
import net.icantpy.compat.McCompat
import net.minecraft.client.Minecraft

/**
 * Owns the visual settings and pushes them into [VisualHook]. Mixins read only those static fields, so nothing
 * here runs on the render path except [onFrameStart], which does work only while zoom is bound.
 */
object Visuals {
    @Volatile
    private var state: VisualsConfig = VisualsConfig()

    /** Nanotime of the previous frame while zoom is bound. Zero means no previous frame. */
    private var lastFrameNanos: Long = 0L

    fun load() {
        state = VisualsStore.load()
        lastFrameNanos = 0L
        apply(state)
    }

    /** Restores vanilla rendering. Saves happen synchronously in [commit], so there is nothing else to flush. */
    fun unload() {
        VisualHook.reset()
        HandShader.close()
        Trail.clear()
        lastFrameNanos = 0L
    }

    fun config(): VisualsConfig = state

    /** Stores [next], writes it to disk and applies it to the mixins. */
    fun commit(next: VisualsConfig) {
        state = next
        VisualsStore.save(next)
        apply(next)
    }

    /** Shorthand for [commit] with a copy of the current config. */
    fun edit(change: (VisualsConfig) -> VisualsConfig) = commit(change(state))

    /** Start of world render: draws the trail and china hat. Both return early when off. */
    fun renderWorld() {
        val c = state
        if (!c.trail && !c.chinaHat) return
        val partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)
        Trail.render(c)
        ChinaHat.render(c, partial)
    }

    /** Client tick. Drops a leftover zoom when the world is gone, so zoom cannot stick across sessions. */
    fun tick() {
        val c = state
        Trail.tick(c)
        if (c.ambientParticles) AmbientParticles.tick(c)
        if (VisualHook.fovMultiplier == 1f) return
        if (Minecraft.getInstance().player == null) VisualHook.fovMultiplier = 1f
    }

    /** Start of world render. Eases the zoom multiplier toward its target. Does nothing when zoom is unbound. */
    fun onFrameStart() {
        val c = state
        if (c.zoomKey == VisualsConfig.UNBOUND) {
            lastFrameNanos = 0L
            return
        }
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1e9f
        lastFrameNanos = now
        val target = if (zoomHeld(c.zoomKey)) 1f / c.zoomFactor else 1f
        VisualHook.fovMultiplier = ViewmodelMath.easeToward(VisualHook.fovMultiplier, target, dt, c.zoomSmooth)
    }

    /** Zoom ignores the key while a screen is open or no world is loaded, like freelook. */
    private fun zoomHeld(code: Int): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.player == null || McCompat.currentScreen(mc) != null) return false
        return InputConstants.isKeyDown(mc.window, code)
    }

    private fun apply(c: VisualsConfig) {
        val viewmodel = c.viewmodelEnabled
        VisualHook.enabled = c.anyActive()
        VisualHook.noFireOverlay = c.noFireOverlay
        VisualHook.noWaterOverlay = c.noWaterOverlay
        VisualHook.noInWallOverlay = c.noInWallOverlay
        VisualHook.noHurtCamera = c.noHurtCamera
        VisualHook.noVignette = c.noVignette
        VisualHook.noBlockBreakParticles = c.noBlockBreakParticles
        VisualHook.noExplosionParticles = c.noExplosionParticles
        VisualHook.noLightning = c.noLightning
        VisualHook.fullbright = c.fullbright
        VisualHook.fixedDayTime = if (c.timeChanger) c.dayTime else -1L
        VisualHook.noFog = c.noFog
        VisualHook.fogDistanceScale = c.fogDistance
        VisualHook.cameraDistance = if (c.customCameraDistance) c.cameraDistance else -1f
        VisualHook.cameraThroughWalls = c.cameraThroughWalls
        if (c.zoomKey == VisualsConfig.UNBOUND) VisualHook.fovMultiplier = 1f
        VisualHook.aspectRatio = if (c.customAspect) c.aspectRatio else -1f
        // The viewmodel section is one unit: when it is off, the swing and equip settings stay vanilla too.
        VisualHook.viewmodel = if (viewmodel) Viewmodel else null
        VisualHook.noEquipReset = viewmodel && c.noEquipReset
        VisualHook.swingDurationScale = if (viewmodel) 1f / c.swingSpeed else 1f
        VisualHook.playerModel = c.playerModelActive()
        VisualHook.dinnerbone = c.dinnerbone
        VisualHook.spinDegreesPerTick = if (c.spinbot) c.spinSpeed else 0f
        VisualHook.modelScaleX = c.modelScaleX
        VisualHook.modelScaleY = c.modelScaleY
        VisualHook.modelScaleZ = c.modelScaleZ
        HandShader.apply(c)
        VisualHook.handShader = if (c.handShader) HandShader else null
    }
}
