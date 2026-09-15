package net.icantpy.loader

import kotlin.test.Test
import kotlin.test.assertEquals

class IcantpyUpdateSettingsTest {
    @Test
    fun usesPublicV7RenDomainByDefault() {
        assertEquals("https://mod.v7ren.com/icantpy", IcantpyUpdateSettings.DEFAULT_UPDATE_BASE)
    }

    @Test
    fun modernManifestUsesSeparateCompatibilityChannel() {
        assertEquals(
            "https://mod.v7ren.com/icantpy/manifest-v2.json",
            IcantpyUpdateSettings.modernManifestUrl(),
        )
    }
}
