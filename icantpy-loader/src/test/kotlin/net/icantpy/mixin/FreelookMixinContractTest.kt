package net.icantpy.mixin

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class FreelookMixinContractTest {
    @Test
    fun cameraRedirectsEntityViewAnglesInsideAlignWithEntity() {
        val text = mixin("CameraMixin.java")
        assertTrue("@Redirect" in text)
        assertTrue("alignWithEntity" in text)
        assertTrue("Lnet/minecraft/world/entity/Entity;getViewYRot(F)F" in text)
        assertTrue("Lnet/minecraft/world/entity/Entity;getViewXRot(F)F" in text)
    }

    @Test
    fun mouseHandlerRedirectsLocalPlayerTurn() {
        val text = mixin("MouseHandlerMixin.java")
        assertTrue("@Redirect" in text)
        assertTrue("turnPlayer" in text)
        assertTrue("Lnet/minecraft/client/player/LocalPlayer;turn(DD)V" in text)
    }

    @Test
    fun keyMappingBoundKeyIsAccessible() {
        val text = mixin("KeyMappingAccessor.java")
        assertTrue("@Accessor(\"key\")" in text)
        assertTrue("icantpyBoundKey" in text)
    }

    private fun mixin(name: String): String {
        val path = Path.of("..", "icantpy-shared", "src", "main", "java", "net", "icantpy", "mixin", name)
        assertTrue(Files.isRegularFile(path), "missing $path")
        return Files.readString(path)
    }
}
