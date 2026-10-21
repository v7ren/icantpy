package net.icantpy.api;

import java.util.function.IntUnaryOperator;

/**
 * Plain-Java switch read by the entity glow mixins. Mixins touch only this class (no Kotlin objects, no
 * game classes beyond the injection target), so the hook costs one volatile read while inactive and cannot
 * trigger class initialization of payload code during early game startup.
 *
 * The payload sets [provider] and [enabled] together only while it has active player-glow markers, and clears
 * both on unload. [provider] returns 0xRRGGBB for an entity id or [NO_GLOW].
 */
public final class GlowHook {
    public static final int NO_GLOW = -1;

    /** True only while a provider is installed and wants glow. Checked first by the mixins. */
    public static volatile boolean enabled = false;

    /** Entity id to 0xRRGGBB, or [NO_GLOW]. Null when no payload is active. */
    public static volatile IntUnaryOperator provider = null;

    private GlowHook() {
    }

    /** Glow color for [entityId], or [NO_GLOW]. Never throws: any provider failure means no glow. */
    public static int colorFor(int entityId) {
        if (!enabled) return NO_GLOW;
        IntUnaryOperator p = provider;
        if (p == null) return NO_GLOW;
        try {
            return p.applyAsInt(entityId);
        } catch (Throwable ignored) {
            return NO_GLOW;
        }
    }

    /** Installs [next] and enables it, or disables and clears the hook when [next] is null. */
    public static void install(IntUnaryOperator next) {
        if (next == null) {
            enabled = false;
            provider = null;
        } else {
            provider = next;
            enabled = true;
        }
    }
}
