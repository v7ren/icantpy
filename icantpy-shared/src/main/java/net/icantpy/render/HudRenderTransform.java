package net.icantpy.render;

import org.joml.Matrix3x2fStack;

/** A scoped, optional HUD translation. False suppresses an unprojectable element. */
public final class HudRenderTransform {
    private HudRenderTransform() {}

    public static void render(Matrix3x2fStack pose, Object override, Runnable render) {
        if (Boolean.FALSE.equals(override)) return;
        if (!(override instanceof float[] offset) || offset.length != 2
                || !Float.isFinite(offset[0]) || !Float.isFinite(offset[1])) {
            render.run();
            return;
        }
        pose.pushMatrix();
        try {
            pose.translate(offset[0], offset[1]);
            render.run();
        } finally {
            pose.popMatrix();
        }
    }
}
