package net.icantpy.api;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws nothing. A payload parks its HUD layers on this on unload instead of removing them: Fabric keeps a removed
 * layer's identifier reserved, so re-adding it after a reload fails, and a no-op owned by the loader's classloader
 * keeps the HUD registry from holding on to the unloaded payload.
 */
public final class NoopHudElement implements HudElement {
    public static final NoopHudElement INSTANCE = new NoopHudElement();

    private NoopHudElement() {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }
}
