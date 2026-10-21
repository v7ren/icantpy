package net.icantpy.gui.config

import org.lwjgl.glfw.GLFW

/** Display name for a GLFW key code, shared by the Leap keybind rows and inventory sort hotkeys. */
fun icantpyKeyLabel(code: Int): String = GLFW.glfwGetKeyName(code, 0)?.uppercase() ?: "Key $code"
