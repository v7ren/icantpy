package net.icantpy.render

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudCrosshairHookTargetTest {
    @Test
    fun exactlyOneSupportedHudClassOwnsTheHookedMethod() {
        val classLoader = javaClass.classLoader
        val owners = listOf("net/minecraft/client/gui/Gui", "net/minecraft/client/gui/Hud").filter { name ->
            classLoader.getResourceAsStream("$name.class")?.use { input ->
                val node = ClassNode()
                ClassReader(input).accept(node, ClassReader.SKIP_CODE)
                node.methods.any {
                    it.name == "extractCrosshair" &&
                        it.desc == "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
                }
            } ?: false
        }
        assertEquals(1, owners.size)
    }

    @Test
    fun residentMixinConfigRegistersBothRequiredRenderHooks() {
        val config = javaClass.classLoader.getResourceAsStream("icantpy-accessor.mixins.json")!!
            .bufferedReader().use { it.readText() }
        assertTrue(config.contains("\"HudCrosshairMixin\""))
        assertTrue(config.contains("\"GameRendererProjectionAccessor\""))
        assertTrue(config.contains("\"EntityTurnMixin\""))
        assertTrue(config.contains("\"MinecraftInputMixin\""))
        assertTrue(config.contains("\"MouseHandlerMixin\""))
    }
}
