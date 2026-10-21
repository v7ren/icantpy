package net.icantpy.mixin

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
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
    fun freelookConsumesTurnAtEntityTurnHeadNotInMouseHandler() {
        val entityTurn = mixin("EntityTurnMixin.java")
        assertTrue("@Mixin(Entity.class)" in entityTurn)
        assertTrue("method = \"turn\"" in entityTurn)
        assertTrue("cancellable = true" in entityTurn)
        assertTrue("input.player.turn" in entityTurn)

        // A @Redirect on LocalPlayer.turn inside MouseHandler.turnPlayer collides with other mods' MouseHandler mixins.
        val mouseHandler = mixin("MouseHandlerMixin.java")
        assertFalse("@Redirect" in mouseHandler)
        assertFalse("LocalPlayer;turn(DD)V" in mouseHandler)
    }

    @Test
    fun loaderMixinConfigsSkipFailedHooksInsteadOfCrashing() {
        for (name in listOf("icantpy-accessor.mixins.json", "icantpy-loader.mixins.json")) {
            val text = Files.readString(Path.of("src", "main", "resources", name))
            assertTrue(Regex("\"required\"\\s*:\\s*false").containsMatchIn(text), "$name must not be required")
            assertTrue(Regex("\"defaultRequire\"\\s*:\\s*0").containsMatchIn(text), "$name must default to require 0")
        }
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
