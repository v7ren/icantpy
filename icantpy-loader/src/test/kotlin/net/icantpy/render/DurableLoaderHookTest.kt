package net.icantpy.render

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DurableLoaderHookTest {
    private fun readClass(name: String): ClassNode = ClassNode().also { node ->
        javaClass.classLoader.getResourceAsStream("$name.class")!!.use {
            ClassReader(it).accept(node, 0)
        }
    }

    private fun mixinConfig(): String =
        javaClass.classLoader.getResourceAsStream("icantpy-accessor.mixins.json")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun loaderMixinListIncludesDurableInputAndCameraHooks() {
        val config = mixinConfig()
        for (name in listOf(
            "CameraMixin",
            "EntityTurnMixin",
            "MouseHandlerMixin",
            "KeyboardHandlerMixin",
            "MinecraftInputMixin",
        )) {
            assertTrue(config.contains("\"$name\""), name)
        }
    }

    @Test
    fun mouseKeyboardAndMinecraftHookTargetsExist() {
        val mouse = readClass("net/minecraft/client/MouseHandler")
        assertTrue(mouse.methods.any {
            it.name == "onButton" && it.desc == "(JLnet/minecraft/client/input/MouseButtonInfo;I)V"
        })
        assertTrue(mouse.methods.any { it.name == "onScroll" && it.desc == "(JDD)V" })
        val keyboard = readClass("net/minecraft/client/KeyboardHandler")
        assertTrue(keyboard.methods.any {
            it.name == "keyPress" && it.desc == "(JILnet/minecraft/client/input/KeyEvent;)V"
        })
        val minecraft = readClass("net/minecraft/client/Minecraft")
        assertTrue(minecraft.methods.any { it.name == "pick" && it.desc == "(F)V" })
        assertTrue(minecraft.methods.any { it.name == "startAttack" && it.desc == "()Z" })
        assertTrue(minecraft.methods.any { it.name == "startUseItem" && it.desc == "()V" })
        assertTrue(minecraft.methods.any { it.name == "handleKeybinds" && it.desc == "()V" })
        assertTrue(minecraft.methods.any {
            it.name == "setScreenAndShow" && it.desc == "(Lnet/minecraft/client/gui/screens/Screen;)V"
        })
    }

    @Test
    fun chatMixinCancelsSendAfterVanillaRecordsHistory() {
        val mixin = readClass("net/icantpy/loader/mixin/ChatScreenMixin")
        val hooks = mixin.methods.filter { it.name.startsWith("icantpy\$outgoing") }
        assertTrue(hooks.size == 2)
        for (hook in hooks) {
            val calls = hook.instructions.filterIsInstance<MethodInsnNode>()
            assertTrue(calls.any { it.name == "handleChatMessage" }, hook.name)
            assertFalse(calls.any { it.name == "addRecentChat" }, hook.name)
        }
    }

    @Test
    fun clientActionsLiveOnTheLoaderClasspath() {
        val actions = readClass("net/icantpy/api/IcantpyClientActions")
        assertTrue(actions.methods.any { it.name == "sendCommand" })
        assertTrue(actions.methods.any { it.name == "sendChat" })
        val calls = actions.methods.single { it.name == "sendCommand" }
            .instructions.filterIsInstance<MethodInsnNode>()
        assertTrue(calls.any { it.name == "schedule" })
    }
}
