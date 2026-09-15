package net.icantpy.render;

/** Render-thread handoff used while vanilla turns an ItemStackRenderState into foil submits. */
public final class CustomGlintRenderContext {
    private static final ThreadLocal<Integer> COLOR = new ThreadLocal<>();

    private CustomGlintRenderContext() {}

    public static void set(Integer color) {
        if (color == null) COLOR.remove();
        else COLOR.set(color);
    }

    public static Integer get() { return COLOR.get(); }
}
