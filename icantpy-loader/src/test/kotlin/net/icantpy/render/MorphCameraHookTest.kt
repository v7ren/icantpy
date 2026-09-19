package net.icantpy.render

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorphCameraHookTest {
    private fun readClass(name: String): ClassNode = ClassNode().also { node ->
        javaClass.classLoader.getResourceAsStream("$name.class")!!.use {
            ClassReader(it).accept(node, 0)
        }
    }

    @Test
    fun cameraHookTargetsAndFrustumMethodsExist() {
        val camera = readClass("net/minecraft/client/Camera")
        for ((name, descriptor) in listOf(
            "extractRenderState" to "(Lnet/minecraft/client/renderer/state/level/CameraRenderState;F)V",
            "update" to "(Lnet/minecraft/client/DeltaTracker;)V",
            "alignWithEntity" to "(F)V",
            "setRotation" to "(FF)V",
            "getViewRotationMatrix" to "(Lorg/joml/Matrix4f;)Lorg/joml/Matrix4f;",
            "createProjectionMatrixForCulling" to "()Lorg/joml/Matrix4f;",
            "prepareCullFrustum" to "(Lorg/joml/Matrix4fc;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;)V",
        )) {
            assertTrue(camera.methods.any { it.name == name && it.desc == descriptor }, name)
        }
    }

    @Test
    fun cosmeticHookCannotWritePlayerRotationHitResultsOrNetwork() {
        val hook = readClass("net/icantpy/mixin/CameraMixin").methods
            .single { it.name == "applyCosmeticLook" }
        val calls = hook.instructions.filterIsInstance<MethodInsnNode>()
        assertTrue(calls.any { it.name == "setRotation" })
        assertTrue(calls.any { it.name == "prepareCullFrustum" })
        assertFalse(calls.any {
            it.owner.startsWith("net/minecraft/world/entity/") ||
                it.owner.startsWith("net/minecraft/network/")
        })
        assertFalse(hook.instructions.filterIsInstance<FieldInsnNode>().any {
            it.opcode == Opcodes.PUTFIELD || it.opcode == Opcodes.PUTSTATIC
        })
    }

    @Test
    fun lookHookRunsAfterVanillaAlignsTheCamera() {
        val mixin = readClass("net/icantpy/mixin/CameraMixin")
        val afterAlign = mixin.methods.single { it.name == "icantpy\$cosmeticLookAfterAlign" }
        val extract = mixin.methods.single { it.name == "icantpy\$cosmeticLookDirection" }
        assertTrue(afterAlign.instructions.filterIsInstance<MethodInsnNode>().any { it.name == "applyCosmeticLook" })
        assertTrue(extract.instructions.filterIsInstance<MethodInsnNode>().any { it.name == "applyCosmeticLook" })
    }

    @Test
    fun vanillaFramePickingPrecedesRenderExtraction() {
        val minecraft = readClass("net/minecraft/client/Minecraft")
        val frameCalls = minecraft.methods.map { method ->
            method.instructions.filterIsInstance<MethodInsnNode>()
        }.single { calls ->
            calls.any { it.owner == "net/minecraft/client/renderer/GameRenderer" && it.name == "extract" } &&
                calls.any { it.owner == "net/minecraft/client/Minecraft" && it.name == "pick" }
        }
        val pick = frameCalls.indexOfFirst { it.owner == "net/minecraft/client/Minecraft" && it.name == "pick" }
        val extract = frameCalls.indexOfFirst {
            it.owner == "net/minecraft/client/renderer/GameRenderer" && it.name == "extract"
        }
        assertTrue(pick < extract)
    }
}
