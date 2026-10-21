package net.icantpy.api;

/**
 * Plain-Java switches read by the client-side visual mixins (render cleanup, fog, fullbright, time, camera,
 * zoom, aspect ratio, viewmodel), in the same style as [CullHook]. The payload writes these fields; mixins only
 * read them, so each check is one volatile read and touches no payload classes.
 *
 * Every value here changes what this client draws. Nothing affects packets, hit results or movement.
 * Defaults are vanilla. [reset] restores them, and the payload calls it on unload.
 */
public final class VisualHook {
    /** Master switch. When false every mixin behaves exactly like vanilla. */
    public static volatile boolean enabled = false;

    // Render cleanup
    public static volatile boolean noFireOverlay = false;
    public static volatile boolean noWaterOverlay = false;
    /** The block texture drawn when the camera is inside a block. */
    public static volatile boolean noInWallOverlay = false;
    public static volatile boolean noHurtCamera = false;
    public static volatile boolean noVignette = false;
    public static volatile boolean noBlockBreakParticles = false;
    public static volatile boolean noExplosionParticles = false;
    public static volatile boolean noLightning = false;

    // World look
    public static volatile boolean fullbright = false;
    /** Fixed client time of day in ticks (0..23999), or -1 for the server time. */
    public static volatile long fixedDayTime = -1L;
    public static volatile boolean noFog = false;
    /** Multiplies the fog start and end distances. 1 is vanilla. Ignored when [noFog] is set. */
    public static volatile float fogDistanceScale = 1.0f;

    // Camera
    /** Third-person camera distance in blocks, or a value <= 0 for vanilla (4). */
    public static volatile float cameraDistance = -1.0f;
    /** When true the third-person camera is not pulled in by walls. */
    public static volatile boolean cameraThroughWalls = false;
    /** Multiplies the world FOV each frame (zoom). 1 is vanilla. The HUD FOV is untouched. */
    public static volatile float fovMultiplier = 1.0f;
    /** Projection aspect ratio (width / height), or a value <= 0 for the window's own ratio. */
    public static volatile float aspectRatio = -1.0f;

    // First-person viewmodel
    /** When true the item does not dip down and back up after switching items. */
    public static volatile boolean noEquipReset = false;
    /** Multiplies the local player's swing animation length. 1 is vanilla, 2 is twice as slow. */
    public static volatile float swingDurationScale = 1.0f;

    public interface ViewmodelProvider {
        /**
         * Applies offset, rotation and scale to the first-person item. [poseStack] is a
         * com.mojang.blaze3d.vertex.PoseStack. Called after vanilla's arm transforms, right before the item is drawn.
         */
        void transformItem(Object poseStack, boolean mainHand, boolean rightArm, float swingProgress, float equipProgress);

        /**
         * Replaces vanilla's swing transform. Return false to keep the vanilla swing.
         * [poseStack] is a com.mojang.blaze3d.vertex.PoseStack.
         */
        boolean transformSwing(Object poseStack, boolean rightArm, float swingProgress);
    }

    public static volatile ViewmodelProvider viewmodel = null;

    // Local player model, seen in third person. Other players are never changed.
    /** True when any of the model values below differ from vanilla. Checked first by the model mixin. */
    public static volatile boolean playerModel = false;
    public static volatile boolean dinnerbone = false;
    /** Spin speed in degrees per tick, or 0 for no spin. */
    public static volatile float spinDegreesPerTick = 0.0f;
    public static volatile float modelScaleX = 1.0f;
    public static volatile float modelScaleY = 1.0f;
    public static volatile float modelScaleZ = 1.0f;

    public interface HandShaderProvider {
        /**
         * Called right after vanilla finished GameRenderer.renderItemInHand, including its early returns, so the
         * provider must check itself that the first-person hand was actually drawn this frame.
         * [gameRenderer] is a net.minecraft.client.renderer.GameRenderer.
         */
        void afterHand(Object gameRenderer);
    }

    public static volatile HandShaderProvider handShader = null;

    private VisualHook() {
    }

    /** Swallows provider failures so a payload bug can never break the frame. */
    public static void afterHand(Object gameRenderer) {
        if (!enabled) return;
        HandShaderProvider p = handShader;
        if (p == null) return;
        try {
            p.afterHand(gameRenderer);
        } catch (Throwable ignored) {
        }
    }

    public static boolean on() {
        return enabled;
    }

    /** Swallows provider failures so a payload bug can never break first-person rendering. */
    public static void transformItem(Object poseStack, boolean mainHand, boolean rightArm, float swing, float equip) {
        if (!enabled) return;
        ViewmodelProvider p = viewmodel;
        if (p == null) return;
        try {
            p.transformItem(poseStack, mainHand, rightArm, swing, equip);
        } catch (Throwable ignored) {
        }
    }

    public static boolean transformSwing(Object poseStack, boolean rightArm, float swing) {
        if (!enabled) return false;
        ViewmodelProvider p = viewmodel;
        if (p == null) return false;
        try {
            return p.transformSwing(poseStack, rightArm, swing);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Restores every value to vanilla. */
    public static void reset() {
        enabled = false;
        viewmodel = null;
        noFireOverlay = false;
        noWaterOverlay = false;
        noInWallOverlay = false;
        noHurtCamera = false;
        noVignette = false;
        noBlockBreakParticles = false;
        noExplosionParticles = false;
        noLightning = false;
        fullbright = false;
        fixedDayTime = -1L;
        noFog = false;
        fogDistanceScale = 1.0f;
        cameraDistance = -1.0f;
        cameraThroughWalls = false;
        fovMultiplier = 1.0f;
        aspectRatio = -1.0f;
        noEquipReset = false;
        swingDurationScale = 1.0f;
        playerModel = false;
        dinnerbone = false;
        spinDegreesPerTick = 0.0f;
        modelScaleX = 1.0f;
        modelScaleY = 1.0f;
        modelScaleZ = 1.0f;
        handShader = null;
    }
}
