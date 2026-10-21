package net.icantpy.qol.auto

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW
import java.util.Collections
import java.util.WeakHashMap

/**
 * Lets the player take over an auto menu run, or is locked out of it while the lock is on (see
 * [AutoInputGate]). Our own clicks go through the game mode and do not dispatch screen input events,
 * so only real input reaches these listeners.
 */
object AutoMenuInput {
    private val attached: MutableSet<Screen> = Collections.newSetFromMap(WeakHashMap())

    fun attach(screen: Screen) {
        if (!attached.add(screen)) return
        ScreenEvents.afterExtract(screen).register { target, graphics, _, _, _ ->
            AutoInputGate.renderHint(target, graphics)
        }
        ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
            AutoInputGate.allow(AutoInputEvent.MOUSE_PRESS, event)
        }
        ScreenMouseEvents.allowMouseRelease(screen).register { _, _ ->
            AutoInputGate.allow(AutoInputEvent.OTHER)
        }
        ScreenMouseEvents.allowMouseDrag(screen).register { _, _, _, _ ->
            AutoInputGate.allow(AutoInputEvent.OTHER)
        }
        ScreenMouseEvents.allowMouseScroll(screen).register { _, _, _, _, _ ->
            AutoInputGate.allow(AutoInputEvent.OTHER)
        }
        ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
            AutoInputGate.allow(keyEvent(event.key()), event)
        }
        ScreenKeyboardEvents.allowKeyRelease(screen).register { _, _ ->
            AutoInputGate.allow(AutoInputEvent.OTHER)
        }
        ScreenKeyboardEvents.allowCharType(screen).register { _, _ ->
            AutoInputGate.allow(AutoInputEvent.OTHER)
        }
    }

    private fun keyEvent(key: Int): AutoInputEvent =
        if (key == GLFW.GLFW_KEY_ESCAPE) AutoInputEvent.ESCAPE else AutoInputEvent.OTHER
}
