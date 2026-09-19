package net.icantpy.render

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

class FreelookHookTest {
    private fun readClass(name: String): ClassNode = ClassNode().also { node ->
        javaClass.classLoader.getResourceAsStream("$name.class")!!.use {
            ClassReader(it).accept(node, 0)
        }
    }

    @Test
    fun entityTurnExistsWithVanillaDeltaSignature() {
        val entity = readClass("net/minecraft/world/entity/Entity")
        assertTrue(entity.methods.any { it.name == "turn" && it.desc == "(DD)V" })
    }

    @Test
    fun freelookMixinCancelsTurnThroughTheBridgeAndDoesNotSetEntityRotation() {
        val hook = readClass("net/icantpy/mixin/EntityTurnMixin").methods
            .single { it.name == "icantpy\$freelookTurn" }
        val calls = hook.instructions.filterIsInstance<MethodInsnNode>()
        assertTrue(calls.any { it.owner == "net/icantpy/api/IcantpyBridge" && it.name == "query" })
        assertFalse(calls.any {
            it.name == "setYRot" || it.name == "setXRot" || it.name == "turn" ||
                it.owner.startsWith("net/minecraft/network/")
        })
        assertFalse(hook.instructions.filterIsInstance<FieldInsnNode>().any {
            it.opcode == Opcodes.PUTFIELD || it.opcode == Opcodes.PUTSTATIC
        })
    }

    @Test
    fun loaderAndPayloadMixinListsRegisterTheTurnHook() {
        val loader = javaClass.classLoader.getResourceAsStream("icantpy-accessor.mixins.json")!!
            .bufferedReader().use { it.readText() }
        assertTrue(loader.contains("\"EntityTurnMixin\""))
    }
}
