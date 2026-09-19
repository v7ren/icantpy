package net.icantpy.loader

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.icantpy.api.IcantpyBridge
import net.icantpy.api.IcantpyKeyBindings
import net.icantpy.api.IcantpyRuntimeEvent
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object IcantpyLoaderKeys {
    fun register() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("icantpy", "controls"))
        bind(
            IcantpyKeyBindings.OPEN_CONFIG,
            "key.icantpy_loader.open_config",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            category,
        )
        bind(IcantpyKeyBindings.ADD_WAYPOINT, "key.icantpy_loader.add_waypoint", GLFW.GLFW_KEY_UNKNOWN, category)
        bind(IcantpyKeyBindings.OPEN_STATS, "key.icantpy_loader.open_stats", GLFW.GLFW_KEY_UNKNOWN, category)
        bind(IcantpyKeyBindings.OPEN_LOADOUT, "key.icantpy_loader.open_loadout", GLFW.GLFW_KEY_UNKNOWN, category)
        bind(IcantpyKeyBindings.FREELOOK, "key.icantpy_loader.freelook", GLFW.GLFW_KEY_F6, category)
        for (slot in 1..IcantpyKeyBindings.ACTION_SLOTS) {
            bind(
                IcantpyKeyBindings.actionId(slot),
                "key.icantpy_loader.action.$slot",
                GLFW.GLFW_KEY_UNKNOWN,
                category,
            )
        }
    }

    fun pollClicks() {
        consume(IcantpyKeyBindings.OPEN_CONFIG) { IcantpyBridge.openGui() }
        consume(IcantpyKeyBindings.ADD_WAYPOINT) { IcantpyBridge.addWaypointAtLook() }
        consume(IcantpyKeyBindings.OPEN_STATS) { IcantpyBridge.openStats() }
        consume(IcantpyKeyBindings.OPEN_LOADOUT) { IcantpyBridge.openLoadout() }
        for (slot in 1..IcantpyKeyBindings.ACTION_SLOTS) {
            val id = IcantpyKeyBindings.actionId(slot)
            consume(id) {
                IcantpyBridge.dispatch(IcantpyRuntimeEvent("input.action", context = mapOf("id" to id)))
            }
        }
    }

    private fun bind(id: String, translation: String, defaultKey: Int, category: KeyMapping.Category): KeyMapping {
        return IcantpyKeyBindings.put(
            id,
            KeyMappingHelper.registerKeyMapping(
                KeyMapping(translation, InputConstants.Type.KEYSYM, defaultKey, category),
            ),
        )
    }

    private fun consume(id: String, action: () -> Unit) {
        val mapping = IcantpyKeyBindings.mapping(id) ?: return
        while (mapping.consumeClick()) {
            action()
        }
    }
}
