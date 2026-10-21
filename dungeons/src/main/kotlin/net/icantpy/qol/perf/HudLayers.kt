package net.icantpy.qol.perf

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.icantpy.Icantpy
import net.icantpy.api.NoopHudElement
import net.minecraft.resources.Identifier

/**
 * Reload-safe HUD layers. Fabric keeps an identifier reserved after removeElement, so a reloaded payload cannot
 * add the same layer again ("Layer with identifier ... already exists"). Layers are therefore added once per game
 * session and afterwards only have their renderer swapped: the payload's on install, a loader-owned no-op on uninstall.
 */
object HudLayers {
    /**
     * Draws [element] before the vanilla HUD, reusing the layer when an earlier payload already registered [id].
     * Returns the identifier actually used; pass it to [uninstall]. If the reserved layer can't be replaced either
     * (an earlier payload removed it), a fresh suffixed identifier is registered for the rest of the session.
     */
    fun installFirst(id: Identifier, element: HudElement): Identifier {
        try {
            HudElementRegistry.addFirst(id, element)
            return id
        } catch (_: IllegalArgumentException) {
            // Registered by an earlier payload in this session.
        }
        try {
            HudElementRegistry.replaceElement(id) { element }
            return id
        } catch (exception: RuntimeException) {
            Icantpy.LOGGER.warn("HUD layer $id could not be reused; registering a fresh one", exception)
        }
        val fresh = Identifier.fromNamespaceAndPath(id.namespace, "${id.path}_${System.nanoTime()}")
        HudElementRegistry.addFirst(fresh, element)
        return fresh
    }

    /** Parks [id] on a no-op so the registry drops its reference to this payload's renderer. */
    fun uninstall(id: Identifier) {
        try {
            HudElementRegistry.replaceElement(id) { NoopHudElement.INSTANCE }
        } catch (exception: RuntimeException) {
            Icantpy.LOGGER.warn("Failed to park HUD layer $id", exception)
        }
    }
}
