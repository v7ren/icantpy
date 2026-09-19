package net.icantpy.cosmetics.morph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerDisguiseConfigTest {
    @Test
    fun lookAtModeAlwaysKeepsTheVanillaCrosshairFixed() {
        assertFalse(MorphCameraMode.LOOK_AT.movesCrosshair(true))
        assertFalse(MorphCameraMode.LOOK_AT.movesCrosshair(false))
    }

    @Test
    fun onlyEnabledShiftedCrosshairModeMovesTheCrosshair() {
        assertTrue(MorphCameraMode.SHIFTED_CROSSHAIR.movesCrosshair(true))
        assertFalse(MorphCameraMode.SHIFTED_CROSSHAIR.movesCrosshair(false))
    }

    @Test
    fun storedConfigRoundTripsBothModesEvenWhileEyeHeightIsOff() {
        for (mode in MorphCameraMode.entries) {
            for (enabled in listOf(false, true)) {
                val config = PlayerDisguiseConfig("minecraft:cat", enabled, mode)
                assertEquals(config, PlayerDisguiseConfig.parse(com.google.gson.Gson().toJson(config.serialized())))
            }
        }
    }

    @Test
    fun cameraModeLoadsBothModesWithoutChangingMorphOrEyeHeight() {
        for (mode in MorphCameraMode.entries) {
            assertEquals(PlayerDisguiseConfig("minecraft:cat", true, mode), PlayerDisguiseConfig.parse(
                """{"entity":"cat","followEyeHeight":true,"cameraMode":"${mode.id}"}""",
            ))
        }
    }

    @Test
    fun oldAndInvalidModeConfigsPreserveCurrentLookAtBehavior() {
        for (field in listOf("", ",\"cameraMode\":null", ",\"cameraMode\":false",
            ",\"cameraMode\":42", ",\"cameraMode\":{}", ",\"cameraMode\":\"unknown\"")) {
            assertEquals(PlayerDisguiseConfig("minecraft:cat", true, MorphCameraMode.LOOK_AT),
                PlayerDisguiseConfig.parse("""{"entity":"cat","followEyeHeight":true$field}"""))
        }
    }

    @Test
    fun modeCommandsAcceptCanonicalNamesAndRejectUnknownValues() {
        assertEquals(MorphCameraMode.SHIFTED_CROSSHAIR, MorphCameraMode.fromId(" Crosshair "))
        assertEquals(MorphCameraMode.LOOK_AT, MorphCameraMode.fromId("LOOK"))
        assertNull(MorphCameraMode.fromId("wrong"))
        assertNull(MorphCameraMode.fromId(null))
    }

    @Test
    fun onlyEnabledLookAtModeCanRotateTheCosmeticCameraOrModel() {
        assertTrue(MorphCameraMode.LOOK_AT.usesLookDirection(true))
        assertFalse(MorphCameraMode.LOOK_AT.usesLookDirection(false))
        assertFalse(MorphCameraMode.SHIFTED_CROSSHAIR.usesLookDirection(true))
        assertFalse(MorphCameraMode.SHIFTED_CROSSHAIR.usesLookDirection(false))
    }

    @Test
    fun bareMinecraftEntityIdsAreCanonicalized() {
        assertEquals("minecraft:cat", PlayerDisguiseConfig.normalizeEntityId(" cat "))
        assertEquals("mod:custom/entity", PlayerDisguiseConfig.normalizeEntityId("MOD:custom/entity"))
    }

    @Test
    fun invalidEntityIdsAreRejected() {
        assertNull(PlayerDisguiseConfig.normalizeEntityId(""))
        assertNull(PlayerDisguiseConfig.normalizeEntityId("minecraft:"))
        assertNull(PlayerDisguiseConfig.normalizeEntityId("minecraft:cat with spaces"))
    }

    @Test
    fun configParsingPreservesOnlyAValidEntityId() {
        assertEquals(
            PlayerDisguiseConfig("minecraft:cat"),
            PlayerDisguiseConfig.parse("""{"entity":"cat"}"""),
        )
        assertEquals(
            PlayerDisguiseConfig("minecraft:cat", followEyeHeight = true),
            PlayerDisguiseConfig.parse("""{"entity":"cat","followEyeHeight":true}"""),
        )
        assertEquals(
            PlayerDisguiseConfig(),
            PlayerDisguiseConfig.parse("""{"entity":"not valid"}"""),
        )
        assertEquals(PlayerDisguiseConfig(), PlayerDisguiseConfig.parse("{not json"))
    }
}
