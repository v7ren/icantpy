package net.icantpy.api

import kotlin.test.Test
import kotlin.test.assertEquals

class IcantpyHeadProfilesTest {
    @Test
    fun texturePropertiesArePresentAfterProfileCreation() {
        val profile = IcantpyHeadProfiles.fromTexture("dGVzdA==").partialProfile()

        assertEquals("dGVzdA==", profile.properties().get("textures").single().value())
    }
}
