package net.icantpy.qol.invsort

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.icantpy.Icantpy
import net.icantpy.qol.auto.AutoInputEvent
import net.icantpy.qol.auto.AutoInputGate
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW
import java.util.Collections
import java.util.WeakHashMap

/**
 * Attaches per-screen Fabric listeners to each player inventory screen once. Every listener checks
 * the controller generation captured at attach time, so listeners from an unloaded payload do nothing.
 * Input goes through [AutoInputGate], which swallows it while an auto run holds the screen locked.
 */
object InvSortScreenEvents {
    private val attached: MutableSet<Screen> = Collections.newSetFromMap(WeakHashMap())

    fun attachIfInventory(screen: Screen?, generation: Int) {
        if (screen == null || !InvSortLayout.isPlayerInventory(screen) || !attached.add(screen)) return
        ScreenEvents.afterExtract(screen).register { target, graphics, mouseX, mouseY, _ ->
            if (!isCurrent(generation)) return@register
            InvSortButton.render(target, graphics, mouseX, mouseY)
            AutoInputGate.renderHint(target, graphics)
        }
        ScreenMouseEvents.allowMouseClick(screen).register { target, event ->
            if (!isCurrent(generation)) return@register true
            // The sort buttons are offered the click only when the gate lets the press through.
            AutoInputGate.allow(AutoInputEvent.MOUSE_PRESS, event) { InvSortButton.handleMouse(target, event) }
        }
        ScreenMouseEvents.allowMouseRelease(screen).register { _, _ ->
            !isCurrent(generation) || AutoInputGate.allow(AutoInputEvent.OTHER)
        }
        ScreenMouseEvents.allowMouseDrag(screen).register { _, _, _, _ ->
            !isCurrent(generation) || AutoInputGate.allow(AutoInputEvent.OTHER)
        }
        ScreenMouseEvents.allowMouseScroll(screen).register { _, _, _, _, _ ->
            !isCurrent(generation) || AutoInputGate.allow(AutoInputEvent.OTHER)
        }
        ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
            if (!isCurrent(generation)) return@register true
            val kind = if (event.key() == GLFW.GLFW_KEY_ESCAPE) AutoInputEvent.ESCAPE else AutoInputEvent.OTHER
            AutoInputGate.allow(kind, event)
        }
        ScreenKeyboardEvents.allowKeyRelease(screen).register { _, _ ->
            !isCurrent(generation) || AutoInputGate.allow(AutoInputEvent.OTHER)
        }
        ScreenKeyboardEvents.allowCharType(screen).register { _, _ ->
            !isCurrent(generation) || AutoInputGate.allow(AutoInputEvent.OTHER)
        }
        Icantpy.LOGGER.info("Inventory sort button attached to ${screen.javaClass.simpleName}")
    }

    private fun isCurrent(generation: Int): Boolean = generation == InvSortController.generation
}
