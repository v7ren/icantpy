package net.icantpy.cosmetics.morph

import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerDisguiseEnabledTest {
    @Test
    fun enabledFlagRoundTripsThroughSerializedJson() {
        for (enabled in listOf(true, false)) {
            val config = PlayerDisguiseConfig("minecraft:cat", true, MorphCameraMode.LOOK_AT, enabled)
            assertEquals(config, PlayerDisguiseConfig.parse(Gson().toJson(config.serialized())))
        }
    }

    @Test
    fun oldConfigWithoutEnabledKeyStaysOn() {
        val config = PlayerDisguiseConfig.parse("""{"entity":"cat","followEyeHeight":false,"cameraMode":"look"}""")
        assertTrue(config.enabled)
        assertEquals("minecraft:cat", config.entityId)
    }

    @Test
    fun explicitDisabledKeepsSavedEntity() {
        val config = PlayerDisguiseConfig.parse("""{"entity":"cat","enabled":false}""")
        assertFalse(config.enabled)
        assertEquals("minecraft:cat", config.entityId)
    }

    @Test
    fun nonBooleanEnabledValueIsTreatedAsOn() {
        assertTrue(PlayerDisguiseConfig.parse("""{"entity":"cat","enabled":"no"}""").enabled)
    }
}
