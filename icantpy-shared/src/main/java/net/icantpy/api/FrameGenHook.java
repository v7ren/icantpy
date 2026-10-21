package net.icantpy.api;

/**
 * Plain-Java switch read by the GameRenderer frame generation mixin, in the same style as [GlowHook].
 *
 * Frame generation (rotation reprojection): on a generated frame the level render is skipped and the payload
 * redraws the last real level frame, turned to match the current camera rotation, under a freshly drawn HUD.
 * Any provider failure means "render normally".
 */
public final class FrameGenHook {
    public interface Provider {
        /** Called once at the start of each GameRenderer.render. True to skip the level render this frame. */
        boolean skipLevelRender();

        /** Called right after a real level render finished, before the GUI is drawn. Captures the world-only frame. */
        void afterLevelRender();
    }

    /** True only while a provider is installed. Checked first by the mixin. */
    public static volatile boolean enabled = false;

    public static volatile Provider provider = null;

    private FrameGenHook() {
    }

    public static boolean skipLevelRender() {
        if (!enabled) return false;
        Provider p = provider;
        if (p == null) return false;
        try {
            return p.skipLevelRender();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void afterLevelRender() {
        if (!enabled) return;
        Provider p = provider;
        if (p == null) return;
        try {
            p.afterLevelRender();
        } catch (Throwable ignored) {
            // A capture failure only costs the next generated frame; the payload logs and disables itself.
        }
    }

    /** Installs [next] and enables it, or disables and clears the hook when [next] is null. */
    public static void install(Provider next) {
        if (next == null) {
            enabled = false;
            provider = null;
        } else {
            provider = next;
            enabled = true;
        }
    }
}
