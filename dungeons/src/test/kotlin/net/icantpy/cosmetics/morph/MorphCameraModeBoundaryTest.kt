package net.icantpy.cosmetics.morph

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import kotlin.test.*

class MorphCameraModeBoundaryTest {
    @Test
    fun lookModeReadsVanillaFacingNotHitBlocksAndDoesNotWriteGameplayState() {
        val node = ClassNode()
        javaClass.classLoader.getResourceAsStream(
            "net/icantpy/cosmetics/morph/MorphCameraLook.class",
        )!!.use { ClassReader(it).accept(node, 0) }
        val query = node.methods.single { it.name == "query" }
        val calls = query.instructions.filterIsInstance<MethodInsnNode>()
        // Kotlin namespaces internal methods with a module-name suffix in bytecode.
        assertTrue(calls.any { it.name.startsWith("usesLookDirection\$") })
        assertTrue(calls.any { it.name == "getViewYRot" })
        assertTrue(calls.any { it.name == "getViewXRot" })
        assertFalse(query.instructions.filterIsInstance<FieldInsnNode>().any { it.name == "hitResult" })
        assertFalse(calls.any { it.owner.startsWith("net/minecraft/network/")
            || it.name in setOf("setXRot", "setYRot", "turn", "pick", "clip", "send") })
    }
}
